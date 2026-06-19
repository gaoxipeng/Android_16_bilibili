package com.example.bilibili.data

import java.net.URLEncoder
import java.security.MessageDigest
import kotlin.math.min

object WbiSigner {
    private val mixinOrder = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
        33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40, 61,
        26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 52, 11, 36, 20, 34, 44,
        6,
    )

    fun deriveMixinKey(imgUrl: String, subUrl: String): String {
        fun token(url: String): String = url.substringAfterLast('/').substringBefore('.')
        val raw = token(imgUrl) + token(subUrl)
        return buildString {
            for (index in mixinOrder) {
                if (index < raw.length) append(raw[index])
            }
        }.take(32)
    }

    fun sign(params: MutableMap<String, String>, mixinKey: String): Map<String, String> {
        params.remove("w_rid")
        params["wts"] = (System.currentTimeMillis() / 1000L).toString()
        if (!params.containsKey("web_location")) {
            params["web_location"] = "1550101"
        }
        val query = params.entries
            .sortedBy { it.key }
            .joinToString("&") { (key, value) ->
                "${encode(key)}=${encode(value)}"
            }
        params["w_rid"] = md5(query + mixinKey)
        return params
    }

    fun signWbi2(params: MutableMap<String, String>): Map<String, String> {
        val chars = "ABCDEFGHIJK"
        val randomChars = chars.toList().shuffled().take(2).joinToString("")
        params["dm_img_list"] = "[]"
        params["dm_img_str"] = randomChars
        params["dm_cover_img_str"] = chars.toList().shuffled().take(2).joinToString("")
        params["dm_img_inter"] = """{"ds":[],"wh":[0,0,0],"of":[0,0,0]}"""
        return params
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
            .replace("+", "%20")
            .replace("%21", "!")
            .replace("%27", "'")
            .replace("%28", "(")
            .replace("%29", ")")
            .replace("%7E", "~")

    private fun md5(text: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
