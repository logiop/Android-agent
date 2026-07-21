package com.logiop.androidagent.memory

import android.content.ContentValues
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Repository over [MemoryDb] for pending runs and learned skills.
 * All access is synchronous; callers should invoke off the main thread for
 * larger reads (the skills list).
 */
class SkillStore(context: Context) {

    private val db = MemoryDb(context.applicationContext)
    private val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

    // --- Pending runs awaiting human confirmation ---

    fun savePending(trajectory: Trajectory): Long {
        val values = ContentValues().apply {
            put("command", trajectory.command)
            put("trajectory_json", TrajectoryCodec.encodeTrajectory(trajectory))
            put("created_at", timestamp.format(Date()))
        }
        return db.writableDatabase.insert("pending_skill", null, values)
    }

    fun loadPending(id: Long): PendingSkill? {
        db.readableDatabase.query(
            "pending_skill", arrayOf("command", "trajectory_json"),
            "id = ?", arrayOf(id.toString()), null, null, null,
        ).use { c ->
            if (!c.moveToFirst()) return null
            val command = c.getString(0)
            val trajectory = TrajectoryCodec.decodeTrajectory(c.getString(1))
            return PendingSkill(id, command, trajectory)
        }
    }

    fun deletePending(id: Long) {
        db.writableDatabase.delete("pending_skill", "id = ?", arrayOf(id.toString()))
    }

    // --- Learned skills ---

    fun compileAndSave(draft: DraftSkill, createdBy: String = "user"): Long {
        val write = db.writableDatabase
        write.beginTransaction()
        try {
            val skillValues = ContentValues().apply {
                put("intent_pattern", draft.intentPattern)
                put("target_app", draft.targetApp)
                put("created_by", createdBy)
                put("trust_state", TrustState.QUARANTINE.name)
                put("success_count", 0)
                put("fail_count", 0)
                put("version", 1)
                put("created_at", timestamp.format(Date()))
            }
            val skillId = write.insert("skill", null, skillValues)
            draft.steps.forEach { step ->
                val stepValues = ContentValues().apply {
                    put("skill_id", skillId)
                    put("idx", step.idx)
                    put("action_type", step.actionType)
                    put("locator_json", step.locatorJson)
                    put("state_descriptor_json", step.stateDescriptorJson)
                    put("param_slots_json", step.paramSlotsJson)
                    put("irreversible", if (step.irreversible) 1 else 0)
                }
                write.insert("skill_step", null, stepValues)
            }
            write.setTransactionSuccessful()
            return skillId
        } finally {
            write.endTransaction()
        }
    }

    fun listSkills(): List<Skill> {
        val result = mutableListOf<Skill>()
        db.readableDatabase.query(
            "skill",
            arrayOf(
                "id", "intent_pattern", "target_app", "created_by", "trust_state",
                "success_count", "fail_count", "version", "created_at",
            ),
            null, null, null, null, "created_at DESC",
        ).use { c ->
            while (c.moveToNext()) {
                result += Skill(
                    id = c.getLong(0),
                    intentPattern = c.getString(1),
                    targetApp = c.getString(2),
                    createdBy = c.getString(3),
                    trustState = runCatching { TrustState.valueOf(c.getString(4)) }
                        .getOrDefault(TrustState.QUARANTINE),
                    successCount = c.getInt(5),
                    failCount = c.getInt(6),
                    version = c.getInt(7),
                    createdAt = c.getString(8),
                )
            }
        }
        return result
    }

    fun deleteSkill(id: Long) {
        val write = db.writableDatabase
        write.delete("skill_step", "skill_id = ?", arrayOf(id.toString()))
        write.delete("skill", "id = ?", arrayOf(id.toString()))
    }
}
