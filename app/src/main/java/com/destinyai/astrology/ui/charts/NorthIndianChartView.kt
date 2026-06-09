package com.destinyai.astrology.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
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
            .padding(8.dp)
            // iOS parity: outer chart shadow + inner grid glow (issue 6)
            .shadow(
                elevation = 4.dp,
                ambientColor = goldColor.copy(alpha = 0.1f),
                spotColor = goldColor.copy(alpha = 0.1f),
            )
            .shadow(
                elevation = 2.dp,
                ambientColor = goldColor.copy(alpha = 0.3f),
                spotColor = goldColor.copy(alpha = 0.3f),
            ),
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

        // 1. Double outer border — gradient stroke (issue 1)
        drawRect(
            brush = goldGradient,
            size = size,
            style = Stroke(width = medPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawRect(
            brush = goldGradient,
            topLeft = Offset(4f, 4f),
            size = androidx.compose.ui.geometry.Size(w - 8f, h - 8f),
            style = Stroke(width = thinPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // 2. Diagonals — gradient + round cap (issue 2)
        drawLine(
            brush = goldGradient,
            start = Offset(0f, 0f),
            end = Offset(w, h),
            strokeWidth = medPx,
            cap = StrokeCap.Round,
        )
        drawLine(
            brush = goldGradient,
            start = Offset(w, 0f),
            end = Offset(0f, h),
            strokeWidth = medPx,
            cap = StrokeCap.Round,
        )

        // 3. Inner diamond — gradient + round join (issue 3)
        val diamond = Path().apply {
            moveTo(w / 2, 0f)
            lineTo(w, h / 2)
            lineTo(w / 2, h)
            lineTo(0f, h / 2)
            close()
        }
        drawPath(
            path = diamond,
            brush = goldGradient,
            style = Stroke(width = medPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // 4. House content (sign number + planet codes)
        for (house in 1..12) {
            val center = houseCentroid(house, w, h)
            val signNum = ChartConstants.northIndianSignForHouse(house, ascNum)
            val planets = chartData.d1.filter { it.value.house == house }.map { it.key }
            val isDiamond = house in listOf(1, 4, 7, 10)

            // Sign number — gold gradient brush (issue 4)
            val signStyle = TextStyle(
                fontSize = if (isDiamond) 12.sp else 10.sp,
                fontWeight = FontWeight.Bold,
                brush = goldGradient,
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
                        // Planet text with iOS-parity black drop shadow (issue 5)
                        val planetStyle = TextStyle(
                            fontSize = if (isDiamond) 10.sp else 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.6f),
                                offset = Offset(0f, 1f),
                                blurRadius = 1f,
                            ),
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
