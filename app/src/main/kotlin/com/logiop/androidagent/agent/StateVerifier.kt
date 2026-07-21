package com.logiop.androidagent.agent

/**
 * Guards against "task-success bias": the LLM declaring done without the screen
 * actually changing. A run is only worth learning from if at least one control
 * action ran and the end state differs from the start state.
 */
object StateVerifier {

    fun isLearnable(
        startFingerprint: String,
        endFingerprint: String,
        controlSteps: Int,
    ): Boolean = controlSteps > 0 && startFingerprint != endFingerprint
}
