package com.logiop.androidagent.memory

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillStatsTest {

    @Test
    fun belowMinimumRuns_neverFlags() {
        assertFalse(SkillStats.needsRecompile(successCount = 0, failCount = 2))
    }

    @Test
    fun highFailureRate_flags() {
        assertTrue(SkillStats.needsRecompile(successCount = 1, failCount = 3))
        assertTrue(SkillStats.needsRecompile(successCount = 2, failCount = 2))
    }

    @Test
    fun mostlySuccessful_doesNotFlag() {
        assertFalse(SkillStats.needsRecompile(successCount = 5, failCount = 1))
    }
}
