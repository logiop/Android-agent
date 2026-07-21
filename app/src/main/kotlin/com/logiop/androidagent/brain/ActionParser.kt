package com.logiop.androidagent.brain

import org.json.JSONException
import org.json.JSONObject

/**
 * Parses the model output into an [AgentAction], tolerating surrounding prose or
 * markdown fences by extracting the first JSON object it can find.
 */
object ActionParser {

    fun parse(raw: String): AgentAction? {
        val json = extractJsonObject(raw) ?: return null
        return try {
            val obj = JSONObject(json)
            val action = obj.optString("action").trim().lowercase()
            if (action.isEmpty()) {
                null
            } else {
                AgentAction(
                    action = action,
                    target = obj.optString("target").trim(),
                    text = obj.optString("text").trim(),
                )
            }
        } catch (e: JSONException) {
            null
        }
    }

    private fun extractJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        return if (start in 0 until end) raw.substring(start, end + 1) else null
    }
}
