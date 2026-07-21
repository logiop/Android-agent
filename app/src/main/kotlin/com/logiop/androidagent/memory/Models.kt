package com.logiop.androidagent.memory

/** Typed slot extracted from a demonstrated command (the variable part). */
enum class SlotType { TEXT, TIME, PHONE, URL, CONTACT }

/** Probation state of a learned skill. New skills start in QUARANTINE. */
enum class TrustState { QUARANTINE, TRUSTED }

data class Slot(val name: String, val type: SlotType)

/**
 * Multi-feature locator of an element the agent acted on, so a future replay can
 * re-find it even when one attribute changes. Weights (resourceId 0.40 … sibling
 * 0.05) are applied at match time in Phase 3.2; here we just capture the features.
 */
data class ElementLocator(
    val resourceId: String = "",
    val text: String = "",
    val contentDesc: String = "",
    val className: String = "",
    val parentText: String = "",
    val siblingText: String = "",
)

/** Fingerprint of a screen state: the foreground package plus key element ids/text. */
data class UiStateDescriptor(
    val packageName: String,
    val keys: List<String>,
) {
    fun fingerprint(): String = packageName + "|" + keys.sorted().joinToString(",")
}

/** One executed control step recorded during a run, used to compile a skill. */
data class TrajectoryStep(
    val actionType: String,
    val argument: String,
    val locator: ElementLocator,
    val stateFingerprint: String,
    val irreversible: Boolean,
)

/** The full record of a successful run, the raw material for a skill. */
data class Trajectory(
    val command: String,
    val targetApp: String,
    val steps: List<TrajectoryStep>,
)

/** A persisted skill step. */
data class SkillStep(
    val idx: Int,
    val actionType: String,
    val locatorJson: String,
    val stateDescriptorJson: String,
    val paramSlotsJson: String,
    val irreversible: Boolean,
)

/** A persisted, parameterized skill. */
data class Skill(
    val id: Long,
    val intentPattern: String,
    val targetApp: String,
    val createdBy: String,
    val trustState: TrustState,
    val successCount: Int,
    val failCount: Int,
    val version: Int,
    val createdAt: String,
)

/** A skill proposed to the user for confirmation before it is saved. */
data class DraftSkill(
    val command: String,
    val intentPattern: String,
    val targetApp: String,
    val slots: List<Slot>,
    val steps: List<SkillStep>,
)

/** A run awaiting human confirmation, loaded by the review screen. */
data class PendingSkill(
    val id: Long,
    val command: String,
    val trajectory: Trajectory,
)
