package com.example.bilibili.player

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.TextUnit
import com.example.bilibili.data.BiliDanmakuEmoticon

private const val DanmakuEmoticonReplacementChar = "\uFFFC"

internal data class DanmakuTextMeasure(
    val text: AnnotatedString,
    val placeholders: List<AnnotatedString.Range<Placeholder>>,
    val emoticonUrls: List<String>,
)

internal fun buildDanmakuMeasureText(
    content: String,
    emoticons: Map<String, BiliDanmakuEmoticon>,
    emojiSize: TextUnit,
    fontSize: TextUnit,
): DanmakuTextMeasure {
    if (emoticons.isEmpty()) {
        return DanmakuTextMeasure(
            text = AnnotatedString(content),
            placeholders = emptyList(),
            emoticonUrls = emptyList(),
        )
    }

    val normalizedEmoticons = rememberDanmakuEmoticonAliases(emoticons)
    val phrases = normalizedEmoticons.keys.sortedByDescending { it.length }
    val emoticonUrls = mutableListOf<String>()
    val placeholders = mutableListOf<AnnotatedString.Range<Placeholder>>()
    val annotated = buildAnnotatedString {
        var index = 0
        while (index < content.length) {
            val phrase = phrases.firstOrNull { content.startsWith(it, index) }
            if (phrase != null) {
                val spec = normalizedEmoticons[phrase]
                val url = spec?.url
                if (!url.isNullOrBlank()) {
                    val (width, height) = danmakuEmoticonPlaceholderSize(
                        spec = spec,
                        fontSize = fontSize,
                        fallbackSize = emojiSize,
                    )
                    val placeholder = Placeholder(
                        width = width,
                        height = height,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                    )
                    val start = length
                    append(DanmakuEmoticonReplacementChar)
                    placeholders += AnnotatedString.Range(
                        item = placeholder,
                        start = start,
                        end = length,
                    )
                    emoticonUrls += url
                    index += phrase.length
                    continue
                }
            }
            append(content[index])
            index++
        }
    }

    if (placeholders.isEmpty()) {
        val directSpec = normalizedEmoticons[content]
        if (directSpec != null && directSpec.url.isNotBlank()) {
            return buildSingleEmoticonMeasureText(
                spec = directSpec,
                fontSize = fontSize,
                fallbackSize = emojiSize,
            )
        }
        if (emoticons.size == 1) {
            val onlySpec = emoticons.values.first()
            if (onlySpec.url.isNotBlank()) {
                return buildSingleEmoticonMeasureText(
                    spec = onlySpec,
                    fontSize = fontSize,
                    fallbackSize = emojiSize,
                )
            }
        }
    }

    return DanmakuTextMeasure(
        text = annotated,
        placeholders = placeholders,
        emoticonUrls = emoticonUrls.toList(),
    )
}

private fun rememberDanmakuEmoticonAliases(
    emoticons: Map<String, BiliDanmakuEmoticon>,
): Map<String, BiliDanmakuEmoticon> {
    if (emoticons.isEmpty()) return emptyMap()
    val result = linkedMapOf<String, BiliDanmakuEmoticon>()
    emoticons.forEach { (phrase, spec) ->
        val trimmed = phrase.trim()
        if (trimmed.isBlank()) return@forEach
        result[trimmed] = spec
        result[trimmed.removeSurrounding("[", "]")] = spec
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            result["[$trimmed]"] = spec
        }
    }
    return result
}

private fun buildSingleEmoticonMeasureText(
    spec: BiliDanmakuEmoticon,
    fontSize: TextUnit,
    fallbackSize: TextUnit,
): DanmakuTextMeasure {
    val (width, height) = danmakuEmoticonPlaceholderSize(
        spec = spec,
        fontSize = fontSize,
        fallbackSize = fallbackSize,
    )
    val placeholder = Placeholder(
        width = width,
        height = height,
        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
    )
    val annotated = buildAnnotatedString {
        append(DanmakuEmoticonReplacementChar)
    }
    return DanmakuTextMeasure(
        text = annotated,
        placeholders = listOf(
            AnnotatedString.Range(
                item = placeholder,
                start = 0,
                end = annotated.length,
            ),
        ),
        emoticonUrls = listOf(spec.url),
    )
}

private fun danmakuEmoticonPlaceholderSize(
    spec: BiliDanmakuEmoticon,
    fontSize: TextUnit,
    fallbackSize: TextUnit,
): Pair<TextUnit, TextUnit> {
    if (spec.width <= 0 || spec.height <= 0) {
        return fallbackSize to fallbackSize
    }
    val baseFontPx = 25f
    val scale = (fontSize.value / baseFontPx).coerceIn(0.85f, 2.4f)
    val height = TextUnit(
        value = fallbackSize.value * scale,
        type = fallbackSize.type,
    )
    val width = TextUnit(
        value = height.value * (spec.width.toFloat() / spec.height.toFloat()),
        type = height.type,
    )
    return width to height
}
