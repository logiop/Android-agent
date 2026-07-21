package com.logiop.androidagent.brain

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import com.logiop.androidagent.R
import java.util.concurrent.Executors

/**
 * The agent's "brain": runs on-device inference with MediaPipe LLM Inference to
 * turn a command plus the current UI tree into a single [AgentAction].
 *
 * The model is loaded lazily on first use (so activating the overlay stays fast)
 * and freed via [close] when the overlay is dismissed, honoring the plan's
 * "load only while active" requirement. Inference is heavy and runs on a
 * single-thread worker; callbacks are delivered on the main thread.
 */
class Brain(private val context: Context) {

    interface Callback {
        fun onAction(action: AgentAction, raw: String)
        fun onError(message: String)
    }

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var engine: LlmInference? = null

    fun isModelAvailable(): Boolean = ModelRepository.isReady(context)

    fun plan(command: String, uiTree: String, callback: Callback) {
        worker.execute {
            try {
                val inference = ensureEngine()
                val prompt = PromptBuilder.build(command, uiTree)
                val raw = inference.generateResponse(prompt).orEmpty()
                val action = ActionParser.parse(raw)
                main.post {
                    if (action != null) {
                        callback.onAction(action, raw)
                    } else {
                        callback.onError(context.getString(R.string.brain_bad_output))
                    }
                }
            } catch (t: Throwable) {
                val message = t.message ?: context.getString(R.string.brain_failed)
                main.post { callback.onError(message) }
            }
        }
    }

    private fun ensureEngine(): LlmInference {
        engine?.let { return it }
        val options = LlmInferenceOptions.builder()
            .setModelPath(ModelRepository.modelFile(context).absolutePath)
            .setMaxTokens(512)
            .setTopK(40)
            .setTemperature(0.2f)
            .setRandomSeed(0)
            .build()
        return LlmInference.createFromOptions(context, options).also { engine = it }
    }

    /** Frees the model from memory. Safe to call when the overlay is dismissed. */
    fun close() {
        worker.execute {
            engine?.close()
            engine = null
        }
        worker.shutdown()
    }
}
