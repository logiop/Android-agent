package com.logiop.androidagent.memory

import android.content.ContentValues
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide store for the agent chat transcript, persisted in [MemoryDb].
 *
 * A singleton so the overlay service (writer) and the chat screen (reader) share
 * the same in-memory [flow] without any IPC — they run in the same process.
 */
object ChatStore {

    private var db: MemoryDb? = null
    private val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

    private val _flow = MutableStateFlow<List<ChatMessage>>(emptyList())
    val flow: StateFlow<List<ChatMessage>> = _flow.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (db == null) {
            db = MemoryDb(context.applicationContext)
            _flow.value = readAll()
        }
    }

    @Synchronized
    fun append(role: ChatRole, text: String) {
        val database = db ?: return
        val values = ContentValues().apply {
            put("ts", timestamp.format(Date()))
            put("role", role.name)
            put("text", text)
        }
        database.writableDatabase.insert("chat_message", null, values)
        _flow.value = readAll()
    }

    private fun readAll(): List<ChatMessage> {
        val database = db ?: return emptyList()
        val result = mutableListOf<ChatMessage>()
        database.readableDatabase.query(
            "chat_message",
            arrayOf("id", "ts", "role", "text"),
            null, null, null, null, "id ASC",
        ).use { c ->
            while (c.moveToNext()) {
                result += ChatMessage(
                    id = c.getLong(0),
                    ts = c.getString(1),
                    role = runCatching { ChatRole.valueOf(c.getString(2)) }
                        .getOrDefault(ChatRole.AGENT),
                    text = c.getString(3),
                )
            }
        }
        return result
    }
}
