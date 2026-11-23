package com.byagowi.persiancalendar.ui.localevents

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.byagowi.persiancalendar.entities.LocalEventCategory
import com.byagowi.persiancalendar.ui.icons.material.filled.calendarMonthIcon

val cakeIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "cake",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            stroke = null,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
            strokeLineMiter = 1f,
        ) {
            moveTo(12f, 6f)
            curveToRelative(1.11f, 0f, 2f, -0.9f, 2f, -2f)
            curveToRelative(0f, -0.38f, -0.1f, -0.73f, -0.29f, -1.03f)
            lineTo(12f, 1.13f)
            lineToRelative(-1.71f, 1.84f)
            curveToRelative(-0.19f, 0.3f, -0.29f, 0.65f, -0.29f, 1.03f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            close()
            moveTo(18f, 9f)
            horizontalLineToRelative(-5f)
            verticalLineTo(7f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(2f)
            horizontalLineTo(6f)
            curveToRelative(-1.66f, 0f, -3f, 1.34f, -3f, 3f)
            verticalLineToRelative(1f)
            horizontalLineToRelative(18f)
            verticalLineToRelative(-1f)
            curveToRelative(0f, -1.66f, -1.34f, -3f, -3f, -3f)
            close()
            moveTo(3f, 15f)
            verticalLineToRelative(4f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(14f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineToRelative(-4f)
            horizontalLineTo(3f)
            close()
        }
    }.build()
}

val favoriteIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "favorite",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            stroke = null,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
            strokeLineMiter = 1f,
        ) {
            moveTo(12f, 21.35f)
            lineToRelative(-1.45f, -1.32f)
            curveTo(5.4f, 15.36f, 2f, 12.28f, 2f, 8.5f)
            curveTo(2f, 5.42f, 4.42f, 3f, 7.5f, 3f)
            curveToRelative(1.74f, 0f, 3.41f, 0.81f, 4.5f, 2.09f)
            curveTo(13.09f, 3.81f, 14.76f, 3f, 16.5f, 3f)
            curveToRelative(3.08f, 0f, 5.5f, 2.42f, 5.5f, 5.5f)
            curveToRelative(0f, 3.78f, -3.4f, 6.86f, -8.55f, 11.54f)
            lineTo(12f, 21.35f)
            close()
        }
    }.build()
}

val notificationsIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "notifications",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            stroke = null,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
            strokeLineMiter = 1f,
        ) {
            moveTo(12f, 22f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            horizontalLineToRelative(-4f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            close()
            moveTo(18f, 16f)
            verticalLineToRelative(-5f)
            curveToRelative(0f, -3.07f, -1.63f, -5.64f, -4.5f, -6.32f)
            verticalLineTo(4f)
            curveToRelative(0f, -0.83f, -0.67f, -1.5f, -1.5f, -1.5f)
            reflectiveCurveToRelative(-1.5f, 0.67f, -1.5f, 1.5f)
            verticalLineToRelative(0.68f)
            curveTo(7.64f, 5.36f, 6f, 7.92f, 6f, 11f)
            verticalLineToRelative(5f)
            lineToRelative(-2f, 2f)
            verticalLineToRelative(1f)
            horizontalLineToRelative(16f)
            verticalLineToRelative(-1f)
            lineToRelative(-2f, -2f)
            close()
        }
    }.build()
}

fun categoryIcon(category: LocalEventCategory): ImageVector = when (category) {
    LocalEventCategory.BIRTHDAY -> cakeIcon
    LocalEventCategory.ANNIVERSARY -> favoriteIcon
    LocalEventCategory.REMINDER -> notificationsIcon
    LocalEventCategory.EVENT -> calendarMonthIcon
}
