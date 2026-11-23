package com.example.bilibili.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.bilibili.ui.theme.BiliBlue
import com.example.bilibili.ui.theme.BiliPink
import com.example.bilibili.util.BiliLinkTarget
import com.example.bilibili.util.BiliTextLinkParser

private val MentionPattern = Regex("""@([^\s@:：,，。！？!?、；;]+)""")
private val ReplyMentionPattern = Regex("""^回复\s+@(.+?)\s*[:：]""")

val LocalBiliSyncedEmoticons = compositionLocalOf<Map<String, String>> { emptyMap() }

private data class CommentLinkRange(
    val start: Int,
    val end: Int,
    val target: BiliLinkTarget,
)

private fun commentLinkRanges(text: String): List<CommentLinkRange> {
    val segments = BiliTextLinkParser.parse(text)
    val ranges = mutableListOf<CommentLinkRange>()
    var cursor = 0
    for (segment in segments) {
        val start = cursor
        cursor += segment.text.length
        val target = segment.target ?: continue
        ranges += CommentLinkRange(start = start, end = cursor, target = target)
    }
    return ranges
}

@Composable
fun BiliCommentText(
    text: String,
    emoticons: Map<String, String>,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    mentionColor: Color = BiliPink,
    linkColor: Color = BiliBlue,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onLinkClick: ((BiliLinkTarget) -> Unit)? = null,
    mentionMids: Map<String, Long> = emptyMap(),
) {
    val syncedEmoticons = LocalBiliSyncedEmoticons.current
    val resolvedEmoticons = remember(emoticons, syncedEmoticons) {
        syncedEmoticons + emoticons
    }
    val phrases = remember(resolvedEmoticons) { resolvedEmoticons.keys.sortedByDescending { it.length } }
    val hasMentions = remember(text) { MentionPattern.containsMatchIn(text) }
    val linkRanges = remember(text) { commentLinkRanges(text) }
    val hasLinks = linkRanges.isNotEmpty()
    val replyMention = remember(text) { ReplyMentionPattern.find(text) }
    if (resolvedEmoticons.isEmpty() && !hasMentions && !hasLinks) {
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
    val inlineContent = remember(resolvedEmoticons, emojiSize) {
        resolvedEmoticons.mapValues { (phrase, _) ->
            InlineTextContent(
                placeholder = Placeholder(
                    width = emojiSize,
                    height = emojiSize,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                ),
            ) {
                AsyncImage(
                    model = resolvedEmoticons[phrase],
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
            if (text[index] == '@') {
                val replyName = replyMention?.groups?.get(1)
                    ?.takeIf { it.range.first == index + 1 }?.value?.trimEnd()
                val knownName = mentionMids.keys
                    .filter { text.startsWith(it, index + 1) }
                    .filter { name ->
                        val next = text.getOrNull(index + 1 + name.length)
                        next == null || next.isWhitespace() || next in "@:：,，。！？!?、；;"
                    }.maxByOrNull { it.length }
                val name = replyName ?: knownName ?: MentionPattern.matchAt(text, index)?.groupValues?.get(1)
                if (!name.isNullOrEmpty()) {
                    val mid = mentionMids[name]
                    if (onLinkClick != null) {
                        val target = if (mid != null) BiliLinkTarget.UserSpace(mid)
                            else BiliLinkTarget.UserMention(name)
                        withLink(LinkAnnotation.Clickable(
                            tag = "mention-$index",
                            styles = TextLinkStyles(style = SpanStyle(color = mentionColor)),
                            linkInteractionListener = { onLinkClick(target) },
                        )) { append("@$name") }
                    } else {
                        withStyle(SpanStyle(color = mentionColor)) { append("@$name") }
                    }
                    index += name.length + 1
                    continue
                }
            }
            val linkRange = linkRanges.firstOrNull { it.start == index }
            if (linkRange != null) {
                val linkText = text.substring(linkRange.start, linkRange.end)
                if (onLinkClick != null) {
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = "link-${linkRange.start}",
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline,
                                ),
                            ),
                            linkInteractionListener = {
                                onLinkClick(linkRange.target)
                            },
                        ),
                    ) {
                        append(linkText)
                    }
                } else {
                    withStyle(
                        SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline,
                        ),
                    ) {
                        append(linkText)
                    }
                }
                index = linkRange.end
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
