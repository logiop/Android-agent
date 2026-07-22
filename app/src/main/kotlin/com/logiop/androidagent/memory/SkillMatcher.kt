package com.logiop.androidagent.memory

/**
 * Matches a spoken command against a skill's intent pattern (regex stage of the
 * cascade). `{slot}` placeholders become capture groups; a match returns the
 * slot bindings. Semantic (embedding) matching is a later increment.
 */
object SkillMatcher {

    private val PLACEHOLDER = Regex("""\{([A-Za-z0-9_]+)\}""")

    fun slotNames(intentPattern: String): List<String> =
        PLACEHOLDER.findAll(intentPattern).map { it.groupValues[1] }.toList()

    fun patternToRegex(intentPattern: String): Regex {
        val sb = StringBuilder("^")
        var last = 0
        for (m in PLACEHOLDER.findAll(intentPattern)) {
            sb.append(Regex.escape(intentPattern.substring(last, m.range.first)))
            sb.append("(.+?)")
            last = m.range.last + 1
        }
        sb.append(Regex.escape(intentPattern.substring(last)))
        sb.append("$")
        return Regex(sb.toString(), RegexOption.IGNORE_CASE)
    }

    /** Returns slot→value bindings if [command] matches [intentPattern], else null. */
    fun match(command: String, intentPattern: String): Map<String, String>? {
        val names = slotNames(intentPattern)
        val match = patternToRegex(intentPattern).matchEntire(command.trim()) ?: return null
        return names.mapIndexed { i, name -> name to match.groupValues[i + 1].trim() }.toMap()
    }
}
