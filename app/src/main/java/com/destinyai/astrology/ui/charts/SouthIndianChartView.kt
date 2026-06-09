package com.destinyai.astrology.ui.charts

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.GoldLight

enum class ChartType { D1, D9 }

@Composable
fun SouthIndianChartView(
    chartData: ChartData,
    chartType: ChartType = ChartType.D1,
    ascendantSign: String?,
    personName: String = "",
    gridSizeDp: Float = 340f,
) {
    Box(
        modifier = Modifier
            .size(gridSizeDp.dp)
            .padding(8.dp)
            .shadow(
                elevation = 4.dp,
                ambientColor = Gold.copy(alpha = 0.1f),
                spotColor = Gold.copy(alpha = 0.1f),
            )
            .shadow(
                elevation = 2.dp,
                ambientColor = Gold.copy(alpha = 0.2f),
                spotColor = Gold.copy(alpha = 0.2f),
            ),
    ) {
        SouthIndianCanvas(
            chartData = chartData,
            chartType = chartType,
            ascendantSign = ascendantSign,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun SouthIndianCanvas(
    chartData: ChartData,
    chartType: ChartType,
    ascendantSign: String?,
    modifier: Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val goldColor = Gold

    // Animated 3-stop gradient offset for parity with iOS premium grid stroke
    val transition = rememberInfiniteTransition(label = "south-grid-gradient")
    val gradientOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "gradient-offset",
    )

    // Premium gradient text brush (mirrors iOS AppTheme.Colors.premiumGradient)
    val premiumGradient = Brush.linearGradient(
        colors = listOf(Gold, GoldLight, Gold),
    )
    val nonAscendantGradient = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.5f),
            Color.White.copy(alpha = 0.3f),
        ),
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cell = w / 4f

        val medPx = 1.5f
        val thinPx = 1.0f

        // Animated gold gradient brush — 3 stops, offset shifts for shimmer parity with iOS
        val animatedShift = gradientOffset * w
        val gridGradient = Brush.linearGradient(
            colors = listOf(Gold, Gold.copy(alpha = 0.6f), Gold),
            start = Offset(animatedShift, 0f),
            end = Offset(animatedShift + w, h),
        )

        // Outer double border
        drawRect(brush = gridGradient, size = size, style = Stroke(medPx))
        drawRect(
            brush = gridGradient,
            topLeft = Offset(4f, 4f),
            size = Size(w - 8f, h - 8f),
            style = Stroke(thinPx),
        )

        // Horizontal lines: full-width top (y=cell) and bottom (y=3*cell)
        drawLine(brush = gridGradient, start = Offset(0f, cell), end = Offset(w, cell), strokeWidth = thinPx)
        drawLine(brush = gridGradient, start = Offset(0f, cell * 3), end = Offset(w, cell * 3), strokeWidth = thinPx)
        // Middle horizontal: only left and right segments
        drawLine(brush = gridGradient, start = Offset(0f, cell * 2), end = Offset(cell, cell * 2), strokeWidth = thinPx)
        drawLine(brush = gridGradient, start = Offset(cell * 3, cell * 2), end = Offset(w, cell * 2), strokeWidth = thinPx)

        // Vertical lines: full-height left (x=cell) and right (x=3*cell)
        drawLine(brush = gridGradient, start = Offset(cell, 0f), end = Offset(cell, h), strokeWidth = thinPx)
        drawLine(brush = gridGradient, start = Offset(cell * 3, 0f), end = Offset(cell * 3, h), strokeWidth = thinPx)
        // Middle vertical: only top and bottom segments
        drawLine(brush = gridGradient, start = Offset(cell * 2, 0f), end = Offset(cell * 2, cell), strokeWidth = thinPx)
        drawLine(brush = gridGradient, start = Offset(cell * 2, cell * 3), end = Offset(cell * 2, h), strokeWidth = thinPx)

        // Center square border
        drawRect(
            brush = gridGradient,
            topLeft = Offset(cell, cell),
            size = Size(cell * 2, cell * 2),
            style = Stroke(medPx),
        )

        // Cell content: sign label + planets
        for (row in 0..3) {
            for (col in 0..3) {
                val sign = ChartConstants.southIndianLayout[row][col] ?: continue
                val cx = cell * col + cell / 2f
                val cy = cell * row + cell / 2f
                val isAsc = sign == ascendantSign

                // Sign label — gradient brush for parity with iOS
                val signStyle = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = if (isAsc) FontWeight.Bold else FontWeight.SemiBold,
                    brush = if (isAsc) premiumGradient else nonAscendantGradient,
                )
                val signLayout = textMeasurer.measure(sign, signStyle)
                val planets = planetsInSign(sign, chartData, chartType)

                val totalHeight = signLayout.size.height + if (planets.isNotEmpty()) 4 + 12 else 0
                val startY = cy - totalHeight / 2f

                drawText(
                    textMeasurer = textMeasurer,
                    text = sign,
                    style = signStyle,
                    topLeft = Offset(cx - signLayout.size.width / 2f, startY),
                )

                if (planets.isNotEmpty()) {
                    val planetShadow = Shadow(
                        color = Color.Black.copy(alpha = 0.5f),
                        offset = Offset(0f, 1f),
                        blurRadius = 1f,
                    )
                    val planetStyle = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        shadow = planetShadow,
                    )
                    if (planets.size <= 3) {
                        val joined = planets.joinToString(" ")
                        val pl = textMeasurer.measure(joined, planetStyle)
                        drawText(
                            textMeasurer = textMeasurer,
                            text = joined,
                            style = planetStyle,
                            topLeft = Offset(cx - pl.size.width / 2f, startY + signLayout.size.height + 4),
                        )
                    } else {
                        val row1 = planets.take(3).joinToString(" ")
                        val row2 = planets.drop(3).joinToString(" ")
                        val smallStyle = TextStyle(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            shadow = planetShadow,
                        )
                        val pl1 = textMeasurer.measure(row1, smallStyle)
                        val pl2 = textMeasurer.measure(row2, smallStyle)
                        val yStart = startY + signLayout.size.height + 2
                        drawText(textMeasurer = textMeasurer, text = row1, style = smallStyle, topLeft = Offset(cx - pl1.size.width / 2f, yStart))
                        drawText(textMeasurer = textMeasurer, text = row2, style = smallStyle, topLeft = Offset(cx - pl2.size.width / 2f, yStart + pl1.size.height + 2))
                    }
                }
            }
        }
    }
}

private fun planetsInSign(sign: String, chartData: ChartData, chartType: ChartType): List<String> =
    when (chartType) {
        ChartType.D1 -> chartData.d1.filter { it.value.sign == sign }
            .map { ChartConstants.planetShortCodes[it.key] ?: it.key.take(2) }
        ChartType.D9 -> chartData.d9.filter { it.value.sign == sign }
            .map { ChartConstants.planetShortCodes[it.key] ?: it.key.take(2) }
    }
