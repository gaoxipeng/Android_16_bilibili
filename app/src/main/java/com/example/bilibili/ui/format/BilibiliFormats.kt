package com.example.bilibili.ui.format

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatBiliCount(count: Long): String = when {
    count >= 100_000_000 -> "%.1f亿".format(count / 100_000_000.0)
    count >= 10_000 -> "%.1f万".format(count / 10_000.0)
    else -> count.toString()
}

fun formatBiliCoinBalance(balance: Double): String {
    if (balance <= 0.0) return "0"
    return if (kotlin.math.abs(balance % 1.0) < 0.05) {
        balance.toLong().toString()
    } else {
        String.format(Locale.getDefault(), "%.1f", balance)
    }
}

fun formatBiliPublishTime(seconds: Long): String {
    if (seconds <= 0L) return "刚刚"
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return formatter.format(Date(seconds * 1000L))
}

fun formatBiliHistoryViewTime(seconds: Long): String {
    if (seconds <= 0L) return ""
    val formatter = SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.getDefault())
    return formatter.format(Date(seconds * 1000L))
}

fun formatBiliHistorySectionLabel(seconds: Long): String {
    if (seconds <= 0L) return "更早"
    val itemCal = java.util.Calendar.getInstance().apply {
        timeInMillis = seconds * 1000L
    }
    val today = java.util.Calendar.getInstance()
    val yesterday = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
    fun sameDay(a: java.util.Calendar, b: java.util.Calendar): Boolean =
        a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) &&
            a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)
    return when {
        sameDay(itemCal, today) -> "今天"
        sameDay(itemCal, yesterday) -> "昨天"
        itemCal.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) ->
            "${itemCal.get(java.util.Calendar.MONTH) + 1}月${itemCal.get(java.util.Calendar.DAY_OF_MONTH)}日"
        else ->
            "${itemCal.get(java.util.Calendar.YEAR)}年${itemCal.get(java.util.Calendar.MONTH) + 1}月${itemCal.get(java.util.Calendar.DAY_OF_MONTH)}日"
    }
}

fun formatVideoDurationLabel(totalSeconds: Int): String {
    if (totalSeconds <= 0) return "0:00"
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

fun formatHistoryDurationBadge(progressSeconds: Int, durationSeconds: Int): String {
    if (progressSeconds > 0 && durationSeconds > 0 && progressSeconds < durationSeconds) {
        return "${formatVideoDurationLabel(progressSeconds)} / ${formatVideoDurationLabel(durationSeconds)}"
    }
    return formatVideoDurationLabel(durationSeconds)
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
