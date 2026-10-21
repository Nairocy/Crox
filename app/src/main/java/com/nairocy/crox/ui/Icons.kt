package com.nairocy.crox.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private fun croxIcon(name: String, size: Float, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = size.dp,
        defaultHeight = size.dp,
        viewportWidth = size,
        viewportHeight = size,
    ).apply(block).build()

val IcoImage: ImageVector by lazy {
    croxIcon("image", 24f) {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.7f,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(3.2f, 4.6f)
            horizontalLineTo(20.8f)
            verticalLineTo(19.4f)
            horizontalLineTo(3.2f)
            close()
            moveTo(10.3f, 9.8f)
            arcToRelative(1.7f, 1.7f, 0f, true, true, -3.4f, 0f)
            arcToRelative(1.7f, 1.7f, 0f, true, true, 3.4f, 0f)
            close()
            moveTo(4f, 17.5f)
            lineTo(9.2f, 13.1f)
            lineTo(13.2f, 16.4f)
            lineTo(16.3f, 13.8f)
            lineTo(20.8f, 17.5f)
        }
    }
}

val IcoPlus: ImageVector by lazy {
    croxIcon("plus", 16f) {
        path(stroke = SolidColor(Color.White), strokeLineWidth = 1.8f) {
            moveTo(8f, 2f)
            verticalLineTo(14f)
            moveTo(2f, 8f)
            horizontalLineTo(14f)
        }
    }
}

val IcoClose: ImageVector by lazy {
    croxIcon("close", 16f) {
        path(stroke = SolidColor(Color.White), strokeLineWidth = 1.8f) {
            moveTo(3f, 3f)
            lineTo(13f, 13f)
            moveTo(13f, 3f)
            lineTo(3f, 13f)
        }
    }
}

val IcoBack: ImageVector by lazy {
    croxIcon("back", 16f) {
        path(stroke = SolidColor(Color.White), strokeLineWidth = 1.8f) {
            moveTo(10f, 3f)
            lineTo(5f, 8f)
            lineTo(10f, 13f)
        }
    }
}

val IcoSend: ImageVector by lazy {
    croxIcon("send", 24f) {
        path(stroke = SolidColor(Color.White), strokeLineWidth = 2f) {
            moveTo(3f, 12f)
            horizontalLineTo(19f)
            moveTo(13f, 5.5f)
            lineTo(19.5f, 12f)
            lineTo(13f, 18.5f)
        }
    }
}

val IcoStop: ImageVector by lazy {
    croxIcon("stop", 16f) {
        path(fill = SolidColor(Color.White)) {
            moveTo(2f, 2f)
            horizontalLineTo(14f)
            verticalLineTo(14f)
            horizontalLineTo(2f)
            close()
        }
    }
}

val IcoSwap: ImageVector by lazy {
    croxIcon("swap", 12f) {
        path(stroke = SolidColor(Color.White), strokeLineWidth = 1.4f) {
            moveTo(1.5f, 4f)
            horizontalLineTo(9.5f)
            moveTo(7.5f, 2f)
            lineTo(9.5f, 4f)
            lineTo(7.5f, 6f)
            moveTo(10.5f, 8f)
            horizontalLineTo(2.5f)
            moveTo(4.5f, 6f)
            lineTo(2.5f, 8f)
            lineTo(4.5f, 10f)
        }
    }
}

val IcoGear: ImageVector by lazy {
    croxIcon("gear", 24f) {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.6f,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(21.3f, 10.4f)
            lineTo(21.3f, 13.6f)
            lineTo(19.4f, 13.3f)
            lineTo(18.1f, 16.3f)
            lineTo(19.7f, 17.4f)
            lineTo(17.4f, 19.7f)
            lineTo(16.3f, 18.1f)
            lineTo(13.3f, 19.4f)
            lineTo(13.6f, 21.3f)
            lineTo(10.4f, 21.3f)
            lineTo(10.7f, 19.4f)
            lineTo(7.7f, 18.1f)
            lineTo(6.6f, 19.7f)
            lineTo(4.3f, 17.4f)
            lineTo(5.9f, 16.3f)
            lineTo(4.6f, 13.3f)
            lineTo(2.7f, 13.6f)
            lineTo(2.7f, 10.4f)
            lineTo(4.6f, 10.7f)
            lineTo(5.9f, 7.7f)
            lineTo(4.3f, 6.6f)
            lineTo(6.6f, 4.3f)
            lineTo(7.7f, 5.9f)
            lineTo(10.7f, 4.6f)
            lineTo(10.4f, 2.7f)
            lineTo(13.6f, 2.7f)
            lineTo(13.3f, 4.6f)
            lineTo(16.3f, 5.9f)
            lineTo(17.4f, 4.3f)
            lineTo(19.7f, 6.6f)
            lineTo(18.1f, 7.7f)
            lineTo(19.4f, 10.7f)
            close()
            moveTo(12f, 8.9f)
            arcToRelative(3.1f, 3.1f, 0f, true, true, 0f, 6.2f)
            arcToRelative(3.1f, 3.1f, 0f, true, true, 0f, -6.2f)
            close()
        }
    }
}

val IcoReasoning: ImageVector by lazy {
    croxIcon("reasoning", 12f) {
        path(stroke = SolidColor(Color.White), strokeLineWidth = 1.6f) {
            moveTo(1f, 2.5f)
            horizontalLineTo(11f)
            moveTo(1f, 6f)
            horizontalLineTo(11f)
            moveTo(1f, 9.5f)
            horizontalLineTo(7f)
        }
    }
}

val IcoChevron: ImageVector by lazy {
    croxIcon("chevron", 12f) {
        path(stroke = SolidColor(Color.White), strokeLineWidth = 1.8f) {
            moveTo(4f, 2f)
            lineTo(8f, 6f)
            lineTo(4f, 10f)
        }
    }
}

val IcoChevronDown: ImageVector by lazy {
    croxIcon("chevron-down", 12f) {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(2.5f, 4.5f)
            lineTo(6f, 8f)
            lineTo(9.5f, 4.5f)
        }
    }
}

/** 复制：双矩形叠影（后板 L 形 + 前板矩形）。 */
val IcoCopy: ImageVector by lazy {
    croxIcon("copy", 24f) {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.7f,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(8.4f, 4.2f)
            horizontalLineTo(19.8f)
            verticalLineTo(15.6f)
            moveTo(15.6f, 8.4f)
            horizontalLineTo(4.2f)
            verticalLineTo(19.8f)
            horizontalLineTo(15.6f)
            close()
        }
    }
}

/** 重答：顺时针环形箭头（缺口 + 箭头头部）。 */
val IcoRegen: ImageVector by lazy {
    croxIcon("regen", 24f) {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(21.5f, 2.5f)
            verticalLineTo(8.5f)
            horizontalLineTo(15.5f)
            moveTo(18.99f, 13.5f)
            arcToRelative(9f, 9f, 0f, true, true, -2.12f, -9.36f)
            lineTo(21.5f, 8.5f)
        }
    }
}
