package com.example.bilibili.ui.components.imageviewer

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.bilibili.data.BiliImageSaveHelper
import com.example.bilibili.data.BiliViewerImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

val LocalBiliImageSaveHint = compositionLocalOf { BiliImageSaveHintController() }

enum class BiliCapsuleHintTone {
    Neutral,
    Success,
    Progress,
}

data class BiliCapsuleHintState(
    val message: String,
    val tone: BiliCapsuleHintTone = BiliCapsuleHintTone.Neutral,
    val autoDismissMillis: Long? = 2200L,
    val progress: Float? = null,
)

class BiliImageSaveHintController {
    var activeHint by mutableStateOf<BiliCapsuleHintState?>(null)

    fun showProgress(current: Int, total: Int) {
        activeHint = BiliCapsuleHintState(
            message = "正在保存 $current/$total",
            tone = BiliCapsuleHintTone.Progress,
            autoDismissMillis = null,
            progress = if (total > 0) current.toFloat() / total.toFloat() else null,
        )
    }

    fun showBatchSaveProgress(progress: BiliImageSaveHelper.SaveAllProgress) {
        val message = if (progress.total > 1) {
            "正在保存第 ${progress.activeIndex}/${progress.total} 张"
        } else {
            "正在保存"
        }
        activeHint = BiliCapsuleHintState(
            message = message,
            tone = BiliCapsuleHintTone.Progress,
            autoDismissMillis = null,
            progress = if (progress.total > 0) {
                progress.completed.toFloat() / progress.total.toFloat()
            } else {
                null
            },
        )
    }

    fun showSuccess(message: String) {
        activeHint = BiliCapsuleHintState(
            message = message,
            tone = BiliCapsuleHintTone.Success,
            autoDismissMillis = 2400L,
        )
    }

    fun showFailure(message: String) {
        activeHint = BiliCapsuleHintState(
            message = message,
            tone = BiliCapsuleHintTone.Neutral,
            autoDismissMillis = 2800L,
        )
    }

    fun clear() {
        activeHint = null
    }

    suspend fun saveOne(context: Context, image: BiliViewerImage): Boolean {
        showProgress(1, 1)
        return BiliImageSaveHelper.saveImage(context, image)
            .onSuccess { showSuccess("已保存到相册") }
            .onFailure { error -> showFailure(error.message ?: "保存失败") }
            .isSuccess
    }

    suspend fun saveAll(context: Context, images: List<BiliViewerImage>): BiliImageSaveHelper.SaveAllImagesResult {
        if (images.isEmpty()) {
            showFailure("没有可保存的内容")
            return BiliImageSaveHelper.SaveAllImagesResult(
                saved = 0,
                total = 0,
                errors = listOf("没有可保存的内容"),
            )
        }
        val result = BiliImageSaveHelper.saveAllImages(context, images) { progress ->
            withContext(Dispatchers.Main.immediate) {
                showBatchSaveProgress(progress)
            }
        }
        when {
            result.saved == result.total && result.total > 0 ->
                showSuccess("已保存 ${result.saved} 张图片到相册")
            result.saved > 0 ->
                showSuccess("已保存 ${result.saved}/${result.total} 张图片")
            else ->
                showFailure(result.errors.firstOrNull()?.takeIf { it.isNotBlank() } ?: "保存失败")
        }
        return result
    }
}

@Composable
fun BiliImageSaveHintOverlay(
    modifier: Modifier = Modifier,
    zIndex: Float = 1000f,
) {
    val saveHintController = LocalBiliImageSaveHint.current
    val hint = saveHintController.activeHint
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    AnimatedVisibility(
        visible = hint != null,
        enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { fullHeight -> -fullHeight / 2 },
        exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { fullHeight -> -fullHeight / 2 },
        modifier = modifier
            .zIndex(zIndex)
            .padding(top = topInset + 10.dp),
    ) {
        hint?.let {
            BiliSaveCapsuleHint(
                message = it.message,
                tone = it.tone,
                autoDismissMillis = it.autoDismissMillis,
                progress = it.progress,
                onDismiss = { saveHintController.clear() },
            )
        }
    }
}

@Composable
private fun BiliSaveCapsuleHint(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    tone: BiliCapsuleHintTone = BiliCapsuleHintTone.Neutral,
    autoDismissMillis: Long? = 2200L,
    progress: Float? = null,
) {
    LaunchedEffect(message, autoDismissMillis) {
        autoDismissMillis?.let { millis ->
            delay(millis)
            onDismiss()
        }
    }

    val background = when (tone) {
        BiliCapsuleHintTone.Success -> Color(0xFF34C759)
        BiliCapsuleHintTone.Progress -> Color(0xFF00AEEC)
        BiliCapsuleHintTone.Neutral -> Color(0xFF323232)
    }
    val textColor = Color.White
    val shape = RoundedCornerShape(22.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(background, shape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.wrapContentSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                )
                if (tone == BiliCapsuleHintTone.Progress && progress != null) {
                    Text(
                        text = "${(progress * 100f).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.85f),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}
