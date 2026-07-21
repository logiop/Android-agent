package com.logiop.androidagent.agent

import com.logiop.androidagent.brain.AgentAction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyPolicyTest {

    @Test
    fun sendTap_isIrreversible() {
        assertTrue(SafetyPolicy.isIrreversible(AgentAction("tap", "Invia")))
        assertTrue(SafetyPolicy.isIrreversible(AgentAction("tap", "Elimina messaggio")))
        assertTrue(SafetyPolicy.isIrreversible(AgentAction("tap", "Paga ora")))
    }

    @Test
    fun ordinaryTap_isNotIrreversible() {
        assertFalse(SafetyPolicy.isIrreversible(AgentAction("tap", "Cerca")))
        assertFalse(SafetyPolicy.isIrreversible(AgentAction("tap", "Impostazioni")))
    }

    @Test
    fun nonTapActions_areNeverIrreversible() {
        assertFalse(SafetyPolicy.isIrreversible(AgentAction("type", "invia questo testo")))
        assertFalse(SafetyPolicy.isIrreversible(AgentAction("scroll", "invia")))
        assertFalse(SafetyPolicy.isIrreversible(AgentAction("open_app", "invia")))
    }
}
