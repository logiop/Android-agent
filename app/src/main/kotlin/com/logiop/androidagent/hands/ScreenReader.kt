package com.logiop.androidagent.hands

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.logiop.androidagent.memory.UiStateDescriptor

/**
 * Extracts a compact list of interactive elements from an accessibility node
 * tree: only actionable nodes, text truncated, capped at [MAX_ELEMENTS] so the
 * result stays small enough to feed to an on-device LLM.
 */
object ScreenReader {

    const val MAX_ELEMENTS = 30
    private const val MAX_TEXT_LENGTH = 40

    fun capture(root: AccessibilityNodeInfo, max: Int = MAX_ELEMENTS): UiSnapshot {
        val elements = ArrayList<UiElement>(max)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty() && elements.size < max) {
            val node = queue.removeFirst()
            if (isInteresting(node)) {
                elements.add(node.toElement(elements.size))
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return UiSnapshot(elements, formatCompact(elements))
    }

    private fun isInteresting(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser) return false
        return node.isClickable || node.isEditable || node.isScrollable ||
            node.isCheckable || node.isLongClickable
    }

    private fun AccessibilityNodeInfo.toElement(index: Int): UiElement {
        val bounds = Rect().also { getBoundsInScreen(it) }
        val raw = (text ?: contentDescription ?: "").toString().trim()
        val label = if (raw.length > MAX_TEXT_LENGTH) raw.take(MAX_TEXT_LENGTH) + "…" else raw
        return UiElement(
            index = index,
            role = roleOf(this),
            text = label,
            clickable = isClickable,
            editable = isEditable,
            scrollable = isScrollable,
            bounds = bounds,
            resourceId = viewIdResourceName.orEmpty(),
            contentDesc = contentDescription?.toString().orEmpty(),
            className = className?.toString().orEmpty(),
        )
    }

    /**
     * A fingerprint of the current screen: the foreground package plus the
     * resource ids / text of visible interactive elements. Used to detect
     * whether an action actually changed the state.
     */
    fun descriptorOf(root: AccessibilityNodeInfo): UiStateDescriptor {
        val snapshot = capture(root)
        val keys = snapshot.elements.map { element ->
            element.resourceId.ifBlank { element.text }
        }.filter { it.isNotBlank() }
        return UiStateDescriptor(
            packageName = root.packageName?.toString().orEmpty(),
            keys = keys,
        )
    }

    private fun roleOf(node: AccessibilityNodeInfo): String {
        val cls = node.className?.toString().orEmpty()
        return when {
            node.isEditable -> "edit"
            cls.endsWith("Button") || cls.endsWith("ImageButton") -> "button"
            node.isScrollable -> "scroll"
            node.isCheckable -> "toggle"
            else -> "item"
        }
    }

    private fun formatCompact(elements: List<UiElement>): String =
        elements.joinToString("\n") { e ->
            val flags = buildList {
                if (e.clickable) add("click")
                if (e.editable) add("edit")
                if (e.scrollable) add("scroll")
            }.joinToString(",")
            val suffix = if (flags.isNotEmpty()) " ($flags)" else ""
            "[${e.index}] ${e.role} \"${e.text}\"$suffix"
        }
}
