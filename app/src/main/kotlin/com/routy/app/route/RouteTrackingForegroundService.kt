package com.routy.app.route

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Binder
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.routy.app.MainActivity
import com.routy.app.R
import com.routy.app.RoutyApplication
import com.routy.app.core.storage.RouteWalkTrackStore
import com.routy.app.logic.api.GeoPoint
import com.routy.app.logic.api.RouteStation
import com.routy.app.logic.api.SegmentDto
import com.routy.app.logic.geo.LatLng
import com.routy.app.logic.route.GoldenSegmentHitTracker
import com.routy.app.logic.route.OffPathDetector
import com.routy.app.logic.route.RouteWalkTrackSession
import com.routy.app.logic.route.VoiceCueTracker
import com.routy.app.logic.route.WaypointProgressTracker
import com.routy.app.logic.route.goldenCanonicalForFinishedHop
import com.routy.app.logic.route.isRouteTrackAccuracyUsable
import com.routy.app.logic.route.shouldAutoCompleteRoute
import java.util.Locale
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class RouteTrackingServiceState(
    val active: Boolean = false,
    val trackEnabled: Boolean = false,
    val voiceEnabled: Boolean = false,
    val myLocation: GeoPoint? = null,
    val locationBearing: Float? = null,
    val locationSpeed: Float? = null,
    val completedWaypointIndex: Int = -1,
    val voiceAnnouncedIndex: Int = 0,
    val stationCount: Int = 0,
    val nextStationName: String? = null,
    val trackedGeometry: List<GeoPoint> = emptyList(),
    val offPathWarned: Boolean = false,
    val autoCompleteRequested: Boolean = false,
    val goldenHitSegmentIds: Set<Int> = emptySet(),
)

/**
 * Keeps GPS + GPX track + waypoint progress + TTS alive while the phone is locked / in a pocket.
 */
class RouteTrackingForegroundService : Service() {
    private val binder = LocalBinder()
    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var locationCallback: LocationCallback? = null
    private lateinit var walkTrackStore: RouteWalkTrackStore
    private var voiceController: VoiceGuidanceController? = null
    private var cueController: TrackCueController? = null
    private var voiceTracker: VoiceCueTracker? = null
    private var progressTracker: WaypointProgressTracker? = null
    private var offPathDetector: OffPathDetector? = null
    private var goldenHitTracker: GoldenSegmentHitTracker? = null
    private val walkTrackSession = RouteWalkTrackSession()
    private val goldenSoundCued = mutableSetOf<Int>()

    private var routeKey: String = ""
    private var accountLocaleTag: String = ""
    private var trackEnabled: Boolean = false
    private var voiceEnabled: Boolean = false
    private var stations: List<RouteStation> = emptyList()
    private var routePolyline: List<LatLng> = emptyList()
    private var routeSegmentIds: List<Int> = emptyList()
    private var networkSegments: List<SegmentDto> = emptyList()
    private var todayGoldenCanonicalIds: Set<Int> = emptySet()

    private val _state = MutableStateFlow(RouteTrackingServiceState())
    val state: StateFlow<RouteTrackingServiceState> = _state.asStateFlow()

    private val _stopTrackRequested = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val stopTrackRequested: SharedFlow<Unit> = _stopTrackRequested.asSharedFlow()

