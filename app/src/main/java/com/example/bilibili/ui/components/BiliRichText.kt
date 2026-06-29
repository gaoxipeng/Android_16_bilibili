package com.example.bilibili.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import com.example.bilibili.ui.theme.BiliBlue
import com.example.bilibili.util.BiliLinkTarget
import com.example.bilibili.util.BiliTextLinkParser

@Composable
fun BiliRichText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    linkColor: Color = BiliBlue,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onLinkClick: (BiliLinkTarget) -> Unit,
) {
    val segments = remember(text) { BiliTextLinkParser.parse(text) }
    val hasLinks = segments.any { it.target != null }
    if (!hasLinks) {
        Text(
            text = text,
            modifier = modifier,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = overflow,
        )
        return
    }

    val linkTargets = remember(segments) {
        segments.mapIndexedNotNull { index, segment ->
            segment.target?.let { index.toString() to it }
        }.toMap()
    }

    val annotated = buildAnnotatedString {
        segments.forEachIndexed { index, segment ->
            val target = segment.target
            if (target == null) {
                append(segment.text)
                return@forEachIndexed
            }
            val tag = index.toString()
            withLink(
                LinkAnnotation.Clickable(
                    tag = tag,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline,
                        ),
                    ),
                    linkInteractionListener = {
                        linkTargets[tag]?.let(onLinkClick)
                    },
                ),
            ) {
                append(segment.text)
            }
        }
    }

    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
    )
}
