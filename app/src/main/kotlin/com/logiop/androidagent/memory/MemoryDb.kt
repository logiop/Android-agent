package com.logiop.androidagent.memory

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * SQLite schema for the agent's procedural (skills) and episodic memory.
 * Plain [SQLiteOpenHelper] — no Room/KSP dependency.
 */
class MemoryDb(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE skill (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                intent_pattern TEXT NOT NULL,
                target_app TEXT NOT NULL,
                created_by TEXT NOT NULL,
                trust_state TEXT NOT NULL,
                success_count INTEGER NOT NULL DEFAULT 0,
                fail_count INTEGER NOT NULL DEFAULT 0,
                version INTEGER NOT NULL DEFAULT 1,
                created_at TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE skill_step (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                skill_id INTEGER NOT NULL,
                idx INTEGER NOT NULL,
                action_type TEXT NOT NULL,
                locator_json TEXT NOT NULL,
                state_descriptor_json TEXT NOT NULL,
                param_slots_json TEXT NOT NULL,
                irreversible INTEGER NOT NULL DEFAULT 0,
                argument TEXT NOT NULL DEFAULT '',
                slot_name TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE pending_skill (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                command TEXT NOT NULL,
                trajectory_json TEXT NOT NULL,
                created_at TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE episode (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ts TEXT NOT NULL,
                command TEXT NOT NULL,
                outcome TEXT NOT NULL
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE skill_step ADD COLUMN argument TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE skill_step ADD COLUMN slot_name TEXT NOT NULL DEFAULT ''")
        }
    }

    private companion object {
        const val NAME = "agent_memory.db"
        const val VERSION = 2
    }
}
