package com.playly.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.random.Random

private data class Landscape(val sky: Color, val horizon: Color, val moon: Color, val far: Color, val near: Color, val detail: Color)

private val landscapes = listOf(
    Landscape(Color(0xFF493378), Color(0xFFD06D81), Color(0xFFFFC38E), Color(0xFF4F416F), Color(0xFF23243F), Color(0xFFFF9E67)),
    Landscape(Color(0xFF102853), Color(0xFF536EAA), Color(0xFFB8D6FF), Color(0xFF293F76), Color(0xFF182744), Color(0xFFFFB368)),
    Landscape(Color(0xFF173B46), Color(0xFF76AA6D), Color(0xFFDAEA9D), Color(0xFF325F52), Color(0xFF163C36), Color(0xFFBEE68F)),
)

@Composable
internal fun CoverArt(index: Int, modifier: Modifier = Modifier) {
    val scene = Math.floorMod(index, landscapes.size)
    val palette = landscapes[scene]
    Canvas(modifier) {
        val random = Random(331 + scene)
        val scale = size.width / 160f
        val height = size.height / scale
        drawRect(Brush.verticalGradient(listOf(palette.sky, palette.horizon)))
        withTransform({ scale(scale, scale, Offset.Zero) }) {
            repeat(40) {
                val x = random.nextInt(0, 160)
                val y = random.nextInt(1, (height * .63f).toInt().coerceAtLeast(2))
                pixel(palette.moon.copy(alpha = .4f), x, y.toFloat(), random.nextInt(1, 4), 1f)
            }
            val moonY = height * .28f
            for (row in -12..12) {
                val width = kotlin.math.sqrt((169 - row * row).toFloat()).toInt()
                pixel(palette.moon, 114 - width, moonY + row, width * 2, 1f)
            }
            if (scene == 0) {
                for (x in -3..160 step 9) {
                    val top = height - random.nextInt(17, 40)
                    pixel(palette.far, x, top, 8, height - top)
                    for (y in 4..(height - top).toInt() step 5) {
                        if (random.nextBoolean()) pixel(palette.detail.copy(alpha = .45f), x + 2, top + y, 2, 2f)
                    }
                }
            } else {
                for (peak in 0..5) {
                    val center = 8 + peak * 30
                    val top = height - random.nextInt(28, 50)
                    for (i in 0 until (height - top).toInt()) {
                        val halfWidth = if (scene == 1) i else (i % 10 + i / 3)
                        pixel(if (scene == 1 && i < 8) palette.moon.copy(alpha = .78f) else palette.far,
                            center - halfWidth, top + i, halfWidth * 2 + 1, 1f)
                    }
                }
            }
            for (x in -4..160 step 19) {
                val top = height - random.nextInt(5, 18)
                pixel(palette.near, x, top, 19, height - top)
                pixel(palette.far, x, top, 18, 2f)
                pixel(palette.detail, x + 5, top + 4, 3, 2f)
            }
            val ground = height - 9
            pixel(palette.near, 45, ground - 7, 27, 16f)
            pixel(palette.far, 45, ground - 8, 28, 2f)
            when (scene) {
                0 -> {
                    pixel(Color(0xFF849BC4), 53, ground - 25, 12, 8f)
                    pixel(Color(0xFF262840), 55, ground - 23, 9, 4f)
                    pixel(Color(0xFFBFFDFF), 57, ground - 22, 2, 2f)
                    pixel(Color(0xFFBFFDFF), 61, ground - 22, 2, 2f)
                    pixel(Color(0xFF546C9C), 56, ground - 17, 7, 6f)
                    pixel(Color(0xFFA3B7D7), 55, ground - 11, 3, 3f)
                    pixel(Color(0xFFA3B7D7), 61, ground - 11, 3, 3f)
                }
                1 -> {
                    pixel(Color(0xFFBBCDEE), 50, ground - 20, 14, 11f)
                    pixel(Color(0xFF708CA7), 64, ground - 17, 8, 8f)
                    pixel(Color(0xFF2A5071), 65, ground - 16, 4, 4f)
                    pixel(palette.detail, 70, ground - 13, 2, 2f)
                    pixel(palette.near, 53, ground - 11, 4, 4f)
                    pixel(palette.near, 66, ground - 11, 4, 4f)
                }
                else -> {
                    pixel(Color(0xFFB38364), 54, ground - 19, 14, 11f)
                    pixel(palette.moon, 54, ground - 19, 14, 2f)
                    pixel(Color(0xFFDAEA9D), 60, ground - 16, 3, 4f)
                }
            }
        }
    }
}

private fun DrawScope.pixel(color: Color, x: Int, y: Float, width: Int, height: Float) {
    drawRect(color, Offset(x.toFloat(), y), Size(width.toFloat(), height))
}
