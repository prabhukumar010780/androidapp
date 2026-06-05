package com.destinyai.astrology.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface

/**
 * Segmented control with an animated gold-tinted glass background and a
 * sliding selection pill. Matches the iOS GlassSegmentedControl.
 *
 * @param options        List of segment labels (must be non-empty).
 * @param selectedIndex  Currently selected segment (0-based).
 * @param onSelect       Called with the index of the tapped segment.
 */
@Composable
fun GlassSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return

    val controlHeight = 38.dp
    val pillPadding = 3.dp
    val cornerRadius = 10.dp

    Box(
        modifier = modifier
            .height(controlHeight)
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        NavySurface.copy(alpha = 0.9f),
                        Gold.copy(alpha = 0.08f),
                        NavySurface.copy(alpha = 0.9f),
                    ),
                ),
            ),
    ) {
        // Animated selection pill
        BoxWithConstraints(modifier = Modifier.matchParentSize()) {
            val segmentWidth = maxWidth / options.size

            val pillOffsetX by animateDpAsState(
                targetValue = segmentWidth * selectedIndex + pillPadding,
                animationSpec = tween(durationMillis = 220),
                label = "pillOffset",
            )

            Box(
                modifier = Modifier
                    .offset(x = pillOffsetX, y = pillPadding)
                    .width(segmentWidth - pillPadding * 2)
                    .height(maxHeight - pillPadding * 2)
                    .clip(RoundedCornerShape(cornerRadius - 2.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Gold.copy(alpha = 0.25f),
                                Gold.copy(alpha = 0.15f),
                            ),
                        ),
                    ),
            )
        }

        // Segment label row drawn on top of the pill
        Row(modifier = Modifier.matchParentSize()) {
            options.forEachIndexed { index, label ->
                SegmentItem(
                    label = label,
                    selected = index == selectedIndex,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = { onSelect(index) },
                )
            }
        }
    }
}

@Composable
private fun SegmentItem(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) Gold else CreamText.copy(alpha = 0.55f),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
