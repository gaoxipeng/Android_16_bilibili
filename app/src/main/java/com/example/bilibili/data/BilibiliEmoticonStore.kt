package com.example.bilibili.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Stores live room emoticons synchronized from Bilibili so they can be reused in other rooms. */
class BilibiliEmoticonStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(): Map<String, BiliDanmakuEmoticon> {
        val array = runCatching { JSONArray(prefs.getString(KEY_EMOTICONS, "[]")) }
            .getOrDefault(JSONArray())
        return buildMap {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val phrase = item.optString("phrase").trim()
                val url = item.optString("url").trim()
                if (phrase.isNotEmpty() && url.isNotEmpty()) {
                    put(phrase, BiliDanmakuEmoticon(
                        url = url,
                        width = item.optInt("width"),
                        height = item.optInt("height"),
                    ))
                }
            }
        }
    }

    fun write(emoticons: Map<String, BiliDanmakuEmoticon>) {
        val array = JSONArray()
        emoticons.forEach { (phrase, spec) ->
            if (phrase.isNotBlank() && spec.url.isNotBlank()) {
                array.put(JSONObject()
                    .put("phrase", phrase)
                    .put("url", spec.url)
                    .put("width", spec.width)
                    .put("height", spec.height))
            }
        }
        prefs.edit().putString(KEY_EMOTICONS, array.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "bilibili_emoticon_prefs"
        const val KEY_EMOTICONS = "synced_bilibili_emoticons"
    }
}
