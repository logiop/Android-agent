package com.logiop.androidagent.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StateVerifierTest {

    @Test
    fun changedState_withControlSteps_isLearnable() {
        assertTrue(StateVerifier.isLearnable("com.app|a", "com.app|b", controlSteps = 2))
    }

    @Test
    fun unchangedState_isNotLearnable() {
        assertFalse(StateVerifier.isLearnable("com.app|a", "com.app|a", controlSteps = 3))
    }

    @Test
    fun noControlSteps_isNotLearnable() {
        assertFalse(StateVerifier.isLearnable("com.app|a", "com.app|b", controlSteps = 0))
    }
}
