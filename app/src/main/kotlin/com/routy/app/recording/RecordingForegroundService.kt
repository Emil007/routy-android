package com.routy.app.recording

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
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
import com.routy.app.logic.api.GeoPoint
import com.routy.app.logic.recording.RecordingPhase
import com.routy.app.logic.recording.RecordingPoint
import com.routy.app.logic.recording.RecordingSession
import com.routy.app.logic.recording.shouldRecordPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RecordingServiceState(
    val phase: RecordingPhase = RecordingPhase.IDLE,
    val pointCount: Int = 0,
    val lengthM: Double = 0.0,
    val currentPosition: GeoPoint? = null,
    val locationError: Boolean = false,
)

/**
 * The one piece of this app that has to keep running with the screen off — everything else
 * (map, route suggestions, the WebView shell) can pause the moment the app leaves the
 * foreground, but a walk being recorded can't just stop because the phone went in a pocket.
 * Runs as a started + bound service: started so `startForegroundService()` keeps it alive past
 * whatever unbinds happen while the app backgrounds, bound so RecordingScreen can talk to it and
 * observe [state] directly while it's on screen. The actual point-buffering logic lives in
 * `:logic`'s RecordingSession, unchanged from the web port — this class only owns the
 * Android-specific parts: the location callback, the foreground notification, and translating
 * GPS fixes into RecordingSession.addPoint() calls.
 */
class RecordingForegroundService : Service() {
    private val session = RecordingSession()
    private val binder = LocalBinder()
    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var locationCallback: LocationCallback? = null

    private val _state = MutableStateFlow(RecordingServiceState())
    val state: StateFlow<RecordingServiceState> = _state.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): RecordingForegroundService = this@RecordingForegroundService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        beginRecording()
        return START_STICKY
    }

    /** Caller (RecordingScreen) must already hold ACCESS_FINE_LOCATION before starting this service at all. */
    @SuppressLint("MissingPermission")
    private fun beginRecording() {
        if (session.phase != RecordingPhase.IDLE) return
        session.start(System.currentTimeMillis())
        _state.value = RecordingServiceState(phase = session.phase)

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val point = RecordingPoint(
                    lat = location.latitude,
                    lng = location.longitude,
                    ele = if (location.hasAltitude()) location.altitude else null,
                    timestampMs = System.currentTimeMillis(),
                )
                val currentPosition = GeoPoint(point.lat, point.lng)
                if (session.phase == RecordingPhase.RECORDING && shouldRecordPoint(session.points.lastOrNull(), point)) {
                    session.addPoint(point)
                    _state.value = _state.value.copy(
                        pointCount = session.points.size,
                        lengthM = session.stats(walkSpeedKmhFallback = 5.0).lengthM,
                        currentPosition = currentPosition,
                    )
                    updateNotification()
                } else {
                    _state.value = _state.value.copy(currentPosition = currentPosition)
                }
            }
        }
        locationCallback = callback
        fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnFailureListener { _state.value = _state.value.copy(locationError = true) }
    }

    fun pause() {
        session.pause()
        _state.value = _state.value.copy(phase = session.phase)
        updateNotification()
    }

    fun resume() {
        session.resume()
        _state.value = _state.value.copy(phase = session.phase)
        updateNotification()
    }

    /** Moves to CONFIRM and stops GPS updates (nothing left to record) — the notification stays up until [stopAfterCommitOrDiscard]. */
    fun finish(): List<RecordingPoint> {
        session.finish()
        _state.value = _state.value.copy(phase = session.phase)
        stopLocationUpdates()
        return session.points
    }

    /** Called once the confirm step is done, whichever way it ends — commit succeeded or the user discarded. */
    fun stopAfterCommitOrDiscard() {
        session.discard()
        stopLocationUpdates()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopLocationUpdates() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        locationCallback = null
    }

    override fun onDestroy() {
        stopLocationUpdates()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_recording_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val distanceKm = "%.2f".format(session.stats(walkSpeedKmhFallback = 5.0).lengthM / 1000.0)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_recording_title))
            .setContentText(getString(R.string.notification_recording_text, session.points.size, distanceKm))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "recording"
    }
}
