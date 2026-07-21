package com.logiop.androidagent.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillCompilerTest {

    @Test
    fun buildPattern_templatizesDemonstratedValue() {
        val (pattern, slots) = SkillCompiler.buildPattern("cerca meteo genova", listOf("meteo genova"))
        assertEquals("cerca {slot1}", pattern)
        assertEquals(listOf(Slot("slot1", SlotType.TEXT)), slots)
    }

    @Test
    fun buildPattern_skipsValuesNotInCommand() {
        val (pattern, slots) = SkillCompiler.buildPattern("apri le impostazioni", listOf("wifi"))
        assertEquals("apri le impostazioni", pattern)
        assertTrue(slots.isEmpty())
    }

    @Test
    fun inferType_detectsUrlPhoneTime() {
        assertEquals(SlotType.URL, SkillCompiler.inferType("https://example.com"))
        assertEquals(SlotType.PHONE, SkillCompiler.inferType("+39 333 1234567"))
        assertEquals(SlotType.TIME, SkillCompiler.inferType("18:30"))
        assertEquals(SlotType.TEXT, SkillCompiler.inferType("meteo genova"))
    }

    @Test
    fun slotsFromPattern_extractsPlaceholders() {
        assertEquals(
            listOf("query", "app"),
            SkillCompiler.slotsFromPattern("cerca {query} nell'app {app}"),
        )
    }

    @Test
    fun autoDraft_buildsStepsAndPattern() {
        val trajectory = Trajectory(
            command = "cerca meteo genova",
            targetApp = "com.android.chrome",
            steps = listOf(
                TrajectoryStep("type", "meteo genova", ElementLocator(resourceId = "search"), "com.android.chrome|search", false),
                TrajectoryStep("tap", "Cerca", ElementLocator(text = "Cerca"), "com.android.chrome|results", false),
            ),
        )
        val draft = SkillCompiler.autoDraft(trajectory)
        assertEquals("cerca {slot1}", draft.intentPattern)
        assertEquals(2, draft.steps.size)
        assertEquals("com.android.chrome", draft.targetApp)
    }
}
