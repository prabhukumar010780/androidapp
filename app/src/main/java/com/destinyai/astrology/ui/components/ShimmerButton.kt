package com.destinyai.astrology.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.ui.theme.AuthDimens
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.GoldLight
import com.destinyai.astrology.ui.theme.GoldSoft
import com.destinyai.astrology.ui.theme.NavyDeep
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha

/**
 * Animated gold-shimmer CTA button.
 *
 * Uses [rememberInfiniteTransition] to slide a bright champagne highlight
 * across the gold gradient surface, matching the iOS ShimmerButton.
 */
@Composable
fun ShimmerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")

    // Shimmer position: 0f (left edge) -> 1f (right edge) and repeat
    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )

    // Palette: gold by default, red for destructive actions (sign out, delete)
    val baseColor = if (destructive) Color(0xFFB23535) else Gold
    val softColor = if (destructive) Color(0xFFD64545) else GoldSoft
    val lightColor = if (destructive) Color(0xFFFF7373) else GoldLight

    // Build a brush that places the bright highlight at `shimmerProgress`
    // along the horizontal axis. The highlight band is ~20% of total width.
    val shimmerBrush = Brush.linearGradient(
        colorStops = arrayOf(
            0.00f to baseColor,
            (shimmerProgress - 0.10f).coerceIn(0f, 1f) to baseColor,
            shimmerProgress.coerceIn(0f, 1f) to softColor,
            (shimmerProgress + 0.10f).coerceIn(0f, 1f) to lightColor,
            (shimmerProgress + 0.20f).coerceIn(0f, 1f) to softColor,
            1.00f to baseColor,
        ),
        start = Offset(0f, 0f),
        end = Offset(900f, 0f),
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(AuthDimens.buttonHeight)
            .clip(RoundedCornerShape(AuthDimens.buttonCornerRadius))
            .background(if (enabled) shimmerBrush else Brush.horizontalGradient(listOf(baseColor.copy(alpha = 0.4f), baseColor.copy(alpha = 0.4f))))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (destructive) Color.White else NavyDeep,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
    }
}
