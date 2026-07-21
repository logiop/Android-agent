package com.logiop.androidagent.agent

import android.content.Context

/**
 * The explicit set of app packages the agent is allowed to control. Everything
 * not listed is blocked by default (the plan's non-negotiable safety rule).
 *
 * Backed by SharedPreferences so choices persist across restarts. The default
 * is empty: the agent can navigate (open apps, search) but cannot tap/type/
 * scroll inside any app until the user explicitly whitelists it.
 */
class Whitelist(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isAllowed(packageName: String): Boolean = packages().contains(packageName)

    fun packages(): Set<String> = prefs.getStringSet(KEY, emptySet()).orEmpty().toSet()

    fun add(packageName: String) {
        prefs.edit().putStringSet(KEY, packages() + packageName).apply()
    }

    fun remove(packageName: String) {
        prefs.edit().putStringSet(KEY, packages() - packageName).apply()
    }

    private companion object {
        const val PREFS = "agent_whitelist"
        const val KEY = "allowed_packages"
    }
}
