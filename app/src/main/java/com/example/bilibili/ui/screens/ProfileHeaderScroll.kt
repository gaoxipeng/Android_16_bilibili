package com.example.bilibili.ui.screens

internal data class ProfileHeaderScrollResult(val offset: Float, val consumed: Float)

/** Positive offset collapses the header; scroll deltas follow nested-scroll's Y sign. */
internal fun consumeProfileHeaderScroll(offset: Float, range: Float, deltaY: Float): ProfileHeaderScrollResult {
    val limit = range.coerceAtLeast(0f)
    val previous = offset.coerceIn(0f, limit)
    val next = (previous - deltaY).coerceIn(0f, limit)
    return ProfileHeaderScrollResult(next, previous - next)
}
