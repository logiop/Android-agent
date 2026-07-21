package com.logiop.androidagent.agent

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.logiop.androidagent.R
import com.logiop.androidagent.brain.AgentAction
import com.logiop.androidagent.brain.Brain
import com.logiop.androidagent.hands.AgentAccessibilityService
import com.logiop.androidagent.hands.AppLauncher

/**
 * Drives the agent loop: command + UI tree → LLM action → execute → re-read →
 * repeat, up to [MAX_STEPS]. Stops after [MAX_NO_PROGRESS] steps without any
 * change on screen instead of insisting.
 *
 * Navigation actions (open_app / search) are always allowed. Control actions
 * (tap / type / scroll) run only inside a whitelisted app, and irreversible taps
 * require the host to confirm first.
 */
class AgentLoop(
    private val context: Context,
    private val brain: Brain,
    private val whitelist: Whitelist,
    private val host: Host,
) {

    interface Host {
        fun onThinking()
        fun onInfo(message: String)
        fun onFinished(message: String)
        fun requestConfirmation(description: String, onResult: (Boolean) -> Unit)
    }

    private val main = Handler(Looper.getMainLooper())

    private var command = ""
    private var step = 0
    private var noProgress = 0
    private var lastSignature: String? = null
    private var running = false

    val isRunning: Boolean get() = running

    fun start(command: String) {
        if (running) return
        this.command = command
        step = 0
        noProgress = 0
        lastSignature = null
        running = true
        runStep()
    }

    fun stop() {
        running = false
        main.removeCallbacksAndMessages(null)
    }

    private fun runStep() {
        if (!running) return
        if (step >= MAX_STEPS) {
            finish(context.getString(R.string.agent_max_steps))
            return
        }
        val service = AgentAccessibilityService.instance
        if (service == null) {
            finish(context.getString(R.string.accessibility_needed))
            return
        }

        val snapshot = service.captureScreen()
        val currentPackage = service.currentPackage()
        step++
        host.onThinking()

        brain.plan(command, snapshot.compactText, object : Brain.Callback {
            override fun onAction(action: AgentAction, raw: String) {
                handleAction(action, currentPackage)
            }

            override fun onError(message: String) {
                finish(message)
            }
        })
    }

    private fun handleAction(action: AgentAction, currentPackage: String?) {
        if (!running) return

        when (action.action) {
            "done" -> {
                finish(context.getString(R.string.agent_done))
                return
            }
            // Navigation is non-destructive and allowed regardless of whitelist.
            "open_app" -> {
                AppLauncher.openApp(context, action.target)
                host.onInfo(describe(action))
                continueAfterAction()
                return
            }
            "search" -> {
                AppLauncher.googleSearch(context, action.text.ifBlank { action.target })
                host.onInfo(describe(action))
                continueAfterAction()
                return
            }
        }

        // Control actions operate inside the current app: enforce the whitelist.
        if (currentPackage == null || !whitelist.isAllowed(currentPackage)) {
            finish(context.getString(R.string.agent_blocked_app, currentPackage ?: "?"))
            return
        }

        if (SafetyPolicy.isIrreversible(action)) {
            host.requestConfirmation(describe(action)) { confirmed ->
                if (!running) return@requestConfirmation
                if (confirmed) {
                    performControl(action)
                    continueAfterAction()
                } else {
                    finish(context.getString(R.string.agent_cancelled))
                }
            }
        } else {
            performControl(action)
            continueAfterAction()
        }
    }

    private fun performControl(action: AgentAction) {
        val service = AgentAccessibilityService.instance ?: return
        val ok = when (action.action) {
            "tap" -> service.clickByText(action.target)
            "type" -> service.typeText(action.text.ifBlank { action.target })
            "scroll" -> service.scroll(forward = action.target != "up" && action.target != "backward")
            else -> false
        }
        host.onInfo(describe(action) + if (ok) "" else " " + context.getString(R.string.agent_action_failed))
    }

    /** Lets the UI settle, checks for progress, then runs the next step. */
    private fun continueAfterAction() {
        main.postDelayed({
            if (!running) return@postDelayed
            val service = AgentAccessibilityService.instance
            val signature = (service?.captureScreen()?.compactText.orEmpty()) +
                "|" + (service?.currentPackage() ?: "")
            if (signature == lastSignature) {
                noProgress++
            } else {
                noProgress = 0
                lastSignature = signature
            }
            if (noProgress >= MAX_NO_PROGRESS) {
                finish(context.getString(R.string.agent_no_progress))
            } else {
                runStep()
            }
        }, SETTLE_DELAY_MS)
    }

    private fun finish(message: String) {
        running = false
        main.removeCallbacksAndMessages(null)
        host.onFinished(message)
    }

    private fun describe(action: AgentAction): String {
        val detail = action.text.ifBlank { action.target }
        return if (detail.isBlank()) action.action else "${action.action}: $detail"
    }

    private companion object {
        const val MAX_STEPS = 15
        const val MAX_NO_PROGRESS = 3
        const val SETTLE_DELAY_MS = 700L
    }
}