    inner class LocalBinder : Binder() {
        fun getService(): RouteTrackingForegroundService = this@RouteTrackingForegroundService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        walkTrackStore = (application as RoutyApplication).routeWalkTrackStore
        createNotificationChannel()
        cueController = TrackCueController(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_ALL -> {
                stopTracking(clearSession = false)
                return START_NOT_STICKY
            }
            ACTION_STOP_TRACK -> {
                trackEnabled = false
                persistTrack()
                emitState(_state.value.copy(trackEnabled = false))
                if (!voiceEnabled) {
                    stopTracking()
                } else {
                    updateNotification()
                }
                _stopTrackRequested.tryEmit(Unit)
                return if (_state.value.active) START_STICKY else START_NOT_STICKY
            }
            ACTION_REANNOUNCE -> {
                reannounceUpcoming()
                return START_STICKY
            }
            ACTION_SET_VOICE -> {
                voiceEnabled = intent.getBooleanExtra(EXTRA_VOICE_ENABLED, voiceEnabled)
                emitState(_state.value.copy(voiceEnabled = voiceEnabled))
                if (!trackEnabled && !voiceEnabled) {
                    stopTracking()
                } else {
                    updateNotification()
                }
            }
            ACTION_SET_TRACK -> {
                trackEnabled = intent.getBooleanExtra(EXTRA_TRACK_ENABLED, trackEnabled)
                emitState(_state.value.copy(trackEnabled = trackEnabled))
                updateNotification()
            }
            else -> {
                val stationsJson = intent?.getStringExtra(EXTRA_STATIONS_JSON)
                if (!stationsJson.isNullOrBlank()) {
                    configureFromIntent(intent)
                }
            }
        }

        if (stations.isEmpty() || (!trackEnabled && !voiceEnabled)) {
            stopSelf()
            return START_NOT_STICKY
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        ensureVoiceController()
        startLocationUpdates()
        syncTrackingActive(true)
        emitState(_state.value.copy(active = true, stationCount = stations.size))
        return START_STICKY
    }

    private fun configureFromIntent(intent: Intent) {
        routeKey = intent.getStringExtra(EXTRA_ROUTE_KEY).orEmpty()
        accountLocaleTag = intent.getStringExtra(EXTRA_LOCALE_TAG).orEmpty()
        trackEnabled = intent.getBooleanExtra(EXTRA_TRACK_ENABLED, false)
        voiceEnabled = intent.getBooleanExtra(EXTRA_VOICE_ENABLED, false)
        val completed = intent.getIntExtra(EXTRA_COMPLETED_INDEX, -1)
        val voiceIdx = intent.getIntExtra(EXTRA_VOICE_INDEX, 0)
        stations = runCatching {
            json.decodeFromString<List<RouteStation>>(intent.getStringExtra(EXTRA_STATIONS_JSON)!!)
        }.getOrDefault(emptyList())
        routePolyline = runCatching {
            json.decodeFromString<List<GeoPoint>>(intent.getStringExtra(EXTRA_ROUTE_GEOMETRY_JSON).orEmpty())
        }.getOrDefault(emptyList()).map { LatLng(it.lat, it.lng) }
        val segments = runCatching {
            json.decodeFromString<List<SegmentDto>>(intent.getStringExtra(EXTRA_SEGMENTS_JSON).orEmpty())
        }.getOrDefault(emptyList())
        networkSegments = segments
        routeSegmentIds = intent.getIntArrayExtra(EXTRA_ROUTE_SEGMENT_IDS)?.toList().orEmpty()
        todayGoldenCanonicalIds = intent.getIntArrayExtra(EXTRA_TODAY_GOLDEN_IDS)?.toSet().orEmpty()
        val goldenHits = intent.getIntArrayExtra(EXTRA_GOLDEN_HIT_IDS)?.toSet().orEmpty()
        if (stations.isEmpty()) return

        val savedTrack = if (routeKey.isNotBlank()) walkTrackStore.load(routeKey) else null
        walkTrackSession.restore(savedTrack?.points.orEmpty())
        goldenSoundCued.clear()
        goldenSoundCued.addAll(savedTrack?.goldenHitSegmentIds?.toSet().orEmpty())

        voiceTracker = VoiceCueTracker(stations).also { it.restore(voiceIdx) }
        progressTracker = WaypointProgressTracker(stations).also { it.restore(completed) }
        offPathDetector = OffPathDetector(routePolyline)
        goldenHitTracker = GoldenSegmentHitTracker(goldenHits, segments).also {
            it.restore(savedTrack?.goldenHitSegmentIds?.toSet().orEmpty())
        }

        emitState(
            RouteTrackingServiceState(
                active = true,
                trackEnabled = trackEnabled,
                voiceEnabled = voiceEnabled,
                completedWaypointIndex = completed,
                voiceAnnouncedIndex = voiceIdx,
                stationCount = stations.size,
                nextStationName = nextStationLabel(completed),
                trackedGeometry = walkTrackSession.geometryPoints().map { GeoPoint(it.lat, it.lng) },
                goldenHitSegmentIds = savedTrack?.goldenHitSegmentIds?.toSet().orEmpty(),
            ),
        )
    }

    private fun ensureVoiceController() {
        if (voiceController != null) return
        val locale = if (accountLocaleTag.isBlank()) Locale.getDefault() else Locale.forLanguageTag(accountLocaleTag)
        voiceController = VoiceGuidanceController(applicationContext, locale)
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        stopLocationUpdates()
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3_000L)
            .setMinUpdateIntervalMillis(2_000L)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                onLocation(location)
            }
        }
        locationCallback = callback
        fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    private fun onLocation(location: Location) {
        val point = GeoPoint(location.latitude, location.longitude)
        val latLng = LatLng(point.lat, point.lng)
        val accuracy = if (location.hasAccuracy()) location.accuracy else null
        val accuracyUsable = isRouteTrackAccuracyUsable(accuracy)
        var completed = _state.value.completedWaypointIndex
        var voiceIdx = _state.value.voiceAnnouncedIndex
        var offPathWarned = _state.value.offPathWarned
        var autoComplete = false
        var goldenHits = _state.value.goldenHitSegmentIds

        if (trackEnabled) {
            walkTrackSession.addFix(
                lat = location.latitude,
                lng = location.longitude,
                ele = if (location.hasAltitude()) location.altitude else null,
                timestampMs = location.time,
                accuracy = accuracy,
                speed = if (location.hasSpeed()) location.speed else null,
                bearing = if (location.hasBearing()) location.bearing else null,
            )
            if (accuracyUsable) {
                if (!offPathWarned && offPathDetector?.onLocation(latLng) == true) {
                    offPathWarned = true
                    cueController?.offPathWarning()
                    ensureVoiceController()
                    voiceController?.speak(getString(R.string.route_off_path_warning))
                }
                goldenHitTracker?.onLocation(latLng)?.let {
                    goldenHits = goldenHitTracker?.hitSegmentIds().orEmpty()
                }
            }
        }

        if (voiceEnabled && accuracyUsable) {
            voiceTracker?.onLocationUpdate(latLng)?.let { cue ->
                ensureVoiceController()
                voiceController?.speak(cue.toSpokenText(this, accountLocaleTag))
                voiceIdx = voiceTracker?.announcedCount() ?: voiceIdx
            }
        }

        if (trackEnabled && accuracyUsable) {
            var playedFinishSound = false
            progressTracker?.onLocationUpdate(latLng)?.let { nextCompleted ->
                if (nextCompleted > completed) {
                    for (arrived in (completed + 1)..nextCompleted) {
                        goldenCanonicalForFinishedHop(
                            arrivedStationIndex = arrived,
                            routeSegmentIds = routeSegmentIds,
                            todayGoldenCanonicalIds = todayGoldenCanonicalIds,
                            segments = networkSegments,
                            alreadyCued = goldenSoundCued,
                        )?.let { canon ->
                            goldenSoundCued.add(canon)
                            goldenHits = goldenHits + canon
                            cueController?.goldenHit()
                        }
                    }
                    completed = nextCompleted
                    if (completed >= stations.lastIndex) {
                        cueController?.routeCompleted()
                        playedFinishSound = true
                    } else {
                        cueController?.waypointReached()
                    }
                }
            }
            progressTracker?.let { tracker ->
                if (shouldAutoCompleteRoute(tracker, stations, latLng)) {
                    autoComplete = true
                    trackEnabled = false
                    if (!playedFinishSound) cueController?.routeCompleted()
                }
            }
        }

        persistTrack(goldenHits)

        emitState(
            _state.value.copy(
                active = true,
                trackEnabled = trackEnabled,
                voiceEnabled = voiceEnabled,
                myLocation = point,
                locationBearing = if (location.hasBearing()) location.bearing else null,
                locationSpeed = if (location.hasSpeed()) location.speed else null,
                completedWaypointIndex = completed,
                voiceAnnouncedIndex = voiceIdx,
                stationCount = stations.size,
                nextStationName = nextStationLabel(completed),
                trackedGeometry = walkTrackSession.geometryPoints().map { GeoPoint(it.lat, it.lng) },
                offPathWarned = offPathWarned,
                autoCompleteRequested = autoComplete || _state.value.autoCompleteRequested,
                goldenHitSegmentIds = goldenHits,
            ),
        )
        updateNotification()
    }

    private fun reannounceUpcoming() {
        val cue = voiceTracker?.peekUpcomingCue() ?: return
        ensureVoiceController()
        voiceController?.speak(cue.toSpokenText(this, accountLocaleTag))
    }

    private fun persistTrack(goldenHits: Set<Int> = _state.value.goldenHitSegmentIds) {
        if (routeKey.isBlank()) return
        walkTrackStore.save(routeKey, walkTrackSession.points, goldenHits)
    }

    fun consumeAutoCompleteRequest() {
        emitState(_state.value.copy(autoCompleteRequested = false))
    }

    fun trackPointsForUpload(): List<com.routy.app.logic.route.RouteWalkTrackPoint> = walkTrackSession.points

    private fun nextStationLabel(completedIndex: Int): String? {
        val nextIdx = voiceTracker?.upcomingStationIndex() ?: (completedIndex + 1)
        if (nextIdx !in stations.indices) return null
        val station = stations[nextIdx]
        return station.name ?: "#${station.nodeId}"
    }

    private fun stopTracking(clearSession: Boolean = false) {
        stopLocationUpdates()
        voiceController?.shutdown()
        voiceController = null
        cueController?.release()
        cueController = null
        voiceTracker = null
        progressTracker = null
        offPathDetector = null
        goldenHitTracker = null
        if (clearSession) {
            walkTrackSession.clear()
        }
        goldenSoundCued.clear()
        stations = emptyList()
        routePolyline = emptyList()
        routeSegmentIds = emptyList()
        networkSegments = emptyList()
        todayGoldenCanonicalIds = emptySet()
        emitState(RouteTrackingServiceState())
        syncTrackingActive(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopLocationUpdates() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        locationCallback = null
    }

    override fun onDestroy() {
        stopLocationUpdates()
        voiceController?.shutdown()
        voiceController = null
        cueController?.release()
        cueController = null
        syncTrackingActive(false)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_route_tracking_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopTrack = PendingIntent.getService(
            this,
            1,
            Intent(this, RouteTrackingForegroundService::class.java).setAction(ACTION_STOP_TRACK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val reannounce = PendingIntent.getService(
            this,
            2,
            Intent(this, RouteTrackingForegroundService::class.java).setAction(ACTION_REANNOUNCE),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val done = (_state.value.completedWaypointIndex + 1).coerceAtLeast(0)
        val total = stations.size.coerceAtLeast(1)
        val next = _state.value.nextStationName
        val title = if (next != null) {
            getString(R.string.notification_route_next_stop, next)
        } else {
            getString(R.string.notification_route_tracking_title)
        }
        val text = getString(R.string.notification_route_tracking_text_next, done, total, next ?: "—")
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.notification_route_reannounce), reannounce)
            .addAction(0, getString(R.string.route_track_stop), stopTrack)
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun emitState(state: RouteTrackingServiceState) {
        _state.value = state
        syncTrackingActive(state.active)
    }

    companion object {
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "route_tracking"
        private const val ACTION_STOP_TRACK = "com.routy.app.route.TRACK_STOP"
        private const val ACTION_STOP_ALL = "com.routy.app.route.TRACK_STOP_ALL"
        private const val ACTION_REANNOUNCE = "com.routy.app.route.TRACK_REANNOUNCE"
        private const val ACTION_SET_VOICE = "com.routy.app.route.TRACK_SET_VOICE"
        private const val ACTION_SET_TRACK = "com.routy.app.route.TRACK_SET_TRACK"
        const val EXTRA_STATIONS_JSON = "stations_json"
        const val EXTRA_ROUTE_GEOMETRY_JSON = "route_geometry_json"
        const val EXTRA_SEGMENTS_JSON = "segments_json"
        const val EXTRA_ROUTE_SEGMENT_IDS = "route_segment_ids"
        const val EXTRA_TODAY_GOLDEN_IDS = "today_golden_ids"
        const val EXTRA_GOLDEN_HIT_IDS = "golden_hit_ids"
        const val EXTRA_ROUTE_KEY = "route_key"
        const val EXTRA_LOCALE_TAG = "locale_tag"
        const val EXTRA_TRACK_ENABLED = "track_enabled"
        const val EXTRA_VOICE_ENABLED = "voice_enabled"
        const val EXTRA_COMPLETED_INDEX = "completed_index"
        const val EXTRA_VOICE_INDEX = "voice_index"

        private val json = Json { ignoreUnknownKeys = true }

        private val _trackingActive = MutableStateFlow(false)
        val trackingActive: StateFlow<Boolean> = _trackingActive.asStateFlow()

        fun syncTrackingActive(active: Boolean) {
            _trackingActive.value = active
        }

        fun needsForegroundService(trackEnabled: Boolean, voiceEnabled: Boolean) =
            trackEnabled || voiceEnabled

        fun start(
            context: Context,
            stations: List<RouteStation>,
            routeGeometry: List<GeoPoint>,
            segments: List<SegmentDto>,
            routeSegmentIds: List<Int>,
            todayGoldenCanonicalIds: Set<Int>,
            goldenHitSegmentIds: Set<Int>,
            routeKey: String,
            accountLocaleTag: String,
            trackEnabled: Boolean,
            voiceEnabled: Boolean,
            completedWaypointIndex: Int,
            voiceAnnouncedIndex: Int,
        ) {
            val intent = Intent(context, RouteTrackingForegroundService::class.java).apply {
                putExtra(EXTRA_STATIONS_JSON, json.encodeToString(stations))
                putExtra(EXTRA_ROUTE_GEOMETRY_JSON, json.encodeToString(routeGeometry))
                putExtra(EXTRA_SEGMENTS_JSON, json.encodeToString(segments))
                putExtra(EXTRA_ROUTE_SEGMENT_IDS, routeSegmentIds.toIntArray())
                putExtra(EXTRA_TODAY_GOLDEN_IDS, todayGoldenCanonicalIds.toIntArray())
                putExtra(EXTRA_GOLDEN_HIT_IDS, goldenHitSegmentIds.toIntArray())
                putExtra(EXTRA_ROUTE_KEY, routeKey)
                putExtra(EXTRA_LOCALE_TAG, accountLocaleTag)
                putExtra(EXTRA_TRACK_ENABLED, trackEnabled)
                putExtra(EXTRA_VOICE_ENABLED, voiceEnabled)
                putExtra(EXTRA_COMPLETED_INDEX, completedWaypointIndex)
                putExtra(EXTRA_VOICE_INDEX, voiceAnnouncedIndex)
            }
            context.startForegroundService(intent)
        }

        fun setVoiceEnabled(context: Context, enabled: Boolean) {
            if (!_trackingActive.value) return
            val intent = Intent(context, RouteTrackingForegroundService::class.java).apply {
                action = ACTION_SET_VOICE
                putExtra(EXTRA_VOICE_ENABLED, enabled)
            }
            context.startForegroundService(intent)
        }

        fun setTrackEnabled(context: Context, enabled: Boolean) {
            if (!_trackingActive.value) return
            val intent = Intent(context, RouteTrackingForegroundService::class.java).apply {
                action = ACTION_SET_TRACK
                putExtra(EXTRA_TRACK_ENABLED, enabled)
            }
            context.startForegroundService(intent)
        }

        fun stopTrack(context: Context) {
            context.startService(
                Intent(context, RouteTrackingForegroundService::class.java).setAction(ACTION_STOP_TRACK),
            )
        }

        fun stopAll(context: Context) {
            context.startService(
                Intent(context, RouteTrackingForegroundService::class.java).setAction(ACTION_STOP_ALL),
            )
        }
    }
}
