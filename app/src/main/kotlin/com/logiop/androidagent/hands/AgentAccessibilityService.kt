package com.logiop.androidagent.hands

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.logiop.androidagent.memory.ElementLocator
import com.logiop.androidagent.memory.LocatorMatcher
import com.logiop.androidagent.memory.UiStateDescriptor

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

    /** Fingerprint of the current screen state (package + key element ids/text). */
    fun captureDescriptor(): UiStateDescriptor {
        val root = rootInActiveWindow ?: return UiStateDescriptor("", emptyList())
        return ScreenReader.descriptorOf(root)
    }

    /** Package name of the app currently in the foreground, if known. */
    fun currentPackage(): String? = rootInActiveWindow?.packageName?.toString()

    /**
     * Clicks the first clickable node whose text/description matches [query].
     * Returns the locator of the matched element, or null if nothing was clicked.
     */
    fun clickByText(query: String): ElementLocator? {
        val root = rootInActiveWindow ?: return null
        for (node in root.findAccessibilityNodeInfosByText(query)) {
            val target = clickableAncestor(node) ?: continue
            if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return locatorOf(node)
            }
        }
        return null
    }

    /**
     * Types [text] into the focused (or first) editable field. Returns the
     * locator of the field, or null if none was found.
     */
    fun typeText(text: String): ElementLocator? {
        val root = rootInActiveWindow ?: return null
        val field = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: firstMatching(root) { it.isEditable }
            ?: return null
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return if (field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            locatorOf(field)
        } else {
            null
        }
    }

    /**
     * Scrolls the first scrollable container. Returns the locator of the
     * scrolled container, or null if none was found/scrolled.
     */
    fun scroll(forward: Boolean): ElementLocator? {
        val root = rootInActiveWindow ?: return null
        val scrollable = firstMatching(root) { it.isScrollable } ?: return null
        val action = if (forward) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        return if (scrollable.performAction(action)) locatorOf(scrollable) else null
    }

    /** Finds the on-screen node that best matches [target] above the score threshold. */
    fun findByLocator(target: ElementLocator): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        var best: AccessibilityNodeInfo? = null
        var bestScore = 0.0
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val s = LocatorMatcher.score(target, locatorOf(node))
            if (s > bestScore) {
                bestScore = s
                best = node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return if (bestScore >= LocatorMatcher.THRESHOLD) best else null
    }

    /**
     * Taps a neutral "dismiss" button of an unexpected transient dialog (OK,
     * Chiudi, Continua, Salta…). Deliberately excludes permission-granting
     * buttons (Consenti/Allow/Accetta) and cancel buttons, so replay never
     * silently grants a permission or cancels the user's intent. Returns true
     * if a dialog was dismissed.
     */
    fun dismissTransientDialog(): Boolean {
        val root = rootInActiveWindow ?: return false
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val label = (node.text ?: node.contentDescription ?: "").toString().trim()
            if (label.isNotEmpty() && isDismissLabel(label)) {
                val target = clickableAncestor(node)
                if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
    }

    private fun isDismissLabel(label: String): Boolean =
        DISMISS_KEYWORDS.any { kw ->
            Regex("""\b${Regex.escape(kw)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(label)
        }

    /** Replays a control action on the element matching [target]. */
    fun actOnLocator(target: ElementLocator, action: String, argument: String): Boolean {
        val node = findByLocator(target) ?: return false
        return when (action) {
            "tap" -> clickableAncestor(node)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
            "type" -> {
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, argument)
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            }
            "scroll" -> {
                val forward = argument != "up" && argument != "backward"
                node.performAction(
                    if (forward) {
                        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    } else {
                        AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                    },
                )
            }
            else -> false
        }
    }

    private fun locatorOf(node: AccessibilityNodeInfo): ElementLocator {
        val parent = node.parent
        val siblingText = parent?.let { p ->
            (0 until p.childCount)
                .mapNotNull { p.getChild(it) }
                .firstOrNull { it != node && !it.text.isNullOrBlank() }
                ?.text?.toString()
        }
        return ElementLocator(
            resourceId = node.viewIdResourceName.orEmpty(),
            text = node.text?.toString().orEmpty(),
            contentDesc = node.contentDescription?.toString().orEmpty(),
            className = node.className?.toString().orEmpty(),
            parentText = parent?.text?.toString().orEmpty(),
            siblingText = siblingText.orEmpty(),
        )
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

        /** Neutral dismiss labels only — no permission grants, no cancels. */
        private val DISMISS_KEYWORDS = listOf(
            "ok", "chiudi", "close", "continua", "continue", "salta", "skip",
            "capito", "got it", "va bene", "fine", "done",
        )

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
