package com.logiop.androidagent.agent

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.logiop.androidagent.R
import com.logiop.androidagent.hands.AgentAccessibilityService
import com.logiop.androidagent.hands.AppLauncher
import com.logiop.androidagent.memory.SkillStep
import com.logiop.androidagent.memory.SkillStore
import com.logiop.androidagent.memory.SkillWithSteps
import com.logiop.androidagent.memory.TrajectoryCodec
import com.logiop.androidagent.security.AuditLog

/**
 * Replays a learned skill step-by-step without the LLM. MVP robustness: a guard
 * condition before starting, weighted-locator element finding, strict
 * verification (any failed step aborts), irreversible steps still confirmed by
 * the user, and a fallback to the LLM loop on any deviation.
 */
class SkillReplayer(
    private val context: Context,
    private val skillStore: SkillStore,
    private val auditLog: AuditLog,
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

    fun replay(skill: SkillWithSteps, command: String, slots: Map<String, String>) {
        if (running) return
        this.skill = skill
        this.command = command
        this.slots = slots
        index = 0
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
        if (!perform(step)) {
            abort()
            return
        }
        index++
        main.postDelayed({ runNext() }, SETTLE_DELAY_MS)
    }

    private fun perform(step: SkillStep): Boolean {
        val argument = if (step.slotName.isNotBlank()) {
            slots[step.slotName] ?: step.argument
        } else {
            step.argument
        }
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

    private fun finishSuccess() {
        running = false
        main.removeCallbacksAndMessages(null)
        skillStore.recordReplay(skill.skill.id, success = true)
        auditLog.record("REPLAY_OK skill=${skill.skill.id}")
        host.onFinished(context.getString(R.string.replay_done))
    }

    private fun abort() {
        if (!running) return
        running = false
        main.removeCallbacksAndMessages(null)
        skillStore.recordReplay(skill.skill.id, success = false)
        auditLog.record("REPLAY_ABORT skill=${skill.skill.id} step=$index")
        host.onInfo(context.getString(R.string.replay_aborted))
        host.onFallback(command)
    }

    private fun describe(step: SkillStep): String {
        val detail = if (step.slotName.isNotBlank()) slots[step.slotName] ?: step.argument else step.argument
        return if (detail.isBlank()) step.actionType else "${step.actionType}: $detail"
    }

    private companion object {
        const val SETTLE_DELAY_MS = 700L
    }
}
