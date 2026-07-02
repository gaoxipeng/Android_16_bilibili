package com.example.bilibili.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun Modifier.consumeTouchEvents(): Modifier = clickable(
    indication = null,
    interactionSource = remember { MutableInteractionSource() },
    onClick = {},
)

fun Modifier.blockHiddenTouches(visible: Boolean): Modifier =
    if (visible) {
        this
    } else {
        then(
            Modifier.pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    }
                }
            },
        )
    }
