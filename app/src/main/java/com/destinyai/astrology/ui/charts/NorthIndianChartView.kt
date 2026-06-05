package com.destinyai.astrology.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.ui.theme.Gold

private val goldColor = Gold

@Composable
fun NorthIndianChartView(
    chartData: ChartData,
    ascendantSign: String?,
    gridSizeDp: Float = 340f,
) {
    Box(
        modifier = Modifier
            .size(gridSizeDp.dp)
            .padding(8.dp),
    ) {
        NorthIndianCanvas(
            chartData = chartData,
            ascendantSign = ascendantSign,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun NorthIndianCanvas(
    chartData: ChartData,
    ascendantSign: String?,
    modifier: Modifier,
) {
    val ascNum = ascendantSign?.let { ChartConstants.signNumbers[it] } ?: 1
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val goldGradient = Brush.linearGradient(
            colors = listOf(goldColor, goldColor.copy(alpha = 0.6f), goldColor),
            start = Offset(0f, 0f),
            end = Offset(w, h),
        )

        val medPx = 1.5f
        val thinPx = 1.0f

        // 1. Double outer border
        drawRect(color = goldColor, size = size, style = androidx.compose.ui.graphics.drawscope.Stroke(medPx))
        drawRect(
            color = goldColor.copy(alpha = 0.6f),
            topLeft = Offset(4f, 4f),
            size = androidx.compose.ui.geometry.Size(w - 8f, h - 8f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(thinPx),
        )

        // 2. Diagonals
        drawLine(goldColor, Offset(0f, 0f), Offset(w, h), medPx)
        drawLine(goldColor, Offset(w, 0f), Offset(0f, h), medPx)

        // 3. Inner diamond
        val diamond = Path().apply {
            moveTo(w / 2, 0f)
            lineTo(w, h / 2)
            lineTo(w / 2, h)
            lineTo(0f, h / 2)
            close()
        }
        drawPath(diamond, color = goldColor, style = androidx.compose.ui.graphics.drawscope.Stroke(medPx))

        // 4. House content (sign number + planet codes)
        for (house in 1..12) {
            val center = houseCentroid(house, w, h)
            val signNum = ChartConstants.northIndianSignForHouse(house, ascNum)
            val planets = chartData.d1.filter { it.value.house == house }.map { it.key }
            val isDiamond = house in listOf(1, 4, 7, 10)

            // Sign number
            val signStyle = TextStyle(
                fontSize = if (isDiamond) 12.sp else 10.sp,
                fontWeight = FontWeight.Bold,
                color = goldColor,
            )
            val signLayout = textMeasurer.measure("$signNum", signStyle)
            val contentSize = if (isDiamond) w * 0.22f else w * 0.15f
            val signOffset = if (isDiamond) contentSize * 0.30f else contentSize * 0.25f
            val signY = center.y - signOffset - signLayout.size.height / 2
            drawText(
                textMeasurer = textMeasurer,
                text = "$signNum",
                style = signStyle,
                topLeft = Offset(center.x - signLayout.size.width / 2, signY),
            )

            // Planets
            if (planets.isNotEmpty()) {
                val radius = contentSize * if (isDiamond) 0.35f else 0.40f
                val offsets = planetOffsets(planets.size, isDiamond, radius)
                planets.forEachIndexed { idx, planetName ->
                    if (idx < offsets.size) {
                        val code = ChartConstants.planetShortCodes[planetName] ?: planetName.take(2)
                        val pt = offsets[idx]
                        val planetStyle = TextStyle(
                            fontSize = if (isDiamond) 10.sp else 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        val pl = textMeasurer.measure(code, planetStyle)
                        drawText(
                            textMeasurer = textMeasurer,
                            text = code,
                            style = planetStyle,
                            topLeft = Offset(
                                center.x + pt.x - pl.size.width / 2,
                                center.y + pt.y - pl.size.height / 2,
                            ),
                        )
                    }
                }
            }
        }
    }
}

// House centroids as fractions of canvas size (mirrors iOS values)
private fun houseCentroid(house: Int, w: Float, h: Float): Offset {
    val m = 0.03f
    val frac: Pair<Float, Float> = when (house) {
        1  -> 0.50f to 0.17f + m
        2  -> 0.25f to 0.08f + m
        3  -> 0.08f + m to 0.25f
        4  -> 0.17f + m to 0.50f
        5  -> 0.08f + m to 0.75f
        6  -> 0.25f to 0.92f - m
        7  -> 0.50f to 0.83f - m
        8  -> 0.75f to 0.92f - m
        9  -> 0.92f - m to 0.75f
        10 -> 0.83f - m to 0.50f
        11 -> 0.92f - m to 0.25f
        12 -> 0.75f to 0.08f + m
        else -> 0.5f to 0.5f
    }
    return Offset(w * frac.first, h * frac.second)
}

// Returns up to 8 safe planet offsets (mirrors iOS positioning)
private fun planetOffsets(count: Int, isDiamond: Boolean, radius: Float): List<Offset> {
    if (count == 1) return listOf(Offset.Zero)
    if (count == 2) return listOf(Offset(-radius * 0.5f, 0f), Offset(radius * 0.5f, 0f))
    if (count == 3) return listOf(
        Offset(0f, -radius * 0.5f),
        Offset(-radius * 0.6f, radius * 0.4f),
        Offset(radius * 0.6f, radius * 0.4f),
    )
    val r1 = radius * 0.5f
    val r2 = radius * 0.9f
    val inner = listOf(Offset(-r1, -r1), Offset(r1, -r1), Offset(-r1, r1), Offset(r1, r1))
    val outer = if (isDiamond) {
        listOf(Offset(0f, -r2), Offset(0f, r2), Offset(-r2, 0f), Offset(r2, 0f))
    } else {
        listOf(Offset(0f, -r2 * 0.8f), Offset(0f, r2 * 0.8f), Offset(-r2 * 0.6f, 0f), Offset(r2 * 0.6f, 0f))
    }
    return inner + outer
}
