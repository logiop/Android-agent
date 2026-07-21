package com.logiop.androidagent.agent

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.logiop.androidagent.R
import com.logiop.androidagent.brain.AgentAction
import com.logiop.androidagent.brain.Brain
import com.logiop.androidagent.hands.AgentAccessibilityService
import com.logiop.androidagent.hands.AppLauncher
import com.logiop.androidagent.memory.Trajectory
import com.logiop.androidagent.memory.TrajectoryStep
import com.logiop.androidagent.security.AuditLog

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
    private val auditLog: AuditLog,
    private val host: Host,
) {

    interface Host {
        fun onThinking()
        fun onInfo(message: String)
        fun onFinished(message: String)
        fun requestConfirmation(description: String, onResult: (Boolean) -> Unit)

        /** A successful run worth turning into a skill (pending human review). */
        fun onLearnable(command: String, trajectory: Trajectory)
    }

    private val main = Handler(Looper.getMainLooper())

    private var command = ""
    private var step = 0
    private var noProgress = 0
    private var lastSignature: String? = null
    private var running = false

    // Trajectory recorded during the current run, for skill compilation.
    private val trajectorySteps = mutableListOf<TrajectoryStep>()
    private var controlSteps = 0
    private var startFingerprint = ""
    private var targetApp = ""

    val isRunning: Boolean get() = running

    fun start(command: String) {
        if (running) return
        this.command = command
        step = 0
        noProgress = 0
        lastSignature = null
        running = true
        trajectorySteps.clear()
        controlSteps = 0
        startFingerprint = ""
        targetApp = ""
        auditLog.record("COMMAND \"$command\"")
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

        if (startFingerprint.isEmpty()) {
            startFingerprint = service.captureDescriptor().fingerprint()
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
                val endFingerprint = AgentAccessibilityService.instance
                    ?.captureDescriptor()?.fingerprint().orEmpty()
                if (StateVerifier.isLearnable(startFingerprint, endFingerprint, controlSteps)) {
                    auditLog.record("LEARNABLE steps=$controlSteps")
                    host.onLearnable(command, Trajectory(command, targetApp, trajectorySteps.toList()))
                }
                finish(context.getString(R.string.agent_done))
                return
            }
            // Navigation is non-destructive and allowed regardless of whitelist.
            "open_app" -> {
                auditLog.record("open_app \"${action.target}\"")
                AppLauncher.openApp(context, action.target)
                host.onInfo(describe(action))
                continueAfterAction()
                return
            }
            "search" -> {
                val query = action.text.ifBlank { action.target }
                auditLog.record("search \"$query\"")
                AppLauncher.googleSearch(context, query)
                host.onInfo(describe(action))
                continueAfterAction()
                return
            }
        }

        // Control actions operate inside the current app: enforce the whitelist.
        if (currentPackage == null || !whitelist.isAllowed(currentPackage)) {
            auditLog.record("BLOCKED pkg=${currentPackage ?: "?"} ${action.action} \"${action.target}\"")
            finish(context.getString(R.string.agent_blocked_app, currentPackage ?: "?"))
            return
        }

        if (SafetyPolicy.isIrreversible(action)) {
            auditLog.record("CONFIRM_REQUEST ${describe(action)}")
            host.requestConfirmation(describe(action)) { confirmed ->
                if (!running) return@requestConfirmation
                if (confirmed) {
                    auditLog.record("CONFIRM_GRANTED ${describe(action)}")
                    performControl(action)
                    continueAfterAction()
                } else {
                    auditLog.record("CONFIRM_DENIED ${describe(action)}")
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
        val argument = if (action.action == "type") action.text.ifBlank { action.target } else action.target
        val locator = when (action.action) {
            "tap" -> service.clickByText(action.target)
            "type" -> service.typeText(action.text.ifBlank { action.target })
            "scroll" -> service.scroll(forward = action.target != "up" && action.target != "backward")
            else -> null
        }
        val ok = locator != null
        if (ok) {
            controlSteps++
            if (targetApp.isEmpty()) targetApp = service.currentPackage().orEmpty()
            trajectorySteps += TrajectoryStep(
                actionType = action.action,
                argument = argument,
                locator = locator!!,
                stateFingerprint = service.captureDescriptor().fingerprint(),
                irreversible = SafetyPolicy.isIrreversible(action),
            )
        }
        auditLog.record("${action.action} \"$argument\" ok=$ok")
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
        auditLog.record("END $message")
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
