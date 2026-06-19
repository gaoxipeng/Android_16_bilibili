package com.example.bilibili

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.bilibili.ui.BilibiliApp
import com.example.bilibili.ui.theme.BilibiliTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BilibiliTheme {
                BilibiliApp()
            }
        }
    }
}
