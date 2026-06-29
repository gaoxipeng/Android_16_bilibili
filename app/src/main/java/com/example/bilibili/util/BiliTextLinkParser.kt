package com.example.bilibili.util

data class BiliTextSegment(
    val text: String,
    val target: BiliLinkTarget? = null,
)

sealed interface BiliLinkTarget {
    data class Video(
        val bvid: String = "",
        val aid: Long = 0L,
        val partPage: Int = 0,
    ) : BiliLinkTarget

    data class UserSpace(
        val mid: Long,
    ) : BiliLinkTarget

    data class External(
        val url: String,
    ) : BiliLinkTarget
}

object BiliTextLinkParser {
    private val UrlPattern = Regex("""https?://[^\s<>"\]）】（\[\]]+""", RegexOption.IGNORE_CASE)
    private val BvPattern = Regex("""(?<![/\w])BV[\w]{10}(?![\w])""", RegexOption.IGNORE_CASE)
    private val AvPattern = Regex("""(?<![/\w])av(\d+)(?![\w])""", RegexOption.IGNORE_CASE)

    private val SpaceMidPattern = Regex(
        """(?:space\.bilibili\.com/|(?:www\.|m\.)?bilibili\.com/space/)(\d{1,18})""",
        RegexOption.IGNORE_CASE,
    )
    private val NumericSpacePattern = Regex(
        """(?:www\.|m\.)?bilibili\.com/(\d{2,18})(?:[/?#]|$)""",
        RegexOption.IGNORE_CASE,
    )
    private val VideoBvPattern = Regex(
        """(?:www\.|m\.)?bilibili\.com/video/(BV[\w]{10})""",
        RegexOption.IGNORE_CASE,
    )
    private val VideoAvPattern = Regex(
        """(?:www\.|m\.)?bilibili\.com/video/av(\d+)""",
        RegexOption.IGNORE_CASE,
    )
    private val AppVideoBvPattern = Regex(
        """bilibili://video/(BV[\w]{10})""",
        RegexOption.IGNORE_CASE,
    )
    private val PageQueryPattern = Regex("""(?:[?&])p=(\d+)""", RegexOption.IGNORE_CASE)

    fun parse(text: String): List<BiliTextSegment> {
        if (text.isBlank()) return listOf(BiliTextSegment(text))

        val matches = buildList {
            UrlPattern.findAll(text).forEach { match ->
                add(RawMatch(match.range.first, match.range.last + 1, match.value, MatchKind.Url))
            }
            BvPattern.findAll(text).forEach { match ->
                add(RawMatch(match.range.first, match.range.last + 1, match.value, MatchKind.Bvid))
            }
            AvPattern.findAll(text).forEach { match ->
                add(
                    RawMatch(
                        start = match.range.first,
                        end = match.range.last + 1,
                        text = match.value,
                        kind = MatchKind.Avid,
                        aid = match.groupValues[1].toLongOrNull() ?: 0L,
                    ),
                )
            }
        }.sortedWith(compareBy({ it.start }, { -(it.end - it.start) }))

        val filtered = mutableListOf<RawMatch>()
        var occupiedUntil = 0
        for (match in matches) {
            if (match.start < occupiedUntil) continue
            filtered += match
            occupiedUntil = match.end
        }

        if (filtered.isEmpty()) return listOf(BiliTextSegment(text))

        val segments = mutableListOf<BiliTextSegment>()
        var cursor = 0
        for (match in filtered) {
            if (match.start > cursor) {
                segments += BiliTextSegment(text.substring(cursor, match.start))
            }
            val display = text.substring(match.start, match.end)
            val target = when (match.kind) {
                MatchKind.Url -> classifyUrl(display)
                MatchKind.Bvid -> BiliLinkTarget.Video(bvid = normalizeBvid(display))
                MatchKind.Avid -> BiliLinkTarget.Video(aid = match.aid)
            }
            segments += BiliTextSegment(text = display, target = target)
            cursor = match.end
        }
        if (cursor < text.length) {
            segments += BiliTextSegment(text.substring(cursor))
        }
        return segments
    }

    private fun classifyUrl(rawUrl: String): BiliLinkTarget {
        val url = trimLinkSuffix(rawUrl)
        SpaceMidPattern.find(url)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { mid ->
            if (mid > 0L) return BiliLinkTarget.UserSpace(mid)
        }
        NumericSpacePattern.find(url)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { mid ->
            if (mid > 0L) return BiliLinkTarget.UserSpace(mid)
        }
        AppVideoBvPattern.find(url)?.groupValues?.getOrNull(1)?.let { bvid ->
            return BiliLinkTarget.Video(
                bvid = normalizeBvid(bvid),
                partPage = parsePartPage(url),
            )
        }
        VideoBvPattern.find(url)?.groupValues?.getOrNull(1)?.let { bvid ->
            return BiliLinkTarget.Video(
                bvid = normalizeBvid(bvid),
                partPage = parsePartPage(url),
            )
        }
        VideoAvPattern.find(url)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { aid ->
            if (aid > 0L) {
                return BiliLinkTarget.Video(aid = aid, partPage = parsePartPage(url))
            }
        }
        return BiliLinkTarget.External(url)
    }

    private fun parsePartPage(url: String): Int =
        PageQueryPattern.find(url)?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 } ?: 0

    private fun normalizeBvid(raw: String): String =
        Regex("""BV[\w]{10}""", RegexOption.IGNORE_CASE).find(raw)?.value.orEmpty()

    private fun trimLinkSuffix(url: String): String =
        url.trimEnd(
            ')', '）', ']', '】', '.', ',', '，', '。', ';', '；',
            '!', '?', '？', '"', '\'', '…', '*',
        )

    private enum class MatchKind {
        Url,
        Bvid,
        Avid,
    }

    private data class RawMatch(
        val start: Int,
        val end: Int,
        val text: String,
        val kind: MatchKind,
        val aid: Long = 0L,
    )
}
