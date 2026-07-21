package com.logiop.androidagent.hands

import android.graphics.Rect

/** A single interactive element extracted from the current screen. */
data class UiElement(
    val index: Int,
    val role: String,
    val text: String,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val bounds: Rect,
)

/**
 * Compact snapshot of the interactive elements currently on screen.
 *
 * [compactText] is the representation the LLM "brain" will consume in a later
 * step; [elements] keeps the structured data for direct action execution.
 */
data class UiSnapshot(
    val elements: List<UiElement>,
    val compactText: String,
)
