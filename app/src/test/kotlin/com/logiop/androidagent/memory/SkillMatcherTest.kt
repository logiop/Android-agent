package com.logiop.androidagent.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SkillMatcherTest {

    @Test
    fun match_extractsSingleSlot() {
        assertEquals(
            mapOf("slot1" to "meteo genova"),
            SkillMatcher.match("cerca meteo genova", "cerca {slot1}"),
        )
    }

    @Test
    fun match_isCaseInsensitive() {
        assertEquals(
            mapOf("slot1" to "Meteo Genova"),
            SkillMatcher.match("Cerca Meteo Genova", "cerca {slot1}"),
        )
    }

    @Test
    fun match_extractsMultipleSlots() {
        assertEquals(
            mapOf("contatto" to "Luca", "testo" to "ciao"),
            SkillMatcher.match("invia a Luca dicendo ciao", "invia a {contatto} dicendo {testo}"),
        )
    }

    @Test
    fun match_returnsNullWhenPatternDiffers() {
        assertNull(SkillMatcher.match("apri chrome", "cerca {slot1}"))
    }

    @Test
    fun match_literalPatternRequiresExactCommand() {
        assertEquals(emptyMap<String, String>(), SkillMatcher.match("vai indietro", "vai indietro"))
        assertNull(SkillMatcher.match("vai avanti", "vai indietro"))
    }
}
