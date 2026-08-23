package com.routy.app.route

import android.content.Context
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import com.routy.app.R
import com.routy.app.logic.geo.CompassPoint
import com.routy.app.logic.route.VoiceCue
import java.util.Locale

class VoiceGuidanceController(context: Context, private val ttsLocale: Locale) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(audioAttributes)
        .build()

    private var tts: TextToSpeech? = null
    private var ready = false
    private var speaking = false

    init {
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = ttsLocale
                tts?.setAudioAttributes(audioAttributes)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        speaking = false
                        audioManager.abandonAudioFocusRequest(focusRequest)
                    }

                    @Deprecated("Deprecated in TextToSpeech")
                    override fun onError(utteranceId: String?) {
                        speaking = false
                        audioManager.abandonAudioFocusRequest(focusRequest)
                    }
                })
            }
        }
    }

    fun speak(text: String) {
        if (!ready || speaking) return
        speaking = true
        audioManager.requestAudioFocus(focusRequest)
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "routy-voice-cue")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        speaking = false
    }
}

@Composable
fun rememberVoiceGuidanceController(accountLocaleTag: String): VoiceGuidanceController {
    val context = LocalContext.current
    val deviceLocale = LocalLocale.current.platformLocale
    val targetLocale = if (accountLocaleTag.isBlank()) deviceLocale else Locale.forLanguageTag(accountLocaleTag)
    val controller = remember(accountLocaleTag) { VoiceGuidanceController(context, targetLocale) }
    DisposableEffect(Unit) {
        onDispose { controller.shutdown() }
    }
    return controller
}

private fun Context.stringForAccountLocale(localeTag: String, resId: Int, vararg formatArgs: Any): String {
    if (localeTag.isBlank()) return getString(resId, *formatArgs)
    val config = Configuration(resources.configuration)
    config.setLocale(Locale.forLanguageTag(localeTag))
    return createConfigurationContext(config).getString(resId, *formatArgs)
}

private fun Context.formatStationLabel(
    localeTag: String,
    name: String?,
    viaSegmentName: String?,
): String {
    val base = name ?: stringForAccountLocale(localeTag, R.string.route_station_fallback)
    return if (viaSegmentName.isNullOrBlank()) {
        base
    } else {
        stringForAccountLocale(localeTag, R.string.route_via, base, viaSegmentName)
    }
}

fun VoiceCue.toSpokenText(context: Context, accountLocaleTag: String): String = when (this) {
    is VoiceCue.ArrivingAtNext -> context.stringForAccountLocale(
        accountLocaleTag,
        R.string.route_voice_arrived_next,
        context.formatStationLabel(accountLocaleTag, hereName, hereViaSegmentName),
        context.formatStationLabel(accountLocaleTag, nextName, nextViaSegmentName),
        direction.toSpokenLabel(context, accountLocaleTag),
    )
    is VoiceCue.ArrivingAtFinal -> context.stringForAccountLocale(
        accountLocaleTag,
        R.string.route_voice_arrived_final,
        context.formatStationLabel(accountLocaleTag, hereName, hereViaSegmentName),
    )
}

private fun CompassPoint.toSpokenLabel(context: Context, accountLocaleTag: String): String =
    context.stringForAccountLocale(
        accountLocaleTag,
        when (this) {
            CompassPoint.N -> R.string.route_compass_n
            CompassPoint.NE -> R.string.route_compass_ne
            CompassPoint.E -> R.string.route_compass_e
            CompassPoint.SE -> R.string.route_compass_se
            CompassPoint.S -> R.string.route_compass_s
            CompassPoint.SW -> R.string.route_compass_sw
            CompassPoint.W -> R.string.route_compass_w
            CompassPoint.NW -> R.string.route_compass_nw
        },
    )
