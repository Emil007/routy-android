package com.routy.app.route

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Short sound + haptic cues for waypoint/route completion (independent of TTS). */
class TrackCueController(context: Context) {
    private val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun waypointReached() {
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        vibrate(40)
    }

    fun routeCompleted() {
        tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 250)
        vibrate(120)
    }

    /** Stronger cue for non-normal celebration tiers on walk complete. */
    fun celebration() {
        tone.startTone(ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE, 450)
        vibrate(220)
    }

    fun release() {
        tone.release()
    }

    private fun vibrate(ms: Long) {
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(ms)
        }
    }
}
