package com.example.bilibili.data

import java.security.MessageDigest

object BiliAppSign {
    fun sign(params: Map<String, String>, appSecret: String): String {
        val query = params.entries
            .sortedBy { it.key }
            .joinToString("&") { "${it.key}=${it.value}" }
        return md5(query + appSecret)
    }

    fun signQuery(baseParams: Map<String, String>, appSecret: String, includeTs: Boolean = true): Map<String, String> {
        val params = baseParams.toMutableMap()
        if (includeTs && !params.containsKey("ts")) {
            params["ts"] = (System.currentTimeMillis() / 1000L).toString()
        }
        params["sign"] = sign(params, appSecret)
        return params
    }

    private fun md5(value: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
