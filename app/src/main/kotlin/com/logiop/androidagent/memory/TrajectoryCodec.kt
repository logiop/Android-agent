package com.logiop.androidagent.memory

import org.json.JSONArray
import org.json.JSONObject

/** JSON (de)serialization for trajectories and locators. */
object TrajectoryCodec {

    fun encodeLocator(locator: ElementLocator): String = JSONObject().apply {
        put("resourceId", locator.resourceId)
        put("text", locator.text)
        put("contentDesc", locator.contentDesc)
        put("className", locator.className)
        put("parentText", locator.parentText)
        put("siblingText", locator.siblingText)
    }.toString()

    fun decodeLocator(json: String): ElementLocator {
        val obj = JSONObject(json)
        return ElementLocator(
            resourceId = obj.optString("resourceId"),
            text = obj.optString("text"),
            contentDesc = obj.optString("contentDesc"),
            className = obj.optString("className"),
            parentText = obj.optString("parentText"),
            siblingText = obj.optString("siblingText"),
        )
    }

    fun encodeTrajectory(trajectory: Trajectory): String = JSONObject().apply {
        put("command", trajectory.command)
        put("targetApp", trajectory.targetApp)
        put(
            "steps",
            JSONArray().apply {
                trajectory.steps.forEach { step ->
                    put(
                        JSONObject().apply {
                            put("actionType", step.actionType)
                            put("argument", step.argument)
                            put("locator", JSONObject(encodeLocator(step.locator)))
                            put("stateFingerprint", step.stateFingerprint)
                            put("irreversible", step.irreversible)
                        },
                    )
                }
            },
        )
    }.toString()

    fun decodeTrajectory(json: String): Trajectory {
        val obj = JSONObject(json)
        val stepsArray = obj.optJSONArray("steps") ?: JSONArray()
        val steps = (0 until stepsArray.length()).map { i ->
            val s = stepsArray.getJSONObject(i)
            TrajectoryStep(
                actionType = s.optString("actionType"),
                argument = s.optString("argument"),
                locator = decodeLocator(s.getJSONObject("locator").toString()),
                stateFingerprint = s.optString("stateFingerprint"),
                irreversible = s.optBoolean("irreversible"),
            )
        }
        return Trajectory(
            command = obj.optString("command"),
            targetApp = obj.optString("targetApp"),
            steps = steps,
        )
    }
}
