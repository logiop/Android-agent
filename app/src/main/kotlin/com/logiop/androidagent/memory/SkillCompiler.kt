package com.logiop.androidagent.memory

/**
 * Compiles a [Trajectory] into a [DraftSkill] with deterministic slot
 * extraction: the concrete values the user demonstrated (typed text, tapped
 * labels) that appear in the command become typed slots. This is the
 * programming-by-demonstration principle and needs no LLM — the demonstrated
 * values *are* the parameters. The user confirms/corrects the result afterwards.
 */
object SkillCompiler {

    private val PHONE = Regex("""\+?\d[\d\s]{5,}\d""")
    private val URL = Regex("""(https?://|www\.)\S+""", RegexOption.IGNORE_CASE)
    private val TIME = Regex("""\b\d{1,2}[:.]\d{2}\b""")

    /** Builds a draft skill proposal from a run. */
    fun autoDraft(trajectory: Trajectory): DraftSkill {
        // Only typed text is a real parameter; tap labels are UI affordances
        // (and often coincide with words in the command, e.g. the verb "cerca").
        val values = trajectory.steps
            .filter { it.actionType == "type" }
            .map { it.argument.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val result = buildPatternInternal(trajectory.command, values)

        val steps = trajectory.steps.mapIndexed { idx, step ->
            val slotName = if (step.actionType == "type") {
                result.valueToSlot.entries
                    .firstOrNull { it.key.equals(step.argument.trim(), ignoreCase = true) }
                    ?.value.orEmpty()
            } else {
                ""
            }
            SkillStep(
                idx = idx,
                actionType = step.actionType,
                locatorJson = TrajectoryCodec.encodeLocator(step.locator),
                stateDescriptorJson = step.stateFingerprint,
                paramSlotsJson = "{}",
                irreversible = step.irreversible,
                argument = step.argument,
                slotName = slotName,
            )
        }

        return DraftSkill(
            command = trajectory.command,
            intentPattern = result.pattern,
            targetApp = trajectory.targetApp,
            slots = result.slots,
            steps = steps,
        )
    }

    /**
     * Replaces each demonstrated [values] occurrence in [command] with a
     * `{slotN}` placeholder, returning the templated pattern and the typed slots.
     * Values not present in the command are skipped (can't be templatized).
     */
    fun buildPattern(command: String, values: List<String>): Pair<String, List<Slot>> {
        val result = buildPatternInternal(command, values)
        return result.pattern to result.slots
    }

    private data class PatternResult(
        val pattern: String,
        val slots: List<Slot>,
        val valueToSlot: Map<String, String>,
    )

    private fun buildPatternInternal(command: String, values: List<String>): PatternResult {
        var pattern = command
        val slots = mutableListOf<Slot>()
        val valueToSlot = LinkedHashMap<String, String>()
        var index = 1
        for (value in values) {
            if (value.isBlank()) continue
            val at = pattern.indexOf(value, ignoreCase = true)
            if (at < 0) continue
            val name = "slot$index"
            pattern = pattern.substring(0, at) + "{$name}" + pattern.substring(at + value.length)
            slots += Slot(name, inferType(value))
            valueToSlot[value] = name
            index++
        }
        return PatternResult(pattern, slots, valueToSlot)
    }

    fun inferType(value: String): SlotType = when {
        URL.containsMatchIn(value) -> SlotType.URL
        PHONE.matches(value.trim()) -> SlotType.PHONE
        TIME.containsMatchIn(value) -> SlotType.TIME
        else -> SlotType.TEXT
    }

    /** Extracts `{slot}` placeholder names from a (possibly user-edited) pattern. */
    fun slotsFromPattern(pattern: String): List<String> =
        Regex("""\{([A-Za-z0-9_]+)\}""").findAll(pattern).map { it.groupValues[1] }.distinct().toList()
}
