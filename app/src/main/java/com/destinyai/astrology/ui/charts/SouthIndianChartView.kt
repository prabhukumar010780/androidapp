package com.destinyai.astrology.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.ui.theme.Gold

enum class ChartType { D1, D9 }

@Composable
fun SouthIndianChartView(
    chartData: ChartData,
    chartType: ChartType = ChartType.D1,
    ascendantSign: String?,
    gridSizeDp: Float = 340f,
) {
    Box(
        modifier = Modifier
            .size(gridSizeDp.dp)
            .padding(8.dp),
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

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cell = w / 4f

        val medPx = 1.5f
        val thinPx = 1.0f

        // Outer double border
        drawRect(color = goldColor, size = size, style = Stroke(medPx))
        drawRect(
            color = goldColor.copy(alpha = 0.6f),
            topLeft = Offset(4f, 4f),
            size = Size(w - 8f, h - 8f),
            style = Stroke(thinPx),
        )

        // Horizontal lines: full-width top (y=cell) and bottom (y=3*cell)
        drawLine(goldColor, Offset(0f, cell), Offset(w, cell), thinPx)
        drawLine(goldColor, Offset(0f, cell * 3), Offset(w, cell * 3), thinPx)
        // Middle horizontal: only left and right segments
        drawLine(goldColor, Offset(0f, cell * 2), Offset(cell, cell * 2), thinPx)
        drawLine(goldColor, Offset(cell * 3, cell * 2), Offset(w, cell * 2), thinPx)

        // Vertical lines: full-height left (x=cell) and right (x=3*cell)
        drawLine(goldColor, Offset(cell, 0f), Offset(cell, h), thinPx)
        drawLine(goldColor, Offset(cell * 3, 0f), Offset(cell * 3, h), thinPx)
        // Middle vertical: only top and bottom segments
        drawLine(goldColor, Offset(cell * 2, 0f), Offset(cell * 2, cell), thinPx)
        drawLine(goldColor, Offset(cell * 2, cell * 3), Offset(cell * 2, h), thinPx)

        // Center square border
        drawRect(
            color = goldColor,
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

                // Sign label
                val signStyle = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = if (isAsc) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isAsc) goldColor else Color.White.copy(alpha = 0.5f),
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
                    val planetStyle = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
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
                        val smallStyle = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
