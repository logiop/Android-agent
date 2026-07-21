package com.logiop.androidagent.agent

import com.logiop.androidagent.brain.AgentAction

/**
 * Decides which actions are irreversible and therefore require explicit manual
 * confirmation before running: sending messages/email, posting, deleting data,
 * and payments (per the plan's security section).
 *
 * The action type alone is not enough — a tap is only dangerous depending on
 * what it commits — so this matches the target/text against a keyword list.
 */
object SafetyPolicy {

    private val IRREVERSIBLE_KEYWORDS = listOf(
        "invia", "inviare", "manda", "spedisci", "send",
        "pubblica", "posta", "post", "condividi", "share", "tweet",
        "elimina", "cancella", "delete", "rimuovi", "remove",
        "paga", "pagamento", "pay", "acquista", "buy", "ordina", "order",
        "conferma ordine", "checkout", "trasferisci", "bonifico",
    )

    // Whole-word matching so e.g. "posta" does not fire inside "impostazioni".
    private val patterns = IRREVERSIBLE_KEYWORDS.map { keyword ->
        Regex("""\b${Regex.escape(keyword)}\b""", RegexOption.IGNORE_CASE)
    }

    fun isIrreversible(action: AgentAction): Boolean {
        // Only a commit tap is treated as irreversible; typing/scrolling are not.
        if (action.action != "tap") return false
        val haystack = "${action.target} ${action.text}"
        return patterns.any { it.containsMatchIn(haystack) }
    }
}
