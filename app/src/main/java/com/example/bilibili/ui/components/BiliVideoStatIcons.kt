package com.example.bilibili.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

private const val BiliVideoStatRingPath =
    "M12 4.74805C9.47358 4.74805 7.26788 4.87656 5.71068 5.00283C4.51218 5.10002 3.57814 6.01845 3.471 7.21015C3.35659 8.48281 3.25 10.1617 3.25 11.998C3.25 13.8344 3.35659 15.5133 3.471 16.7859C3.57814 17.9776 4.51218 18.8961 5.71068 18.9933C7.26789 19.1195 9.47358 19.248 12 19.248C14.5267 19.248 16.7325 19.1195 18.2898 18.9932C19.4881 18.896 20.422 17.9779 20.5291 16.7864C20.6435 15.5143 20.75 13.8357 20.75 11.998C20.75 10.1604 20.6435 8.48179 20.5291 7.20966C20.422 6.01821 19.4881 5.10005 18.2898 5.00287C16.7325 4.87658 14.5267 4.74805 12 4.74805zM5.62985 4.00611C7.20743 3.87818 9.44078 3.74805 12 3.74805C14.5595 3.74805 16.793 3.8782 18.3706 4.00614C20.0481 4.14219 21.3738 5.43787 21.5251 7.12011C21.6414 8.41388 21.75 10.1233 21.75 11.998C21.75 13.8728 21.6414 15.5822 21.5251 16.876C21.3738 18.5582 20.0481 19.8539 18.3706 19.99C16.793 20.1179 14.5595 20.248 12 20.248C9.44078 20.248 7.20743 20.1179 5.62985 19.99C3.95208 19.8539 2.62627 18.5579 2.47502 16.8755C2.35866 15.5812 2.25 13.8715 2.25 11.998C2.25 10.1246 2.35866 8.41486 2.47502 7.12061C2.62627 5.43818 3.95208 4.14216 5.62985 4.00611z"

private fun ImageVector.Builder.addBiliVideoStatRing() {
    addPath(
        pathData = PathParser().parsePathString(BiliVideoStatRingPath).toNodes(),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1f,
        strokeLineJoin = StrokeJoin.Round,
    )
}

val BiliVideoPlayCountIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "BiliVideoPlayCount",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addBiliVideoStatRing()
        addPath(
            fill = SolidColor(Color.Black),
            pathData = PathParser().parsePathString(
                "M14.7138 10.96875C15.50765 11.4271 15.50765 12.573 14.71375 13.0313L11.5362 14.8659C10.74235 15.3242 9.75 14.7513 9.75001 13.8346L9.75001 10.1655C9.75001 9.24881 10.74235 8.67587 11.5362 9.13422L14.7138 10.96875z",
            ).toNodes(),
        )
    }.build()
}

val BiliVideoDanmakuCountIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "BiliVideoDanmakuCount",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addBiliVideoStatRing()
        addPath(
            fill = SolidColor(Color.Black),
            pathData = PathParser().parsePathString(
                "M15.875 10.75L9.875 10.75C9.46079 10.75 9.125 10.4142 9.125 10C9.125 9.58579 9.46079 9.25 9.875 9.25L15.875 9.25C16.2892 9.25 16.625 9.58579 16.625 10C16.625 10.4142 16.2892 10.75 15.875 10.75z",
            ).toNodes(),
        )
        addPath(
            fill = SolidColor(Color.Black),
            pathData = PathParser().parsePathString(
                "M17.375 14.75L11.375 14.75C10.9608 14.75 10.625 14.4142 10.625 14C10.625 13.5858 10.9608 13.25 11.375 13.25L17.375 13.25C17.7892 13.25 18.125 13.5858 18.125 14C18.125 14.4142 17.7892 14.75 17.375 14.75z",
            ).toNodes(),
        )
        addPath(
            fill = SolidColor(Color.Black),
            pathData = PathParser().parsePathString(
                "M7.875 10C7.875 10.4142 7.53921 10.75 7.125 10.75L6.625 10.75C6.21079 10.75 5.875 10.4142 5.875 10C5.875 9.58579 6.21079 9.25 6.625 9.25L7.125 9.25C7.53921 9.25 7.875 9.58579 7.875 10z",
            ).toNodes(),
        )
        addPath(
            fill = SolidColor(Color.Black),
            pathData = PathParser().parsePathString(
                "M9.375 14C9.375 14.4142 9.03921 14.75 8.625 14.75L8.125 14.75C7.71079 14.75 7.375 14.4142 7.375 14C7.375 13.5858 7.71079 13.25 8.125 13.25L8.625 13.25C9.03921 13.25 9.375 13.5858 9.375 14z",
            ).toNodes(),
        )
    }.build()
}
