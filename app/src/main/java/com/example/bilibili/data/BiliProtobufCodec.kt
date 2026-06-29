package com.example.bilibili.data

import java.io.ByteArrayOutputStream

internal object BiliProtobufCodec {
    fun buildDynDetailReq(dynamicId: String): ByteArray = ByteArrayOutputStream().apply {
        writeStringField(this, 2, dynamicId)
        writeVarintField(this, 10, 8)
    }.toByteArray()

    fun findField14Strings(data: ByteArray): List<String> = buildList {
        collectField14Strings(data, 0, data.size, this)
    }

    fun buildMetadata(accessKey: String, buvid: String): ByteArray = ByteArrayOutputStream().apply {
        writeStringField(this, 1, accessKey)
        writeStringField(this, 2, "android")
        writeStringField(this, 3, "phone")
        writeVarintField(this, 4, 8510300)
        writeStringField(this, 5, "master")
        writeStringField(this, 6, buvid)
        writeStringField(this, 7, "android")
    }.toByteArray()

    fun buildFawkesReq(): ByteArray = ByteArrayOutputStream().apply {
        writeStringField(this, 1, "android64")
        writeStringField(this, 2, "prod")
        writeStringField(this, 3, "bili-dynamic-ip")
    }.toByteArray()

    fun buildDevice(buvid: String): ByteArray = ByteArrayOutputStream().apply {
        writeVarintField(this, 1, 1)
        writeVarintField(this, 2, 8510300)
        writeStringField(this, 3, buvid)
        writeStringField(this, 4, "android")
        writeStringField(this, 5, "android")
        writeStringField(this, 6, "phone")
        writeStringField(this, 7, "master")
        writeStringField(this, 8, "Xiaomi")
        writeStringField(this, 9, "Mi 11")
        writeStringField(this, 10, "Android 13")
        writeStringField(this, 13, "8.51.0")
    }.toByteArray()

    fun buildNetwork(): ByteArray = ByteArrayOutputStream().apply {
        writeVarintField(this, 1, 2)
        writeVarintField(this, 2, 0)
        writeStringField(this, 3, "46000")
    }.toByteArray()

    fun buildLocale(): ByteArray = ByteArrayOutputStream().apply {
        writeMessageField(this, 1, buildLocaleIds("zh", "Hans", "CN"))
        writeMessageField(this, 2, buildLocaleIds("zh", "Hans", "CN"))
        writeStringField(this, 3, "46000")
        writeStringField(this, 4, "Asia/Shanghai")
    }.toByteArray()

    private fun buildLocaleIds(language: String, script: String, region: String): ByteArray =
        ByteArrayOutputStream().apply {
            writeStringField(this, 1, language)
            writeStringField(this, 2, script)
            writeStringField(this, 3, region)
        }.toByteArray()

    private fun collectField14Strings(data: ByteArray, start: Int, end: Int, out: MutableList<String>) {
        var index = start
        while (index < end) {
            val tag = readVarint(data, index) ?: return
            index = tag.second
            val fieldNumber = (tag.first shr 3).toInt()
            when (tag.first and 7) {
                0L -> index = skipVarint(data, index)
                1L -> index += 8
                2L -> {
                    val length = readVarint(data, index) ?: return
                    index = length.second
                    val chunkEnd = index + length.first.toInt()
                    if (chunkEnd > end) return
                    val chunk = data.copyOfRange(index, chunkEnd)
                    index = chunkEnd
                    if (fieldNumber == 14) {
                        out += chunk.decodeToString()
                    } else {
                        collectField14Strings(chunk, 0, chunk.size, out)
                    }
                }
                5L -> index += 4
                else -> return
            }
        }
    }

    private fun writeTag(output: ByteArrayOutputStream, fieldNumber: Int, wireType: Int) {
        writeVarint(output, ((fieldNumber shl 3) or wireType).toLong())
    }

    private fun writeStringField(output: ByteArrayOutputStream, fieldNumber: Int, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeTag(output, fieldNumber, 2)
        writeVarint(output, bytes.size.toLong())
        output.write(bytes)
    }

    private fun writeVarintField(output: ByteArrayOutputStream, fieldNumber: Int, value: Int) {
        writeTag(output, fieldNumber, 0)
        writeVarint(output, value.toLong())
    }

    private fun writeMessageField(output: ByteArrayOutputStream, fieldNumber: Int, value: ByteArray) {
        writeTag(output, fieldNumber, 2)
        writeVarint(output, value.size.toLong())
        output.write(value)
    }

    private fun writeVarint(output: ByteArrayOutputStream, value: Long) {
        var current = value
        while (current and 0x7FL.inv() != 0L) {
            output.write(((current and 0x7F) or 0x80).toInt())
            current = current ushr 7
        }
        output.write(current.toInt())
    }

    private fun readVarint(data: ByteArray, start: Int): Pair<Long, Int>? {
        var result = 0L
        var shift = 0
        var index = start
        while (index < data.size && shift < 64) {
            val byte = data[index].toInt() and 0xFF
            index++
            result = result or ((byte and 0x7F).toLong() shl shift)
            if (byte and 0x80 == 0) return result to index
            shift += 7
        }
        return null
    }

    private fun skipVarint(data: ByteArray, start: Int): Int {
        var index = start
        while (index < data.size) {
            if (data[index].toInt() and 0x80 == 0) return index + 1
            index++
        }
        return index
    }
}
