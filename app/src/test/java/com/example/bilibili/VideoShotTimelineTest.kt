package com.example.bilibili

import com.example.bilibili.data.BiliVideoShot
import com.example.bilibili.data.VideoShotTimeline
import com.example.bilibili.player.locateTile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoShotTimelineTest {
    @Test
    fun parseBigEndianTimeline() {
        val payload = byteArrayOf(0x00, 0x00, 0x00, 0x0A, 0x00, 0x14)
        assertEquals(listOf(0, 10, 20), VideoShotTimeline.parseUInt16Timeline(payload, littleEndian = false))
    }

    @Test
    fun preferLongerPlausiblePvdata() {
        val inline = listOf(0, 5, 10)
        val pv = listOf(0, 3, 6, 9, 12)
        assertEquals(pv, VideoShotTimeline.preferTimeline(inline, pv))
    }

    @Test
    fun rejectNonMonotonicTimeline() {
        assertFalse(VideoShotTimeline.isPlausible(listOf(0, 10, 5)))
        assertTrue(VideoShotTimeline.isPlausible(listOf(0, 10, 20)))
    }

    @Test
    fun normalizeForcesLeadingZeroAndCapacity() {
        assertEquals(listOf(0, 8, 16), VideoShotTimeline.normalized(listOf(3, 8, 16, 24), capacity = 3))
    }

    @Test
    fun locateTileUsesIndexSecondsAndStopsPastCoverage() {
        val shot = BiliVideoShot(
            images = listOf("https://example.com/s.jpg"),
            indexSeconds = listOf(0, 10, 20),
            tileColumns = 10,
            tileRows = 10,
            tileWidth = 160,
            tileHeight = 90,
        )
        val mid = shot.locateTile(positionMs = 15_000L, durationMs = 60_000L)
        assertNotNull(mid)
        assertEquals(1, mid!!.column + mid.row * 10)

        assertNull(shot.locateTile(positionMs = 25_000L, durationMs = 60_000L))
    }
}
