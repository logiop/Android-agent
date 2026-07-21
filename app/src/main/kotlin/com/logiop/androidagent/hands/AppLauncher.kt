package com.logiop.androidagent.hands

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Deterministic shortcuts that do not go through the LLM: launching an app by
 * name and running a Google search. Both resolve installed apps via the
 * launcher `<queries>` declared in the manifest.
 */
object AppLauncher {

    /** Opens the installed app whose launcher label best matches [query]. */
    fun openApp(context: Context, query: String): Boolean {
        val pkg = resolvePackage(context, query) ?: return false
        val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
        return true
    }

    /** Opens a Google search for [query] in the default browser. */
    fun googleSearch(context: Context, query: String) {
        val url = "https://www.google.com/search?q=" + Uri.encode(query)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun resolvePackage(context: Context, query: String): String? {
        val pm = context.packageManager
        val target = query.trim().lowercase()
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        var partial: String? = null
        for (info in pm.queryIntentActivities(main, 0)) {
            val label = info.loadLabel(pm).toString().trim().lowercase()
            val pkg = info.activityInfo.packageName
            if (label == target) return pkg
            if (partial == null && (label.contains(target) || target.contains(label))) {
                partial = pkg
            }
        }
        return partial
    }
}
