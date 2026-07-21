package com.logiop.androidagent.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.logiop.androidagent.R

/**
 * Thin wrapper around [SpeechRecognizer] configured for on-device Italian
 * speech recognition.
 *
 * Must be created and used on the main thread (as [SpeechRecognizer] requires).
 * The caller is responsible for holding the `RECORD_AUDIO` runtime permission
 * before calling [start]; if it is missing the recognizer reports
 * [SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS] via [Callbacks.onError].
 */
class VoiceRecognizer(private val context: Context) {

    interface Callbacks {
        fun onReady() {}
        fun onResult(text: String)
        fun onError(message: String)
    }

    private var recognizer: SpeechRecognizer? = null

    var isListening = false
        private set

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(callbacks: Callbacks) {
        if (isListening) return
        if (!isAvailable()) {
            callbacks.onError(context.getString(R.string.voice_unavailable))
            return
        }

        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr
        sr.setRecognitionListener(listenerFor(callbacks))

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE_IT)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, LANGUAGE_IT)
            // Prefer on-device recognition; falls back to online only if needed.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        isListening = true
        sr.startListening(intent)
    }

    fun cancel() {
        recognizer?.cancel()
        isListening = false
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
        isListening = false
    }

    private fun releaseRecognizer() {
        isListening = false
        recognizer?.destroy()
        recognizer = null
    }

    private fun listenerFor(callbacks: Callbacks) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = callbacks.onReady()
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            releaseRecognizer()
            callbacks.onError(messageFor(error))
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            releaseRecognizer()
            if (text.isBlank()) {
                callbacks.onError(context.getString(R.string.voice_no_speech))
            } else {
                callbacks.onResult(text)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun messageFor(error: Int): String {
        val res = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> R.string.voice_err_audio
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            -> R.string.voice_err_network
            SpeechRecognizer.ERROR_NO_MATCH -> R.string.voice_err_no_match
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> R.string.voice_err_timeout
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.voice_err_permission
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> R.string.voice_err_busy
            else -> R.string.voice_err_generic
        }
        return context.getString(res)
    }

    private companion object {
        const val LANGUAGE_IT = "it-IT"
    }
}
