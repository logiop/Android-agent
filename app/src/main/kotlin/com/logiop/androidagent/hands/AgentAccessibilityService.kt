package com.logiop.androidagent.hands

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * The agent's "hands": reads the current screen and performs actions
 * (tap / type / scroll) on behalf of the agent.
 *
 * Exposes a process-wide [instance] once the user has enabled the service in
 * Accessibility settings, so the overlay can drive it. The higher-level agent
 * loop (LLM planning) is added in a later step; for now these primitives are
 * called directly by deterministic commands.
 */
class AgentAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Reactive handling (progress detection, etc.) comes with the agent loop.
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** Reads the interactive elements currently visible on screen. */
    fun captureScreen(): UiSnapshot {
        val root = rootInActiveWindow ?: return UiSnapshot(emptyList(), "")
        return ScreenReader.capture(root)
    }

    /** Package name of the app currently in the foreground, if known. */
    fun currentPackage(): String? = rootInActiveWindow?.packageName?.toString()

    /** Clicks the first clickable node whose text/description matches [query]. */
    fun clickByText(query: String): Boolean {
        val root = rootInActiveWindow ?: return false
        for (node in root.findAccessibilityNodeInfosByText(query)) {
            clickableAncestor(node)?.let {
                return it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }
        return false
    }

    /** Types [text] into the focused (or first) editable field. */
    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val field = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: firstMatching(root) { it.isEditable }
            ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /** Scrolls the first scrollable container forward or backward. */
    fun scroll(forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val scrollable = firstMatching(root) { it.isScrollable } ?: return false
        val action = if (forward) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        return scrollable.performAction(action)
    }

    private fun clickableAncestor(start: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var node = start
        while (node != null) {
            if (node.isClickable) return node
            node = node.parent
        }
        return null
    }

    private fun firstMatching(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (predicate(node)) return node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    companion object {
        @Volatile
        var instance: AgentAccessibilityService? = null
            private set

        /** Whether the user has enabled this service in Accessibility settings. */
        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, AgentAccessibilityService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any {
                ComponentName.unflattenFromString(it) == expected
            }
        }
    }
}
