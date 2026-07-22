package com.logiop.androidagent.memory

/**
 * Scores how well a candidate element matches a stored [ElementLocator], using
 * weighted multi-feature matching so a skill still finds its target when a
 * single attribute (e.g. resourceId) changes.
 */
object LocatorMatcher {

    const val THRESHOLD = 0.5

    private const val W_RESOURCE_ID = 0.40
    private const val W_TEXT = 0.20
    private const val W_CONTENT_DESC = 0.15
    private const val W_CLASS_NAME = 0.10
    private const val W_PARENT = 0.10
    private const val W_SIBLING = 0.05

    fun score(target: ElementLocator, candidate: ElementLocator): Double {
        var score = 0.0
        if (exact(target.resourceId, candidate.resourceId)) score += W_RESOURCE_ID
        if (ci(target.text, candidate.text)) score += W_TEXT
        if (ci(target.contentDesc, candidate.contentDesc)) score += W_CONTENT_DESC
        if (exact(target.className, candidate.className)) score += W_CLASS_NAME
        if (ci(target.parentText, candidate.parentText)) score += W_PARENT
        if (ci(target.siblingText, candidate.siblingText)) score += W_SIBLING
        return score
    }

    private fun exact(a: String, b: String): Boolean = a.isNotBlank() && a == b

    private fun ci(a: String, b: String): Boolean = a.isNotBlank() && a.equals(b, ignoreCase = true)
}
