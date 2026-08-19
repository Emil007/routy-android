package com.routy.app.route

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.routy.app.R
import com.routy.app.logic.geo.CompassPoint
import com.routy.app.logic.route.VoiceCue
import java.util.Locale

/**
 * TextToSpeech + audio-focus wrapper, the one piece of M5 that couldn't live in :logic (it's
 * pure Android framework, no meaningful pure-Kotlin logic to extract — the actual cue *decision*
 * logic is VoiceCueTracker, already ported and tested). Requests transient, duckable focus
 * (AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) with USAGE_ASSISTANCE_NAVIGATION_GUIDANCE audio
 * attributes — the same pattern turn-by-turn nav apps use to duck Spotify/music automatically
 * rather than pausing it outright, and releases focus itself once each utterance finishes so
 * ducked audio recovers immediately after each cue instead of staying quiet for the whole walk.
 */
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

    init {
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                // Voice cues should follow the account/server locale, not the device locale.
                // (Full UI localization is intentionally bigger; TTS language is the low-risk first win.)
                tts?.language = ttsLocale
                tts?.setAudioAttributes(audioAttributes)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        audioManager.abandonAudioFocusRequest(focusRequest)
                    }

                    @Deprecated("Deprecated in TextToSpeech, but still the callback invoked pre-API 21")
                    override fun onError(utteranceId: String?) {
                        audioManager.abandonAudioFocusRequest(focusRequest)
                    }
                })
            }
        }
    }

    fun speak(text: String) {
        if (!ready) return
        audioManager.requestAudioFocus(focusRequest)
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "routy-voice-cue")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

@Composable
fun rememberVoiceGuidanceController(accountLocaleTag: String): VoiceGuidanceController {
    val context = LocalContext.current
    val targetLocale = if (accountLocaleTag.isBlank()) Locale.getDefault() else Locale.forLanguageTag(accountLocaleTag)
    val controller = remember(accountLocaleTag) { VoiceGuidanceController(context, targetLocale) }
    DisposableEffect(Unit) {
        onDispose { controller.shutdown() }
    }
    return controller
}

/** Mirrors RouteGenerator.tsx's voiceArrivedNext/voiceArrivedFinal template strings — station.name || t("route.station") for the fallback. */
@Composable
fun VoiceCue.toSpokenText(): String = when (this) {
    is VoiceCue.ArrivingAtNext -> stringResource(
        R.string.route_voice_arrived_next,
        hereName ?: stringResource(R.string.route_station_fallback),
        nextName ?: stringResource(R.string.route_station_fallback),
        direction.toSpokenLabel(),
    )
    is VoiceCue.ArrivingAtFinal -> stringResource(
        R.string.route_voice_arrived_final,
        hereName ?: stringResource(R.string.route_station_fallback),
    )
}

@Composable
private fun CompassPoint.toSpokenLabel(): String = stringResource(
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
