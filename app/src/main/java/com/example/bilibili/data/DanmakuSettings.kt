package com.example.bilibili.data

enum class DanmakuSpeedLevel(val label: String, val durationMultiplier: Float) {
    VerySlow("极慢", 1.85f),
    Slow("较慢", 1.35f),
    Medium("适中", 1f),
    Fast("较快", 0.72f),
    VeryFast("极快", 0.5f),
    ;

    companion object {
        fun fromIndex(index: Int): DanmakuSpeedLevel =
            entries.getOrElse(index.coerceIn(0, entries.lastIndex)) { Medium }
    }
}

data class DanmakuSettings(
    val displayAreaPercent: Int = 100,
    val opacityPercent: Int = 100,
    val fontSizePercent: Int = 100,
    val speedLevel: DanmakuSpeedLevel = DanmakuSpeedLevel.Medium,
) {
    val displayAreaIndex: Int
        get() = DISPLAY_AREA_OPTIONS.indexOf(displayAreaPercent).coerceAtLeast(0)

    fun withDisplayAreaIndex(index: Int): DanmakuSettings =
        copy(displayAreaPercent = DISPLAY_AREA_OPTIONS[index.coerceIn(DISPLAY_AREA_OPTIONS.indices)])

    companion object {
        val DISPLAY_AREA_OPTIONS = intArrayOf(10, 25, 50, 75, 100)

        fun displayAreaLabel(percent: Int): String = "$percent%"
    }
}
