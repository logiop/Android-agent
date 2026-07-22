package com.logiop.androidagent.memory

/** Reliability heuristics over a skill's replay counters. */
object SkillStats {

    private const val MIN_RUNS = 3
    private const val FAILURE_THRESHOLD = 0.5

    /**
     * A skill should be flagged for recompilation when it has run enough times
     * and fails at least half the time (the UI likely changed).
     */
    fun needsRecompile(successCount: Int, failCount: Int): Boolean {
        val total = successCount + failCount
        if (total < MIN_RUNS) return false
        return failCount.toDouble() / total >= FAILURE_THRESHOLD
    }
}
