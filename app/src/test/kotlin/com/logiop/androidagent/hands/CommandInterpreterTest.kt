package com.logiop.androidagent.hands

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandInterpreterTest {

    @Test
    fun openApp_isRecognized() {
        assertEquals(Command.OpenApp("chrome"), CommandInterpreter.interpret("apri Chrome"))
        assertEquals(Command.OpenApp("whatsapp"), CommandInterpreter.interpret("avvia WhatsApp"))
        assertEquals(Command.OpenApp("telegram"), CommandInterpreter.interpret("apri l'app Telegram"))
    }

    @Test
    fun googleSearch_isRecognized() {
        assertEquals(
            Command.GoogleSearch("meteo genova"),
            CommandInterpreter.interpret("cerca meteo Genova su Google"),
        )
        assertEquals(
            Command.GoogleSearch("ricette pasta"),
            CommandInterpreter.interpret("google ricette pasta"),
        )
        assertEquals(
            Command.GoogleSearch("orari treni"),
            CommandInterpreter.interpret("cerca orari treni"),
        )
    }

    @Test
    fun searchOnGoogle_takesPrecedenceOverGenericSearch() {
        // "... su google" must strip the suffix, not keep it in the query.
        assertEquals(
            Command.GoogleSearch("meteo genova"),
            CommandInterpreter.interpret("cerca meteo genova su google"),
        )
    }

    @Test
    fun unknownCommand_fallsBackToFreeform() {
        assertEquals(
            Command.Freeform("manda un messaggio a Luca"),
            CommandInterpreter.interpret("manda un messaggio a Luca"),
        )
    }
}
