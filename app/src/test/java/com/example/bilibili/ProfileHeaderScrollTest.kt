package com.example.bilibili

import com.example.bilibili.ui.screens.consumeProfileHeaderScroll
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileHeaderScrollTest {
    @Test fun upwardDragCollapsesHeaderBeforeListScroll() {
        val result = consumeProfileHeaderScroll(0f, 300f, -100f)
        assertEquals(100f, result.offset, 0.001f)
        assertEquals(-100f, result.consumed, 0.001f)
    }

    @Test fun remainingDeltaContinuesIntoListWithoutPause() {
        val result = consumeProfileHeaderScroll(280f, 300f, -100f)
        assertEquals(300f, result.offset, 0.001f)
        assertEquals(-20f, result.consumed, 0.001f)
        assertEquals(-80f, -100f - result.consumed, 0.001f)
        assertEquals(0f, consumeProfileHeaderScroll(300f, 300f, -100f).consumed, 0.001f)
    }

    @Test fun unusedDownwardDeltaExpandsHeaderOnlyUntilFullyOpen() {
        val result = consumeProfileHeaderScroll(40f, 300f, 100f)
        assertEquals(0f, result.offset, 0.001f)
        assertEquals(40f, result.consumed, 0.001f)
    }
}
