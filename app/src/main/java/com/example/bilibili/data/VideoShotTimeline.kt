package com.example.bilibili.data

/**
 * videoshot 时间轴：优先用 pvdata 的 UInt16 秒表，逻辑对齐 Mac 客户端。
 */
object VideoShotTimeline {
    fun normalized(values: List<Int>, capacity: Int): List<Int> {
        if (capacity <= 0 || values.isEmpty()) return emptyList()
        val timeline = (if (values.size > capacity) values.take(capacity) else values).toMutableList()
        if (timeline[0] != 0) timeline[0] = 0
        return timeline
    }

    fun isPlausible(values: List<Int>): Boolean {
        if (values.isEmpty()) return false
        if (values[0] != 0 && values[0] >= 120) return false
        val last = values.last()
        if (last < 0 || last >= 864_000) return false
        var regressions = 0
        for (index in 1 until values.size) {
            if (values[index] < values[index - 1]) regressions++
        }
        return regressions == 0
    }

    fun parseUInt16Timeline(payload: ByteArray, littleEndian: Boolean): List<Int> {
        if (payload.size < 2) return emptyList()
        val values = ArrayList<Int>(payload.size / 2)
        var offset = 0
        while (offset + 1 < payload.size) {
            val value = if (littleEndian) {
                (payload[offset].toInt() and 0xff) or ((payload[offset + 1].toInt() and 0xff) shl 8)
            } else {
                ((payload[offset].toInt() and 0xff) shl 8) or (payload[offset + 1].toInt() and 0xff)
            }
            values.add(value)
            offset += 2
        }
        return values
    }

    fun parsePvdataTimeline(payload: ByteArray): List<Int> {
        val bigEndian = parseUInt16Timeline(payload, littleEndian = false)
        if (isPlausible(bigEndian)) return bigEndian
        val littleEndian = parseUInt16Timeline(payload, littleEndian = true)
        if (isPlausible(littleEndian)) return littleEndian
        return bigEndian
    }

    fun preferTimeline(inlineIndices: List<Int>, pvIndices: List<Int>): List<Int> =
        when {
            isPlausible(pvIndices) && pvIndices.size >= inlineIndices.size -> pvIndices
            inlineIndices.isEmpty() && isPlausible(pvIndices) -> pvIndices
            else -> inlineIndices
        }
}
