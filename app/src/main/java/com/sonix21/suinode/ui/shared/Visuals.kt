package com.sonix21.suinode.ui.shared

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.math.cos
import kotlin.math.sin

/** Half-donut gauge like the panel's Gauge tile. */
@Composable
fun Gauge(percent: Float, label: String, subLabel: String, color: Color, modifier: Modifier = Modifier) {
    val track = if (color == Color.Unspecified) Color.Gray else color.copy(alpha = 0.18f)
    Canvas(modifier.height(74.dp).fillMaxWidth()) {
        val strokeW = 15f
        val r = size.minDimension / 2f - strokeW / 2f
        val c = Offset(size.width / 2f, size.height - 6.dp.toPx() / 2f + strokeW)
        drawArc(
            color = track,
            startAngle = 180f, sweepAngle = 180f, useCenter = false,
            style = Stroke(strokeW, cap = StrokeCap.Round),
            topLeft = Offset(c.x - r, c.y - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
        )
        drawArc(
            brush = Brush.linearGradient(listOf(color.copy(alpha = 0.75f), color)),
            startAngle = 180f,
            sweepAngle = (percent.coerceIn(0f, 100f) / 100f) * 180f,
            useCenter = false,
            style = Stroke(strokeW, cap = StrokeCap.Round),
            topLeft = Offset(c.x - r, c.y - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
        )
    }
}

/** Simple multi-series line chart (canvas only). */
@Composable
fun LineChart(
    series: List<Pair<List<Float>, Color>>,
    modifier: Modifier = Modifier,
    maxOverride: Float? = null,
) {
    Canvas(modifier) {
        val allValues = series.flatMap { it.first }.filter { it.isFinite() }
        if (allValues.isEmpty()) return@Canvas
        val maxV = maxOverride ?: (allValues.maxOrNull() ?: 1f).coerceAtLeast(0.0001f)

        // grid lines
        for (i in 1..3) {
            val y = size.height * i / 4f
            drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, y), Offset(size.width, y), 1.5f)
        }

        series.forEach { (values, color) ->
            if (values.size < 2) return@forEach
            val stepX = size.width / (values.size - 1).coerceAtLeast(1)
            fun pt(i: Int): Offset =
                Offset(i * stepX, size.height - (values[i].coerceIn(0f, maxV) / maxV) * (size.height - 8f))

            val fill = Path().apply {
                moveTo(0f, size.height)
                values.indices.forEach { lineTo(pt(it).x, pt(it).y) }
                lineTo((values.size - 1) * stepX, size.height)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent)))
            val line = Path().apply { values.indices.forEach { if (it == 0) moveTo(pt(it).x, pt(it).y) else lineTo(pt(it).x, pt(it).y) } }
            drawPath(line, color, style = Stroke(3.2f, cap = StrokeCap.Round))
        }
    }
}

object Qr {
    fun bitmap(content: String, sizePx: Int = 512): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8")
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bmp.setPixel(x, y, if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        return bmp
    }
}

@Composable
fun QrImage(content: String, sizeDp: Dp = 240.dp, modifier: Modifier = Modifier) {
    val bmp = remember(content) { runCatching { Qr.bitmap(content) }.getOrNull() }
    Box(modifier) {
        if (bmp != null) {
            Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.size(sizeDp))
        }
    }
}
