package com.destinyai.astrology.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.services.HapticManager
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay

/**
 * iOS parity (PremiumComponents.swift TypewriterText).
 *
 * Renders [text] one character at a time with a blinking gold cursor.
 *
 * @param text  Full text to type out.
 * @param charDelayMs  Per-character delay (default 40ms — iOS default).
 * @param showCursor  Whether to render the trailing blinking cursor.
 */
@Composable
fun TypewriterText(
    text: String,
    modifier: Modifier = Modifier,
    charDelayMs: Long = 40L,
    showCursor: Boolean = true,
    fontSize: androidx.compose.ui.unit.TextUnit = 18.sp,
    color: Color = CreamText,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    var visibleCount by remember(text) { mutableStateOf(0) }

    LaunchedEffect(text) {
        visibleCount = 0
        text.forEachIndexed { index, _ ->
            delay(charDelayMs)
            visibleCount = index + 1
        }
    }

    val cursorAlpha = remember { mutableStateOf(1f) }
    if (showCursor) {
        val infinite = rememberInfiniteTransition(label = "cursor")
        val anim by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(550),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "cursorBlink",
        )
        cursorAlpha.value = anim
    }

    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        Text(
            text = text.substring(0, visibleCount.coerceAtMost(text.length)),
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
        )
        if (showCursor) {
            Text(
                text = "|",
                color = Gold,
                fontSize = fontSize,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.alpha(cursorAlpha.value),
            )
        }
    }
}

/**
 * iOS parity (PremiumComponents.swift FloatingIcon).
 *
 * Continuously offsets vertically (~6dp amplitude) at a slow easing while a
 * radial gold glow pulses behind it. Used for hero icons in onboarding.
 */
@Composable
fun FloatingIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    iconSize: Dp = 48.dp,
    tint: Color = Gold,
) {
    val infinite = rememberInfiniteTransition(label = "floatIcon")
    val offsetY by infinite.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "offsetY",
    )
    val glowAlpha by infinite.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    Box(
        modifier = modifier.size(iconSize * 1.6f),
        contentAlignment = Alignment.Center,
    ) {
        // Radial glow behind the icon
        Box(
            modifier = Modifier
                .size(iconSize * 1.5f)
                .alpha(glowAlpha)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Gold.copy(alpha = 0.5f), Color.Transparent),
                    ),
                ),
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(iconSize)
                .offset(y = offsetY.dp),
        )
    }
}

/**
 * iOS parity (PremiumComponents.swift GlassCard).
 *
 * Bento cell with frosted backing, 5-stop gold rim, and rounded 20dp corners.
 * Drop-in for grid-cell layouts.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val rim = Brush.linearGradient(
        colorStops = arrayOf(
            0.05f to Color.White.copy(alpha = 0.7f),
            0.25f to Gold,
            0.50f to Gold.copy(alpha = 0.3f),
            0.75f to Gold,
            0.95f to Color.White.copy(alpha = 0.6f),
        ),
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(NavySurface.copy(alpha = 0.78f))
            .drawBehind {
                drawRoundRect(
                    brush = rim,
                    size = size,
                    cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
                    style = Stroke(width = 1.2.dp.toPx()),
                )
            },
        content = content,
    )
}

/**
 * Data carrier for one cell of [BentoGridFeaturesView].
 */
data class BentoFeatureItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
)

/**
 * iOS parity (PremiumComponents.swift BentoGridFeaturesView).
 *
 * 2x2 onboarding grid of feature cells. Each cell is a [GlassCard] with a
 * floating gold icon, gold title, and dim cream subtitle.
 */
@Composable
fun BentoGridFeaturesView(
    items: List<BentoFeatureItem>,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items) { item ->
            GlassCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FloatingIcon(icon = item.icon, iconSize = 36.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = item.title,
                        color = Gold,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.subtitle,
                        color = CreamDim,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * iOS parity (PremiumComponents.swift BioRhythmModifier — 60bpm pulse).
 *
 * Returns a Modifier that scales the target between 0.97f and 1.03f at 60bpm
 * (1 second per cycle). Mirrors the iOS modifier used for breathing UI.
 */
@Composable
fun bioRhythmScale(): Float {
    val infinite = rememberInfiniteTransition(label = "bioRhythm")
    val scale by infinite.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bioRhythmScale",
    )
    return scale
}

/**
 * Hilt EntryPoint to retrieve the singleton HapticManager from a Composable
 * (which has no @Inject lifecycle of its own). Keeps haptic ownership with DI.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface HapticManagerEntryPoint {
    fun hapticManager(): HapticManager
}

/**
 * iOS parity (PremiumComponents.swift BioRhythmModifier.pulse() —
 * HapticManager.shared.playHeartbeat() on every beat).
 *
 * Same 60bpm scale as [bioRhythmScale] but additionally fires
 * [HapticManager.playHeartbeat] on each cycle boundary so the user FEELS
 * the breath. Used by Splash, Auth, and GuestSignIn logos.
 *
 * @param active when false, returns 1f and skips haptics (e.g. while loading).
 */
@Composable
fun rememberBioRhythmWithHaptic(active: Boolean = true): Float {
    val context = LocalContext.current
    val haptic = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            HapticManagerEntryPoint::class.java,
        ).hapticManager()
    }

    // Heartbeat tick once per 1s cycle while active. Coroutine-driven so the
    // animation render path stays cheap. Mirrors iOS .onChange(of: phase) firing
    // playHeartbeat() on each beat boundary.
    LaunchedEffect(active) {
        while (active) {
            haptic.playHeartbeat()
            delay(1000L)
        }
    }

    if (!active) return 1f
    return bioRhythmScale()
}
