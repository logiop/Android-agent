package com.logiop.androidagent.hands

/** A voice command classified into a deterministic action or free-form text. */
sealed interface Command {
    data class OpenApp(val query: String) : Command
    data class GoogleSearch(val query: String) : Command

    /** Anything the deterministic rules could not handle; the LLM will plan it. */
    data class Freeform(val text: String) : Command
}

/**
 * Classifies an Italian voice command. Deterministic shortcuts ("apri …",
 * "cerca … su google") are matched here so they never need the model; every
 * other command falls through to [Command.Freeform].
 */
object CommandInterpreter {

    private val searchOnGoogle = Regex("""^(?:cerca|ricerca)\s+(.+?)\s+su\s+google$""")
    private val googlePrefix = Regex("""^(?:google|cerca su google)\s+(.+)$""")
    private val genericSearch = Regex("""^(?:cerca|ricerca)\s+(.+)$""")
    private val openApp = Regex("""^(?:apri|avvia|lancia)\s+(?:l['’ ]?app\s+|il\s+|la\s+|lo\s+)?(.+)$""")

    fun interpret(raw: String): Command {
        val text = raw.trim().lowercase()

        searchOnGoogle.find(text)?.let { return Command.GoogleSearch(it.groupValues[1].trim()) }
        googlePrefix.find(text)?.let { return Command.GoogleSearch(it.groupValues[1].trim()) }
        genericSearch.find(text)?.let { return Command.GoogleSearch(it.groupValues[1].trim()) }
        openApp.find(text)?.let { return Command.OpenApp(it.groupValues[1].trim()) }

        return Command.Freeform(raw.trim())
    }
}
