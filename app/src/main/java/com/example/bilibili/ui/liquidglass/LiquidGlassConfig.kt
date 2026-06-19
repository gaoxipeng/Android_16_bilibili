package com.example.bilibili.ui.liquidglass

import android.os.Build

object LiquidGlassConfig {
    /**
     * RuntimeShader lens 在部分 MIUI / MTK 设备上会触发 RenderThread SIGSEGV。
     */
    val enableLensEffects: Boolean =
        !Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) &&
            !Build.MANUFACTURER.equals("Redmi", ignoreCase = true)
}
