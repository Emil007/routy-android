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
import com.routy.app.core.storage.RouteProgressStore
import com.routy.app.logic.api.GeoPoint
import com.routy.app.logic.api.RouteStation
import com.routy.app.logic.geo.LatLng
import com.routy.app.logic.route.VoiceCueTracker
import com.routy.app.logic.route.WaypointProgressTracker
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class RouteTrackingServiceState(
    val active: Boolean = false,
    val myLocation: GeoPoint? = null,
    val completedWaypointIndex: Int = -1,
    val voiceAnnouncedIndex: Int = 0,
    val stationCount: Int = 0,
    val nextStationName: String? = null,
)

/**
 * Keeps GPS + waypoint progress + TTS alive while the phone is locked / in a pocket.
 * Compose-only [FusedLocationProviderClient] callbacks are paused with the Activity — same
 * reason [com.routy.app.recording.RecordingForegroundService] exists for path recording.
 */
class RouteTrackingForegroundService : Service() {
    private val binder = LocalBinder()
    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var locationCallback: LocationCallback? = null
    private lateinit var progressStore: RouteProgressStore
    private var voiceController: VoiceGuidanceController? = null
    private var cueController: TrackCueController? = null
    private var voiceTracker: VoiceCueTracker? = null
    private var progressTracker: WaypointProgressTracker? = null

    private var routeKey: String = ""
    private var accountLocaleTag: String = ""
    private var voiceEnabled: Boolean = false
    private var stations: List<RouteStation> = emptyList()

    private val _state = MutableStateFlow(RouteTrackingServiceState())
    val state: StateFlow<RouteTrackingServiceState> = _state.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): RouteTrackingForegroundService = this@RouteTrackingForegroundService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        progressStore = (application as RoutyApplication).routeProgressStore
        createNotificationChannel()
        cueController = TrackCueController(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTracking()
                return START_NOT_STICKY
            }
            ACTION_SET_VOICE -> {
                voiceEnabled = intent.getBooleanExtra(EXTRA_VOICE_ENABLED, voiceEnabled)
            }
            else -> {
                val stationsJson = intent?.getStringExtra(EXTRA_STATIONS_JSON)
                if (!stationsJson.isNullOrBlank()) {
                    configureFromIntent(intent)
                }
            }
        }

        if (stations.isEmpty()) {
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
        voiceEnabled = intent.getBooleanExtra(EXTRA_VOICE_ENABLED, false)
        val completed = intent.getIntExtra(EXTRA_COMPLETED_INDEX, -1)
        val voiceIdx = intent.getIntExtra(EXTRA_VOICE_INDEX, 0)
        stations = runCatching {
            json.decodeFromString<List<RouteStation>>(intent.getStringExtra(EXTRA_STATIONS_JSON)!!)
        }.getOrDefault(emptyList())
        if (stations.isEmpty()) return

        voiceTracker = VoiceCueTracker(stations).also { it.restore(voiceIdx) }
        progressTracker = WaypointProgressTracker(stations).also { it.restore(completed) }
        emitState(
            RouteTrackingServiceState(
                active = true,
                completedWaypointIndex = completed,
                voiceAnnouncedIndex = voiceIdx,
                stationCount = stations.size,
                nextStationName = nextStationLabel(completed),
            ),
        )
    }

    private fun ensureVoiceController() {
        if (voiceController != null) return
        val locale = if (accountLocaleTag.isBlank()) {
            Locale.getDefault()
        } else {
            Locale.forLanguageTag(accountLocaleTag)
        }
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
                onLocation(GeoPoint(location.latitude, location.longitude))
            }
        }
        locationCallback = callback
        fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    private fun onLocation(point: GeoPoint) {
        val latLng = LatLng(point.lat, point.lng)
        var completed = _state.value.completedWaypointIndex
        var voiceIdx = _state.value.voiceAnnouncedIndex

        if (voiceEnabled) {
            voiceTracker?.onLocationUpdate(latLng)?.let { cue ->
                ensureVoiceController()
                voiceController?.speak(cue.toSpokenText(this, accountLocaleTag))
                voiceIdx = voiceTracker?.announcedCount() ?: voiceIdx
            }
        }

        progressTracker?.onLocationUpdate(latLng)?.let { nextCompleted ->
            if (nextCompleted > completed) {
                completed = nextCompleted
                if (completed >= stations.lastIndex) {
                    cueController?.routeCompleted()
                } else {
                    cueController?.waypointReached()
                }
            }
        }

        if (routeKey.isNotBlank()) {
            progressStore.save(routeKey, completed, voiceIdx)
        }

        emitState(
            _state.value.copy(
                active = true,
                myLocation = point,
                completedWaypointIndex = completed,
                voiceAnnouncedIndex = voiceIdx,
                stationCount = stations.size,
                nextStationName = nextStationLabel(completed),
            ),
        )
        updateNotification()
    }

    private fun nextStationLabel(completedIndex: Int): String? {
        val nextIdx = completedIndex + 1
        if (nextIdx !in stations.indices) return null
        return stations[nextIdx].name ?: "#${stations[nextIdx].nodeId}"
    }

    private fun stopTracking() {
        stopLocationUpdates()
        voiceController?.shutdown()
        voiceController = null
        cueController?.release()
        cueController = null
        voiceTracker = null
        progressTracker = null
        stations = emptyList()
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
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, RouteTrackingForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val done = (_state.value.completedWaypointIndex + 1).coerceAtLeast(0)
        val total = stations.size.coerceAtLeast(1)
        val next = _state.value.nextStationName
        val text = if (next != null) {
            getString(R.string.notification_route_tracking_text_next, done, total, next)
        } else {
            getString(R.string.notification_route_tracking_text, done, total)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_route_tracking_title))
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.route_track_stop), stop)
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
        private const val ACTION_STOP = "com.routy.app.route.TRACK_STOP"
        private const val ACTION_SET_VOICE = "com.routy.app.route.TRACK_SET_VOICE"
        const val EXTRA_STATIONS_JSON = "stations_json"
        const val EXTRA_ROUTE_KEY = "route_key"
        const val EXTRA_LOCALE_TAG = "locale_tag"
        const val EXTRA_VOICE_ENABLED = "voice_enabled"
        const val EXTRA_COMPLETED_INDEX = "completed_index"
        const val EXTRA_VOICE_INDEX = "voice_index"

        private val json = Json { ignoreUnknownKeys = true }

        private val _trackingActive = MutableStateFlow(false)
        val trackingActive: StateFlow<Boolean> = _trackingActive.asStateFlow()

        fun syncTrackingActive(active: Boolean) {
            _trackingActive.value = active
        }

        fun start(
            context: Context,
            stations: List<RouteStation>,
            routeKey: String,
            accountLocaleTag: String,
            voiceEnabled: Boolean,
            completedWaypointIndex: Int,
            voiceAnnouncedIndex: Int,
        ) {
            val intent = Intent(context, RouteTrackingForegroundService::class.java).apply {
                putExtra(EXTRA_STATIONS_JSON, json.encodeToString(stations))
                putExtra(EXTRA_ROUTE_KEY, routeKey)
                putExtra(EXTRA_LOCALE_TAG, accountLocaleTag)
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

        fun stop(context: Context) {
            context.startService(
                Intent(context, RouteTrackingForegroundService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
