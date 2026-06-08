package com.destinyai.astrology.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.ui.theme.AppTheme
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold

/**
 * Segmented control with an animated gold-tinted glass background and a
 * sliding selection pill. Matches the iOS GlassSegmentedControl.
 *
 * @param options        List of segment labels (must be non-empty).
 * @param selectedIndex  Currently selected segment (0-based).
 * @param onSelect       Called with the index of the tapped segment.
 * @param height         Overall control height (default 44dp matches iOS).
 * @param cornerRadius   Outer corner radius (default 22dp = pill shape).
 */
@Composable
fun GlassSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    cornerRadius: Dp = 22.dp,
) {
    if (options.isEmpty()) return

    // Coerce selectedIndex into valid range to prevent off-screen pill
    val safeIndex = selectedIndex.coerceIn(0, options.lastIndex)
    val pillPadding = 3.dp
    val haptics = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .height(height)
            .semantics { contentDescription = "glass_segmented_control" }
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                color = Color.Black.copy(alpha = 0.3f),
                shape = RoundedCornerShape(cornerRadius),
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(cornerRadius),
            )
            .padding(pillPadding),
    ) {
        // Animated selection pill
        BoxWithConstraints(modifier = Modifier.matchParentSize()) {
            val segmentWidth = maxWidth / options.size

            val pillOffsetX by animateDpAsState(
                targetValue = segmentWidth * safeIndex,
                animationSpec = spring(
                    dampingRatio = 0.7f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "pillOffset",
            )

            Box(
                modifier = Modifier
                    .offset(x = pillOffsetX)
                    .width(segmentWidth)
                    .height(maxHeight)
                    .shadow(
                        elevation = 6.dp,
                        shape = RoundedCornerShape(cornerRadius - pillPadding),
                        ambientColor = Gold.copy(alpha = 0.4f),
                        spotColor = Gold.copy(alpha = 0.4f),
                    )
                    .clip(RoundedCornerShape(cornerRadius - pillPadding))
                    .background(AppTheme.gradients.premiumCardGradient),
            )
        }

        // Segment label row drawn on top of the pill
        Row(modifier = Modifier.matchParentSize()) {
            options.forEachIndexed { index, label ->
                SegmentItem(
                    label = label,
                    selected = index == safeIndex,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics { contentDescription = "segment_${index}" },
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(index)
                    },
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
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}
