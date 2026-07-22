package com.logiop.androidagent.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocatorMatcherTest {

    private val full = ElementLocator(
        resourceId = "com.app:id/send",
        text = "Invia",
        contentDesc = "Invia messaggio",
        className = "android.widget.Button",
        parentText = "Barra",
        siblingText = "Allega",
    )

    @Test
    fun identical_scoresOne() {
        assertEquals(1.0, LocatorMatcher.score(full, full), 1e-9)
    }

    @Test
    fun resourceIdChanged_otherFeaturesHold_staysAboveThreshold() {
        val candidate = full.copy(resourceId = "com.app:id/send_v2")
        val score = LocatorMatcher.score(full, candidate)
        assertEquals(0.60, score, 1e-9)
        assertTrue(score >= LocatorMatcher.THRESHOLD)
    }

    @Test
    fun onlyTextMatches_belowThreshold() {
        val candidate = ElementLocator(text = "Invia")
        val score = LocatorMatcher.score(full, candidate)
        assertEquals(0.20, score, 1e-9)
        assertTrue(score < LocatorMatcher.THRESHOLD)
    }
}
