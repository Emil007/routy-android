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
import com.routy.app.RoutyApplication
import com.routy.app.core.storage.RecordingSnapshotStore
import com.routy.app.logic.api.GeoPoint
import com.routy.app.logic.recording.RecordingPhase
import com.routy.app.logic.recording.RecordingPoint
import com.routy.app.logic.recording.RecordingSession
import com.routy.app.logic.recording.appendedPointsAfterSnapshot
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

class RecordingForegroundService : Service() {
    private val session = RecordingSession()
    private val binder = LocalBinder()
    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var locationCallback: LocationCallback? = null
    private lateinit var snapshotStore: RecordingSnapshotStore

    private val _state = MutableStateFlow(RecordingServiceState())
    val state: StateFlow<RecordingServiceState> = _state.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): RecordingForegroundService = this@RecordingForegroundService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        snapshotStore = (application as RoutyApplication).recordingSnapshotStore
        createNotificationChannel()
        restoreIfNeeded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> finish()
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        if (session.phase == RecordingPhase.IDLE) beginRecording()
        return START_STICKY
    }

    private fun restoreIfNeeded() {
        val snapshot = snapshotStore.loadSnapshot() ?: return
        if (snapshot.phase == RecordingPhase.IDLE) return
        session.restore(snapshot)
        if (snapshot.phase != RecordingPhase.CONFIRM) {
            appendedPointsAfterSnapshot(snapshot, snapshotStore.loadAppendedPoints())
                .forEach { session.addPoint(it) }
        }
        _state.value = RecordingServiceState(
            phase = session.phase,
            pointCount = session.points.size,
            lengthM = session.stats(walkSpeedKmhFallback = 5.0).lengthM,
        )
        if (session.phase == RecordingPhase.RECORDING) startLocationUpdates()
    }

    fun recordedPoints(): List<RecordingPoint> = session.points

    @SuppressLint("MissingPermission")
    private fun beginRecording() {
        if (session.phase != RecordingPhase.IDLE) return
        session.start(System.currentTimeMillis())
        persistSnapshot()
        _state.value = RecordingServiceState(phase = session.phase)
        startLocationUpdates()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (locationCallback != null) return
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
                val snap = session.snapshot()
                val last = (snap.points + snap.pausedPoints).lastOrNull()
                if ((session.phase == RecordingPhase.RECORDING || session.phase == RecordingPhase.PAUSED) &&
                    shouldRecordPoint(last, point)
                ) {
                    session.addPoint(point)
                    snapshotStore.appendPoint(point)
                    persistSnapshot()
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
        persistSnapshot()
        _state.value = _state.value.copy(phase = session.phase)
        updateNotification()
    }

    fun resume() {
        session.resume()
        persistSnapshot()
        _state.value = _state.value.copy(phase = session.phase)
        startLocationUpdates()
        updateNotification()
    }

    fun finish(): List<RecordingPoint> {
        session.finish()
        persistSnapshot()
        _state.value = _state.value.copy(phase = session.phase)
        stopLocationUpdates()
        return session.points
    }

    fun stopAfterCommitOrDiscard() {
        session.discard()
        snapshotStore.clear()
        stopLocationUpdates()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun persistSnapshot() {
        snapshotStore.saveSnapshot(session.snapshot())
    }

    private fun stopLocationUpdates() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        locationCallback = null
    }

    override fun onDestroy() {
        persistSnapshot()
        stopLocationUpdates()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notification_recording_channel_name), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val pauseIntent = PendingIntent.getService(this, 1, Intent(this, RecordingForegroundService::class.java).setAction(ACTION_PAUSE), PendingIntent.FLAG_IMMUTABLE)
        val resumeIntent = PendingIntent.getService(this, 2, Intent(this, RecordingForegroundService::class.java).setAction(ACTION_RESUME), PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = PendingIntent.getService(this, 3, Intent(this, RecordingForegroundService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val distanceKm = "%.2f".format(session.stats(walkSpeedKmhFallback = 5.0).lengthM / 1000.0)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_recording_title))
            .setContentText(getString(R.string.notification_recording_text, session.points.size, distanceKm))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
        if (session.phase == RecordingPhase.RECORDING) {
            builder.addAction(0, getString(R.string.record_pause), pauseIntent)
            builder.addAction(0, getString(R.string.record_stop), stopIntent)
        } else if (session.phase == RecordingPhase.PAUSED) {
            builder.addAction(0, getString(R.string.record_resume), resumeIntent)
            builder.addAction(0, getString(R.string.record_stop), stopIntent)
        }
        return builder.build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "recording"
        private const val ACTION_PAUSE = "com.routy.app.recording.PAUSE"
        private const val ACTION_RESUME = "com.routy.app.recording.RESUME"
        private const val ACTION_STOP = "com.routy.app.recording.STOP"
    }
}
