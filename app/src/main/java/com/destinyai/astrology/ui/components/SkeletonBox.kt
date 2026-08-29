package com.destinyai.astrology.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.destinyai.astrology.ui.theme.LocalReduceMotion
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.NavyVariant

/**
 * Batch 6b fix #6: content-shaped shimmer placeholder for first-load states.
 *
 * Renders a horizontally-sweeping shimmer highlight over a [NavySurface] base,
 * matching the brand dark palette. Respects [LocalReduceMotion] — when animations
 * are off the box renders as a static dark rectangle.
 *
 * Usage: replace spinner-only loading states with composables built from [SkeletonBox]
 * shapes that mirror the real content dimensions (cards, text rows, orbs, etc.).
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
) {
    val reduceMotion = LocalReduceMotion.current
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "skeletonProgress",
    )
    val effectiveProgress = if (reduceMotion) 0.5f else shimmerProgress

    val shimmerBrush = Brush.linearGradient(
        colorStops = arrayOf(
            0.0f to NavySurface,
            (effectiveProgress - 0.2f).coerceIn(0f, 1f) to NavySurface,
            effectiveProgress.coerceIn(0f, 1f) to NavyVariant.copy(alpha = 0.9f),
            (effectiveProgress + 0.2f).coerceIn(0f, 1f) to NavySurface,
            1.0f to NavySurface,
        ),
        start = Offset(0f, 0f),
        end = Offset(1000f, 0f),
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(shimmerBrush),
    )
}

// ── Pre-built skeleton layouts ──────────────────────────────────────────────

/** Skeleton for a Home DashaInsightCard / transit-row card (wide card, two text rows). */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(NavySurface)
            .padding(16.dp),
    ) {
        Column {
            SkeletonBox(modifier = Modifier.fillMaxWidth(0.55f).height(14.dp), cornerRadius = 6.dp)
            Spacer(Modifier.height(10.dp))
            SkeletonBox(modifier = Modifier.fillMaxWidth(0.8f).height(10.dp), cornerRadius = 6.dp)
            Spacer(Modifier.height(8.dp))
            SkeletonBox(modifier = Modifier.fillMaxWidth(0.4f).height(10.dp), cornerRadius = 6.dp)
        }
    }
}

/** Skeleton for a story-orb row (row of circular placeholders). */
@Composable
fun SkeletonOrbRow(modifier: Modifier = Modifier) {
    Row(modifier = modifier.padding(horizontal = 16.dp)) {
        repeat(5) {
            Column {
                SkeletonBox(
                    modifier = Modifier.size(72.dp),
                    cornerRadius = 36.dp,
                )
                Spacer(Modifier.height(6.dp))
                SkeletonBox(
                    modifier = Modifier.width(56.dp).height(8.dp),
                    cornerRadius = 4.dp,
                )
            }
            if (it < 4) Spacer(Modifier.width(12.dp))
        }
    }
}

/** Skeleton for a Charts planet-row list item. */
@Composable
fun SkeletonListRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        SkeletonBox(modifier = Modifier.size(36.dp), cornerRadius = 18.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            SkeletonBox(modifier = Modifier.fillMaxWidth(0.5f).height(12.dp), cornerRadius = 6.dp)
            Spacer(Modifier.height(6.dp))
            SkeletonBox(modifier = Modifier.fillMaxWidth(0.7f).height(10.dp), cornerRadius = 6.dp)
        }
    }
}

/** Skeleton for the Compatibility result gauge + summary cards. */
@Composable
fun SkeletonCompatibilityResult(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        // Gauge placeholder
        Box(modifier = Modifier.size(160.dp).clip(CircleShape).background(NavySurface))
        Spacer(Modifier.height(20.dp))
        // Summary card rows
        repeat(3) {
            SkeletonCard()
            Spacer(Modifier.height(12.dp))
        }
    }
}
