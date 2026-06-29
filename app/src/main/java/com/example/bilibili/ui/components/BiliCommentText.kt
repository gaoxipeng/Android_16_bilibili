package com.example.bilibili.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val MentionPattern = Regex("""@([^ @:：\n]+)""")

@Composable
fun BiliCommentText(
    text: String,
    emoticons: Map<String, String>,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    mentionColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val phrases = remember(emoticons) { emoticons.keys.sortedByDescending { it.length } }
    val hasMentions = remember(text) { MentionPattern.containsMatchIn(text) }
    if (emoticons.isEmpty() && !hasMentions) {
        Text(
            text = text,
            style = style,
            modifier = modifier,
            maxLines = maxLines,
            overflow = overflow,
        )
        return
    }

    val emojiSize = remember(style.fontSize) {
        if (style.fontSize != TextUnit.Unspecified) style.fontSize * 1.2f else 18.sp
    }
    val inlineContent = remember(emoticons, emojiSize) {
        emoticons.mapValues { (phrase, _) ->
            InlineTextContent(
                placeholder = Placeholder(
                    width = emojiSize,
                    height = emojiSize,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                ),
            ) {
                AsyncImage(
                    model = emoticons[phrase],
                    contentDescription = phrase,
                    modifier = Modifier.size(emojiSize.value.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }

    val annotated = buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            val phrase = phrases.firstOrNull { text.startsWith(it, index) }
            if (phrase != null) {
                appendInlineContent(phrase, phrase)
                index += phrase.length
                continue
            }
            val mention = MentionPattern.find(text, index)
            if (mention != null && mention.range.first == index) {
                withStyle(SpanStyle(color = mentionColor)) {
                    append(mention.value)
                }
                index = mention.range.last + 1
                continue
            }
            append(text[index])
            index++
        }
    }

    Text(
        text = annotated,
        inlineContent = inlineContent,
        style = style,
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow,
    )
}
