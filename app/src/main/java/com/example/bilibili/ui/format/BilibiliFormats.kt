package com.example.bilibili.ui.format

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatBiliCount(count: Long): String = when {
    count >= 100_000_000 -> "%.1f亿".format(count / 100_000_000.0)
    count >= 10_000 -> "%.1f万".format(count / 10_000.0)
    else -> count.toString()
}

fun formatBiliPublishTime(seconds: Long): String {
    if (seconds <= 0L) return "刚刚"
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return formatter.format(Date(seconds * 1000L))
}

fun formatBiliCommentTime(seconds: Long): String {
    if (seconds <= 0L) return ""
    val now = System.currentTimeMillis()
    val delta = now - seconds * 1000L
    return when {
        delta < 60_000L -> "刚刚"
        delta < 3_600_000L -> "${delta / 60_000L}分钟前"
        delta < 86_400_000L -> "${delta / 3_600_000L}小时前"
        else -> formatBiliPublishTime(seconds)
    }
}
