package com.example.bilibili.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

private const val BilibiliPackageName = "tv.danmaku.bili"

object BilibiliAppLauncher {
    fun openVideo(
        context: Context,
        bvid: String,
        aid: Long = 0L,
    ): Boolean {
        val candidates = buildList {
            if (bvid.isNotBlank()) {
                add("bilibili://video/$bvid")
                add("https://www.bilibili.com/video/$bvid")
            }
            if (aid > 0L) {
                add("bilibili://video/av$aid")
                add("https://www.bilibili.com/video/av$aid")
            }
        }
        for (uri in candidates) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                setPackage(BilibiliPackageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return true
            }
        }
        val fallbackUri = when {
            bvid.isNotBlank() -> "https://www.bilibili.com/video/$bvid"
            aid > 0L -> "https://www.bilibili.com/video/av$aid"
            else -> return false
        }
        val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUri)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(fallbackIntent)
            true
        }.getOrElse {
            Toast.makeText(context, "未安装哔哩哔哩客户端", Toast.LENGTH_SHORT).show()
            false
        }
    }
}
