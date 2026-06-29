package com.example.bilibili.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object ExternalUrlOpener {
    fun open(context: Context, url: String) {
        if (url.isBlank()) return
        val normalized = if (url.startsWith("http://", ignoreCase = true) ||
            url.startsWith("https://", ignoreCase = true)
        ) {
            url
        } else {
            "https://$url"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
        }
    }
}
