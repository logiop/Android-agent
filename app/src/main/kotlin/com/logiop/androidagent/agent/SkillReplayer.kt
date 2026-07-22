package com.logiop.androidagent.agent

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.logiop.androidagent.R
import com.logiop.androidagent.brain.AgentAction
import com.logiop.androidagent.brain.Brain
import com.logiop.androidagent.hands.AgentAccessibilityService
import com.logiop.androidagent.hands.AppLauncher
import com.logiop.androidagent.memory.SkillStep
import com.logiop.androidagent.memory.SkillStore
import com.logiop.androidagent.memory.SkillWithSteps
import com.logiop.androidagent.memory.TrajectoryCodec
import com.logiop.androidagent.security.AuditLog

/**
 * Replays a learned skill step-by-step without the LLM, with graduated
 * tolerance to UI deviation:
 * - action succeeds → proceed;
 * - action fails → try to dismiss an unexpected transient dialog and retry;
 * - still failing → a capped single-step LLM fallback nudges past the obstacle;
 * - fallback exhausted → abort and hand the command back to the full LLM loop.
 *
 * Irreversible steps keep the human confirmation even in replay.
 */
class SkillReplayer(
    private val context: Context,
    private val skillStore: SkillStore,
    private val auditLog: AuditLog,
    private val brain: Brain,
    private val host: Host,
) {

    interface Host {
        fun onInfo(message: String)
        fun onFinished(message: String)
        fun requestConfirmation(description: String, onResult: (Boolean) -> Unit)

        /** Deviation/abort: hand the original command back to the LLM loop. */
        fun onFallback(command: String)
    }

    private val main = Handler(Looper.getMainLooper())

    private lateinit var skill: SkillWithSteps
    private var command = ""
    private var slots: Map<String, String> = emptyMap()
    private var index = 0
    private var running = false
    private var consecutiveFallback = 0
    private var totalFallback = 0

    fun replay(skill: SkillWithSteps, command: String, slots: Map<String, String>) {
        if (running) return
        this.skill = skill
        this.command = command
        this.slots = slots
        index = 0
        consecutiveFallback = 0
        totalFallback = 0
        running = true
        auditLog.record("REPLAY skill=${skill.skill.id} \"$command\"")

        // Guard: unless the skill navigates first, we must already be in its app.
        val first = skill.steps.firstOrNull()
        if (first != null && first.actionType != "open_app") {
            val current = AgentAccessibilityService.instance?.currentPackage()
            if (current != skill.skill.targetApp) {
                abort()
                return
            }
        }
        runNext()
    }

    fun stop() {
        running = false
        main.removeCallbacksAndMessages(null)
    }

    private fun runNext() {
        if (!running) return
        if (index >= skill.steps.size) {
            finishSuccess()
            return
        }
        val step = skill.steps[index]
        if (step.irreversible) {
            host.requestConfirmation(describe(step)) { confirmed ->
                if (!running) return@requestConfirmation
                if (confirmed) executeStep(step) else abort()
            }
        } else {
            executeStep(step)
        }
    }

    private fun executeStep(step: SkillStep) {
        if (perform(step)) {
            onStepDone()
            return
        }
        // Deviation. Moderate: dismiss an unexpected dialog and retry once.
        if (AgentAccessibilityService.instance?.dismissTransientDialog() == true) {
            main.postDelayed({ if (running) retryStep(step) }, SETTLE_DELAY_MS)
            return
        }
        fallbackStep(step)
    }

    private fun retryStep(step: SkillStep) {
        if (!running) return
        if (perform(step)) onStepDone() else fallbackStep(step)
    }

    /** Capped single-step LLM fallback to get past a step whose element moved. */
    private fun fallbackStep(step: SkillStep) {
        if (consecutiveFallback >= MAX_CONSECUTIVE_FALLBACK || totalFallback >= MAX_TOTAL_FALLBACK) {
            abort()
            return
        }
        val service = AgentAccessibilityService.instance ?: return abort()
        host.onInfo(context.getString(R.string.replay_fallback))
        val snapshot = service.captureScreen()
        brain.plan(command, snapshot.compactText, object : Brain.Callback {
            override fun onAction(action: AgentAction, raw: String) {
                if (!running) return
                consecutiveFallback++
                totalFallback++
                if (SafetyPolicy.isIrreversible(action)) {
                    host.requestConfirmation(action.action + ": " + action.target) { confirmed ->
                        if (!running) return@requestConfirmation
                        if (confirmed) applyFallback(step, action) else abort()
                    }
                } else {
                    applyFallback(step, action)
                }
            }

            override fun onError(message: String) {
                abort()
            }
        })
    }

    private fun applyFallback(step: SkillStep, action: AgentAction) {
        if (!performBrainAction(action)) {
            abort()
            return
        }
        // The nudge may have cleared the obstacle; re-attempt the original step.
        main.postDelayed({ if (running) retryStep(step) }, SETTLE_DELAY_MS)
    }

    private fun perform(step: SkillStep): Boolean {
        val argument = resolveArgument(step)
        return when (step.actionType) {
            "open_app" -> AppLauncher.openApp(context, argument)
            "search" -> {
                AppLauncher.googleSearch(context, argument)
                true
            }
            "tap", "type", "scroll" -> {
                val locator = TrajectoryCodec.decodeLocator(step.locatorJson)
                AgentAccessibilityService.instance?.actOnLocator(locator, step.actionType, argument) ?: false
            }
            else -> false
        }
    }

    private fun performBrainAction(action: AgentAction): Boolean {
        val service = AgentAccessibilityService.instance ?: return false
        return when (action.action) {
            "open_app" -> AppLauncher.openApp(context, action.target)
            "search" -> {
                AppLauncher.googleSearch(context, action.text.ifBlank { action.target })
                true
            }
            "tap" -> service.clickByText(action.target) != null
            "type" -> service.typeText(action.text.ifBlank { action.target }) != null
            "scroll" -> service.scroll(forward = action.target != "up" && action.target != "backward") != null
            "done" -> true
            else -> false
        }
    }

    private fun onStepDone() {
        consecutiveFallback = 0
        index++
        main.postDelayed({ runNext() }, SETTLE_DELAY_MS)
    }

    private fun finishSuccess() {
        running = false
        main.removeCallbacksAndMessages(null)
        skillStore.recordReplay(skill.skill.id, success = true)
        skillStore.recordEpisode(command, "replay_ok")
        auditLog.record("REPLAY_OK skill=${skill.skill.id}")
        host.onFinished(context.getString(R.string.replay_done))
    }

    private fun abort() {
        if (!running) return
        running = false
        main.removeCallbacksAndMessages(null)
        skillStore.recordReplay(skill.skill.id, success = false)
        skillStore.recordEpisode(command, "replay_abort")
        auditLog.record("REPLAY_ABORT skill=${skill.skill.id} step=$index")
        host.onInfo(context.getString(R.string.replay_aborted))
        host.onFallback(command)
    }

    private fun resolveArgument(step: SkillStep): String =
        if (step.slotName.isNotBlank()) slots[step.slotName] ?: step.argument else step.argument

    private fun describe(step: SkillStep): String {
        val detail = resolveArgument(step)
        return if (detail.isBlank()) step.actionType else "${step.actionType}: $detail"
    }

    private companion object {
        const val SETTLE_DELAY_MS = 700L
        const val MAX_CONSECUTIVE_FALLBACK = 2
        const val MAX_TOTAL_FALLBACK = 5
    }
}
