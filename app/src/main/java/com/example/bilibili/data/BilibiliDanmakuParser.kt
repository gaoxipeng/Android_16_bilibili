package com.example.bilibili.data

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

object BilibiliDanmakuParser {
    fun parseProtobufSeg(bytes: ByteArray): List<BiliDanmakuItem> {
        if (bytes.isEmpty()) return emptyList()
        val items = mutableListOf<BiliDanmakuItem>()
        var offset = 0
        while (offset < bytes.size) {
            val tag = readTag(bytes, offset) ?: break
            offset = tag.second
            val fieldNumber = tag.first ushr 3
            val wireType = tag.first and 0x7
            when {
                fieldNumber == 1 && wireType == 2 -> {
                    val lengthInfo = readVarint(bytes, offset) ?: break
                    offset = lengthInfo.second
                    val length = lengthInfo.first.toInt()
                    val end = offset + length
                    if (end > bytes.size) break
                    parseDanmakuElem(bytes, offset, end)?.let(items::add)
                    offset = end
                }
                wireType == 0 -> {
                    val skipped = readVarint(bytes, offset) ?: break
                    offset = skipped.second
                }
                wireType == 2 -> {
                    val lengthInfo = readVarint(bytes, offset) ?: break
                    offset = lengthInfo.second + lengthInfo.first.toInt()
                }
                wireType == 1 -> offset += 8
                wireType == 5 -> offset += 4
                else -> break
            }
        }
        return items
    }

    fun parseListSo(bytes: ByteArray): List<BiliDanmakuItem> {
        if (bytes.isEmpty()) return emptyList()
        if (bytes.firstOrNull() == 0x0A.toByte()) {
            return parseProtobufSeg(bytes)
        }
        if (bytes.firstOrNull() == '<'.code.toByte()) {
            return parseXml(String(bytes, Charsets.UTF_8))
        }
        return parseLegacyBinary(bytes)
    }

    private fun parseLegacyBinary(bytes: ByteArray): List<BiliDanmakuItem> {
        val items = mutableListOf<BiliDanmakuItem>()
        var offset = 0
        while (offset + 21 <= bytes.size) {
            val buffer = ByteBuffer.wrap(bytes, offset, bytes.size - offset).order(ByteOrder.LITTLE_ENDIAN)
            val timeSec = buffer.getFloat(0)
            val mode = buffer.getInt(4)
            val fontSize = buffer.getInt(8)
            val color = buffer.getInt(12)
            offset += 21
            val hashEnd = bytes.indexOf(0, offset)
            if (hashEnd < 0) break
            offset = hashEnd + 1
            val contentEnd = bytes.indexOf(0, offset)
            if (contentEnd < 0) break
            val content = String(bytes, offset, contentEnd - offset, Charsets.UTF_8).trim()
            offset = contentEnd + 1
            if (content.isBlank()) continue
            items += BiliDanmakuItem(
                timeMs = max(0L, (timeSec * 1000f).toLong()),
                mode = mode,
                fontSize = fontSize.coerceAtLeast(18),
                colorArgb = color,
                content = content,
            )
        }
        return items
    }

    private fun parseDanmakuElem(bytes: ByteArray, start: Int, end: Int): BiliDanmakuItem? {
        var offset = start
        var progressMs = 0L
        var mode = 1
        var fontSize = 25
        var color = 0xFFFFFF
        var content = ""
        while (offset < end) {
            val tag = readTag(bytes, offset) ?: break
            offset = tag.second
            val fieldNumber = tag.first ushr 3
            val wireType = tag.first and 0x7
            when (wireType) {
                0 -> {
                    val value = readVarint(bytes, offset) ?: break
                    offset = value.second
                    when (fieldNumber) {
                        2 -> progressMs = value.first
                        3 -> mode = value.first.toInt()
                        4 -> fontSize = value.first.toInt().coerceAtLeast(18)
                        5 -> color = value.first.toInt() and 0xFFFFFF
                    }
                }
                2 -> {
                    val lengthInfo = readVarint(bytes, offset) ?: break
                    offset = lengthInfo.second
                    val length = lengthInfo.first.toInt()
                    val chunkEnd = offset + length
                    if (chunkEnd > end) break
                    if (fieldNumber == 7) {
                        content = String(bytes, offset, length, Charsets.UTF_8).trim()
                    }
                    offset = chunkEnd
                }
                1 -> offset += 8
                5 -> offset += 4
                else -> break
            }
        }
        if (content.isBlank()) return null
        return BiliDanmakuItem(
            timeMs = progressMs.coerceAtLeast(0L),
            mode = mode,
            fontSize = fontSize,
            colorArgb = color,
            content = content,
        )
    }

    fun parseXml(xml: String): List<BiliDanmakuItem> {
        if (xml.isBlank()) return emptyList()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(xml.reader())
        val items = mutableListOf<BiliDanmakuItem>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "d") {
                val params = parser.getAttributeValue(null, "p").orEmpty()
                val text = parser.nextText().trim()
                if (text.isNotBlank()) {
                    parseXmlParams(params, text)?.let(items::add)
                }
            }
            event = parser.next()
        }
        return items
    }

    private fun parseXmlParams(params: String, content: String): BiliDanmakuItem? {
        val parts = params.split(',')
        if (parts.size < 4) return null
        val timeSec = parts[0].toFloatOrNull() ?: return null
        val mode = parts[1].toIntOrNull() ?: 1
        val fontSize = parts[2].toIntOrNull()?.coerceAtLeast(18) ?: 25
        val color = parts[3].toIntOrNull() ?: 0xFFFFFF
        return BiliDanmakuItem(
            timeMs = max(0L, (timeSec * 1000f).toLong()),
            mode = mode,
            fontSize = fontSize,
            colorArgb = color,
            content = content,
        )
    }

    private fun readTag(bytes: ByteArray, offset: Int): Pair<Int, Int>? {
        val value = readVarint(bytes, offset) ?: return null
        return value.first.toInt() to value.second
    }

    private fun readVarint(bytes: ByteArray, offset: Int): Pair<Long, Int>? {
        if (offset >= bytes.size) return null
        var result = 0L
        var shift = 0
        var index = offset
        while (index < bytes.size && shift <= 63) {
            val byte = bytes[index].toInt() and 0xFF
            index++
            result = result or ((byte and 0x7F).toLong() shl shift)
            if (byte and 0x80 == 0) {
                return result to index
            }
            shift += 7
        }
        return null
    }
}

private fun ByteArray.indexOf(byte: Int, startIndex: Int): Int {
    for (index in startIndex until size) {
        if (this[index] == byte.toByte()) return index
    }
    return -1
}
