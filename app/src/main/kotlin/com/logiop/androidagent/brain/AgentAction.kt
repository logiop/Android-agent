package com.logiop.androidagent.brain

/**
 * The single next action decided by the LLM, matching the strict JSON contract
 * `{action, target, text}` from the project plan.
 *
 * - [action]: one of tap / type / scroll / open_app / search / done.
 * - [target]: element label or app name the action applies to.
 * - [text]: text to type or search; empty otherwise.
 */
data class AgentAction(
    val action: String,
    val target: String = "",
    val text: String = "",
)
