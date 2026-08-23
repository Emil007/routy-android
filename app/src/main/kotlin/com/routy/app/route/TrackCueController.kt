package com.routy.app.route

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.routy.app.R

/** Sound + haptic cues for waypoint/route completion and golden hits. */
class TrackCueController(context: Context) {
    private val appContext = context.applicationContext
    private val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun waypointReached() {
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        vibrate(40)
    }

    fun routeCompleted() {
        playRaw(R.raw.route_finish, 0.4f)
        vibrate(120)
    }

    fun goldenHit() {
        playRaw(R.raw.gold, 0.4f)
        vibrate(80)
    }

    fun offPathWarning() {
        tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 180)
        vibrate(100)
    }

    /** Stronger haptic for non-normal celebration tiers — no second finish jingle. */
    fun celebration() {
        vibrate(220)
    }

    fun release() {
        tone.release()
    }

    private fun playRaw(resId: Int, volume: Float) {
        runCatching {
            MediaPlayer.create(appContext, resId)?.apply {
                setAudioAttributes(audioAttributes)
                setVolume(volume, volume)
                setOnCompletionListener { it.release() }
                start()
            }
        }.onFailure {
            tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 250)
        }
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
