package com.logiop.androidagent.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActionParserTest {

    @Test
    fun parsesPlainJson() {
        assertEquals(
            AgentAction("open_app", "Chrome", ""),
            ActionParser.parse("""{"action":"open_app","target":"Chrome","text":""}"""),
        )
    }

    @Test
    fun extractsJsonFromSurroundingText() {
        val raw = "Sure! ```json {\"action\":\"TAP\",\"target\":\"Cerca\"} ``` done"
        assertEquals(AgentAction("tap", "Cerca", ""), ActionParser.parse(raw))
    }

    @Test
    fun returnsNullWhenNoJson() {
        assertNull(ActionParser.parse("no json here"))
    }

    @Test
    fun returnsNullWhenActionMissing() {
        assertNull(ActionParser.parse("""{"target":"x"}"""))
    }
}
