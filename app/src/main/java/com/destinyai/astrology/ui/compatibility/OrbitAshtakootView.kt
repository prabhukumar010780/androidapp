package com.destinyai.astrology.ui.compatibility

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.R
import com.destinyai.astrology.domain.model.KutaDetail
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.GoldLight
import com.destinyai.astrology.ui.theme.NavyDeep
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.SuccessGreen
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Canonical ordering for the 8 ashtakoot bubbles — mirrors iOS
// OrbitAshtakootView.orbitItems which iterates a fixed order array and uses
// prefix matching against kuta.name to enrich with cancellation data.
internal val canonicalKutaOrder: List<String> = listOf(
    "varna",
    "vashya",
    "tara",
    "yoni",
    "maitri",
    "gana",
    "bhakoot",
    "nadi",
)

// Vector icon map for each kuta — replaces emoji so we can apply Icon(tint=...)
// Mirrors iOS SF Symbols (briefcase.fill, bolt.heart.fill, star.fill, flame.fill,
// person.2.fill, theatermasks.fill, heart.circle.fill, waveform.path.ecg).
internal val kutaVectorIcons: Map<String, ImageVector> = mapOf(
    "varna" to Icons.Filled.Work,
    "vashya" to Icons.Filled.Bolt,
    "tara" to Icons.Filled.Star,
    "yoni" to Icons.Filled.Whatshot,
    "maitri" to Icons.Filled.People,
    "gana" to Icons.Filled.TheaterComedy,
    "bhakoot" to Icons.Filled.Favorite,
    "nadi" to Icons.Filled.MonitorHeart,
)

// Sanskrit display names — proper nouns, not localized. Mirrors iOS
// kutaDisplayName map. Sourced from strings.xml so keys remain centralized.
@Composable
internal fun kutaSanskritName(key: String): String = when (key.lowercase()) {
    "varna" -> stringResource(R.string.kuta_sanskrit_varna)
    "vashya" -> stringResource(R.string.kuta_sanskrit_vashya)
    "tara" -> stringResource(R.string.kuta_sanskrit_tara)
    "yoni" -> stringResource(R.string.kuta_sanskrit_yoni)
    "maitri" -> stringResource(R.string.kuta_sanskrit_maitri)
    "gana" -> stringResource(R.string.kuta_sanskrit_gana)
    "bhakoot" -> stringResource(R.string.kuta_sanskrit_bhakoot)
    "nadi" -> stringResource(R.string.kuta_sanskrit_nadi)
    else -> key.replaceFirstChar { it.uppercase() }
}

// Theme caption (work/attraction/destiny/intimacy/...) shown as the tooltip
// title. Mirrors iOS kutaThemeLabel map. Sourced from strings.xml.
@Composable
internal fun kutaThemeLabel(key: String): String = when (key.lowercase()) {
    "varna" -> stringResource(R.string.kuta_theme_work)
    "vashya" -> stringResource(R.string.kuta_theme_attraction)
    "tara" -> stringResource(R.string.kuta_theme_destiny)
    "yoni" -> stringResource(R.string.kuta_theme_intimacy)
    "maitri" -> stringResource(R.string.kuta_theme_mental)
    "gana" -> stringResource(R.string.kuta_theme_temperament)
    "bhakoot" -> stringResource(R.string.kuta_theme_love)
    "nadi" -> stringResource(R.string.kuta_theme_health_progeny)
    else -> key.replaceFirstChar { it.uppercase() }
}

// Bubble label resolved at view time from strings.xml — mirrors iOS semantic
// map (kuta_varna_label etc.). Falls back to the upstream pre-localized
// KutaDetail.label so callers that haven't migrated still render.
@Composable
internal fun kutaBubbleLabel(kuta: KutaDetail): String = when (kuta.key.lowercase()) {
    "varna" -> stringResource(R.string.kuta_varna_label)
    "vashya" -> stringResource(R.string.kuta_vashya_label)
    "tara" -> stringResource(R.string.kuta_tara_label)
    "yoni" -> stringResource(R.string.kuta_yoni_label)
    "maitri" -> stringResource(R.string.kuta_maitri_label)
    "gana" -> stringResource(R.string.kuta_gana_label)
    "bhakoot" -> stringResource(R.string.kuta_bhakoot_label)
    "nadi" -> stringResource(R.string.kuta_nadi_label)
    else -> kuta.label
}

@Composable
fun OrbitAshtakootView(
    kutas: List<KutaDetail>,
    totalScore: Int,
    rawScore: Int,
    maxScore: Int,
    boyName: String,
    girlName: String,
    selectedKuta: KutaDetail? = null,
    onKutaSelected: (KutaDetail?) -> Unit = {},
    modifier: Modifier = Modifier,
    centerContent: (@Composable () -> Unit)? = null,
) {
    val orbitRadius = 155.dp
    val bubbleSize = 64.dp
    val density = LocalDensity.current
    val orbitRadiusPx = with(density) { orbitRadius.toPx() }

    var hintVisible by remember { mutableStateOf(true) }

    // Filter to canonical keys only — mirrors iOS compactMap which silently drops
    // anything that doesn't prefix-match a known kuta key.
    val orderedKutas = remember(kutas) { orderedCanonicalKutas(kutas) }
    val hasDoshaData = orderedKutas.any { it.doshaPresent }

    // Issue 13: drive global selection-state changes through a single
    // updateTransition so layout/alpha/scale settle on the same spring (mirrors
    // iOS withAnimation(.spring(response: 0.3, dampingFraction: 0.7))).
    val selectionTransition = updateTransition(
        targetState = selectedKuta != null,
        label = "orbit_selection",
    )

    // Dim center gauge while a bubble is selected — iOS uses opacity 0.3 with
    // animation(.easeInOut, value: selectedKuta != nil).
    val centerAlpha by selectionTransition.animateFloat(
        transitionSpec = {
            spring(
                dampingRatio = 0.7f,
                stiffness = Spring.StiffnessMediumLow,
            )
        },
        label = "center_alpha",
    ) { isSelected -> if (isSelected) 0.3f else 1.0f }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height((orbitRadius * 2) + bubbleSize + 24.dp)
            .semantics { contentDescription = "orbit_ashtakoot_view" },
        contentAlignment = Alignment.Center,
    ) {
        // Tap-outside-to-dismiss surface — mirrors iOS .contentShape(Rectangle())
        // + .onTapGesture which clears selectedKuta when tapping empty orbit area.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    if (selectedKuta != null) onKutaSelected(null)
                },
        )

        // Issue 1: blurred radial aura layer behind the orbit ring — mirrors
        // iOS RadialGradient(.gold) + .blur on the orbit ambient.
        Box(
            modifier = Modifier
                .size((orbitRadius * 2) + 60.dp)
                .blur(radius = 28.dp)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Gold.copy(alpha = 0.18f),
                                Gold.copy(alpha = 0.06f),
                                Color.Transparent,
                            ),
                            radius = size.minDimension / 2,
                        ),
                        radius = size.minDimension / 2,
                    )
                },
        )

        // Orbit ring decorations — outer thin ring + inner thick wash (Issue 1).
        Box(
            modifier = Modifier
                .size(orbitRadius * 2)
                .drawBehind {
                    // Outer ring
                    drawCircle(
                        color = Gold.copy(alpha = 0.15f),
                        radius = size.minDimension / 2,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                    // Inner soft band
                    drawCircle(
                        color = Gold.copy(alpha = 0.05f),
                        radius = size.minDimension / 2,
                        style = Stroke(width = 40.dp.toPx()),
                    )
                },
        )

        // Center content slot — iOS injects the gauge via centerView() closure.
        // Falls back to SynergyGaugeView for callers that haven't migrated yet.
        Box(
            modifier = Modifier.alpha(centerAlpha),
            contentAlignment = Alignment.Center,
        ) {
            if (centerContent != null) {
                centerContent()
            } else {
                SynergyGaugeView(
                    score = totalScore,
                    rawScore = rawScore,
                    maxScore = maxScore,
                    boyName = boyName,
                    girlName = girlName,
                    size = 160.dp,
                    showAvatars = false,
                )
            }
        }

        orderedKutas.forEachIndexed { index, kuta ->
            val angleDeg = orbitAngleDegrees(index, orderedKutas.size)
            val angleRad = (angleDeg * PI / 180.0).toFloat()
            val xOffset = with(density) { (orbitRadiusPx * cos(angleRad)).toDp() }
            val yOffset = with(density) { (orbitRadiusPx * sin(angleRad)).toDp() }

            PlanetBubble(
                kuta = kuta,
                isSelected = selectedKuta?.key == kuta.key,
                onSelect = {
                    hintVisible = false
                    onKutaSelected(if (selectedKuta?.key == kuta.key) null else kuta)
                },
                modifier = Modifier.offset(x = xOffset, y = yOffset),
            )
        }
    }
}

// ─── Planet Bubble ────────────────────────────────────────────────────────────

// Emoji map kept for legacy callers; new bubbles render via [kutaVectorIcons]
// so they can be tinted with statusColor (matches iOS SF Symbol fills).
internal val kutaIcons = mapOf(
    "varna" to "🏛️",
    "vashya" to "❤️",
    "tara" to "⭐",
    "yoni" to "🔥",
    "maitri" to "🤝",
    "gana" to "🎭",
    "bhakoot" to "💕",
    "nadi" to "💗",
)

@Composable
internal fun PlanetBubble(
    kuta: KutaDetail,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val successColor = SuccessGreen
    val errorColor = Color(0xFFFC8181)
    val statusColor = when (kuta.statusTier) { 1 -> successColor; 2 -> errorColor; else -> Gold }
    val haptics = LocalHapticFeedback.current

    // Adjusted score derived at the view layer — mirrors iOS view-level
    // computation (orbitItems builder): present+cancelled → max, present+active
    // → 0, no dosha → null (use raw score).
    val viewAdjustedScore: Double? = when {
        kuta.doshaPresent && kuta.doshaCancelled -> kuta.maxScore
        kuta.doshaPresent -> 0.0
        else -> null
    }
    val displayScore = viewAdjustedScore ?: kuta.score
    val scoreText = "${formatScore(displayScore)}/${formatScore(kuta.maxScore)}"
    val bubbleLabel = kutaBubbleLabel(kuta)
    val sanskritName = kutaSanskritName(kuta.key)

    // Issue 13: bubble-local transition mirrors iOS withAnimation(.spring(0.3,
    // 0.7)) — drives scale, ring stroke, and aura alpha together.
    val bubbleTransition = updateTransition(targetState = isSelected, label = "bubble_state")
    val bubbleScale by bubbleTransition.animateFloat(
        transitionSpec = {
            spring(
                dampingRatio = 0.7f,
                stiffness = Spring.StiffnessMedium,
            )
        },
        label = "bubble_scale",
    ) { selected -> if (selected) 1.12f else 1.0f }
    val auraAlpha by bubbleTransition.animateFloat(
        transitionSpec = { tween(durationMillis = 220) },
        label = "bubble_aura_alpha",
    ) { selected -> if (selected) 0.8f else 0.5f }
    val ringStroke by bubbleTransition.animateFloat(
        transitionSpec = { tween(durationMillis = 180) },
        label = "bubble_ring_stroke",
    ) { selected -> if (selected) 2f else 1.5f }

    Box(
        modifier = modifier
            .size(64.dp)
            .scale(bubbleScale)
            .semantics { contentDescription = "orbit_bubble_${kuta.key}" }
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {
                    // Issue 11 + 14: light haptic on tap (TextHandleMove ≈ light).
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Layer 0 — Outer ring: mirrors iOS PlanetBubble layer 0.
        // Uses requiredSize so it escapes the 64dp clip boundary and renders
        // visibly outside the orb (iOS uses orbSize+8 × scaleEffect(1.08) ≈ 77pt).
        // Color logic mirrors iOS: dosha+cancelled=success, dosha+active=error, else statusColor.
        val outerRingColor = when {
            kuta.doshaPresent && kuta.doshaCancelled -> successColor.copy(alpha = 0.35f)
            kuta.doshaPresent -> errorColor.copy(alpha = 0.40f)
            else -> statusColor.copy(alpha = 0.35f)
        }
        Box(
            modifier = Modifier
                .requiredSize(76.dp)
                .drawBehind {
                    drawCircle(
                        color = outerRingColor,
                        radius = size.minDimension / 2 - 1.dp.toPx(),
                        style = Stroke(width = 2.5.dp.toPx()),
                    )
                },
        )
        // Layer 1 — blurred radial status aura (iOS layer 1 RadialGradient + blur).
        Box(
            modifier = Modifier
                .size(96.dp)
                .blur(radius = 12.dp)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                statusColor.copy(alpha = auraAlpha),
                                statusColor.copy(alpha = 0.10f),
                                Color.Transparent,
                            ),
                            radius = size.minDimension / 2,
                        ),
                        radius = size.minDimension / 2,
                    )
                },
        )
        // Layer 2 — Glass sphere base (radial gradient).
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF2E3342),
                            Color(0xFF1E2233),
                            Color(0xFF141824).copy(alpha = 0.6f),
                        ),
                        radius = 96f,
                    ),
                ),
        )
        // Layer 3 — Inner glass bubble (top-left bias, white→clear→shadow).
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.15f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.20f),
                        ),
                        center = Offset(18f, 18f),
                        radius = 60f,
                    ),
                ),
        )
        // Layer 4 — Top highlight specular (iOS layer 4).
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.50f),
                            Color.White.copy(alpha = 0.10f),
                            Color.Transparent,
                        ),
                        center = Offset(15f, 15f),
                        radius = 36f,
                    ),
                ),
        )
        // Layer 5 — Gold ring border (Issue 5: brighter gold gradient when
        // selected, never the flat statusColor stroke).
        Box(
            modifier = Modifier
                .size(64.dp)
                .border(
                    width = ringStroke.dp,
                    brush = Brush.linearGradient(
                        colors = if (isSelected) {
                            listOf(
                                GoldLight,
                                Gold.copy(alpha = 0.6f),
                                GoldLight.copy(alpha = 0.9f),
                            )
                        } else {
                            listOf(
                                Gold.copy(alpha = 0.6f),
                                Gold.copy(alpha = 0.3f),
                                Gold.copy(alpha = 0.5f),
                            )
                        },
                    ),
                    shape = CircleShape,
                ),
        )
        // Layer 6 — Content: tinted vector icon + score + label.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.offset(y = (-2).dp),
        ) {
            kutaVectorIcons[kuta.key]?.let { vector ->
                Icon(
                    imageVector = vector,
                    contentDescription = sanskritName,
                    tint = statusColor,
                    modifier = Modifier
                        .size(14.dp)
                        .shadow(elevation = 4.dp, shape = CircleShape, clip = false),
                )
            }
            Text(
                text = scoreText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (kuta.doshaCancelled) successColor else GoldLight,
                fontSize = 9.sp,
                lineHeight = 11.sp,
            )
            Text(
                text = bubbleLabel.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 6.sp,
                maxLines = 1,
            )
        }
        // Layer 7 — Dosha indicator badge (Issue 4: vector Icon on a
        // mainBackground circle with shadow, mirrors iOS).
        if (kuta.doshaPresent) {
            val badgeTint = if (kuta.doshaCancelled) successColor else errorColor
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(16.dp)
                    .shadow(elevation = 3.dp, shape = CircleShape, clip = false)
                    .clip(CircleShape)
                    .background(NavyDeep)
                    .semantics {
                        contentDescription =
                            if (kuta.doshaCancelled) "dosha_cancelled_badge" else "dosha_active_badge"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (kuta.doshaCancelled) Icons.Filled.CheckCircle else Icons.Filled.Error,
                    contentDescription = null,
                    tint = badgeTint,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

// Pure geometry helpers — testable without Compose

internal fun orbitAngleDegrees(index: Int, total: Int): Double =
    index * (360.0 / total.coerceAtLeast(1)) - 90.0

// Filter + order kutas into the canonical varna→nadi sequence using prefix
// matching, mirroring iOS orbitItems builder. Unmapped entries are dropped.
internal fun orderedCanonicalKutas(kutas: List<KutaDetail>): List<KutaDetail> {
    return canonicalKutaOrder.mapNotNull { key ->
        kutas.firstOrNull {
            it.key.lowercase().startsWith(key) || it.label.lowercase().startsWith(key)
        }
    }
}

// Tooltip subtitle — "0/8 → 8/8" arrow form when cancelled, else raw "%d/%d" or
// "%.1f/%.1f" depending on whether the score has a fractional component.
internal fun kutaScoreSubtitle(kuta: KutaDetail): String {
    val base = "${formatScore(kuta.score)}/${formatScore(kuta.maxScore)}"
    return if (kuta.doshaPresent && kuta.doshaCancelled) {
        val adj = kuta.adjustedScore ?: kuta.maxScore
        "$base → ${formatScore(adj)}/${formatScore(kuta.maxScore)}"
    } else {
        base
    }
}

// ─── Orbit Tooltip View ───────────────────────────────────────────────────────
// Animated tooltip card keyed to selectedKuta state — mirrors iOS
// OrbitTooltipView with spring scale + fade on appearance/dismissal.

@Composable
fun OrbitTooltipView(
    selectedKuta: KutaDetail?,
    boyName: String,
    girlName: String,
    onDismiss: () -> Unit,
    onClassicalAnalysis: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Hold the last non-null kuta so the exit animation has content to render.
    var renderedKuta by remember { mutableStateOf(selectedKuta) }
    LaunchedEffect(selectedKuta) {
        if (selectedKuta != null) renderedKuta = selectedKuta
    }

    AnimatedVisibility(
        visible = selectedKuta != null,
        enter = fadeIn(animationSpec = tween(durationMillis = 180)) +
            scaleIn(
                initialScale = 0.92f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            ),
        exit = fadeOut(animationSpec = tween(durationMillis = 120)) +
            scaleOut(targetScale = 0.94f, animationSpec = tween(durationMillis = 120)),
        modifier = modifier,
    ) {
        renderedKuta?.let { kuta ->
            OrbitTooltipCard(
                kuta = kuta,
                boyName = boyName,
                girlName = girlName,
                onDismiss = onDismiss,
                onClassicalAnalysis = onClassicalAnalysis,
            )
        }
    }
}

@Composable
private fun OrbitTooltipCard(
    kuta: KutaDetail,
    boyName: String,
    girlName: String,
    onDismiss: () -> Unit,
    onClassicalAnalysis: (String) -> Unit,
) {
    val successColor = SuccessGreen
    val errorColor = Color(0xFFFC8181)
    val statusColor = when (kuta.statusTier) { 1 -> successColor; 2 -> errorColor; else -> Gold }
    val themeLabel = kutaThemeLabel(kuta.key)
    val sanskritName = kutaSanskritName(kuta.key)
    val subtitle = stringResource(
        R.string.kuta_koota_subtitle,
        sanskritName,
        kutaScoreSubtitle(kuta),
    )
    val closeDesc = stringResource(R.string.orbit_bubble_close)

    Column(
        modifier = Modifier
            .width(300.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(NavySurface)
            .border(1.dp, statusColor.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .semantics { contentDescription = "orbit_tooltip_${kuta.key}" }
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { /* consume — prevent dismiss */ },
    ) {
        // Header: tinted vector icon + theme label + Sanskrit subtitle + score badge + close button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(statusColor.copy(alpha = 0.1f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            kutaVectorIcons[kuta.key]?.let { vector ->
                Icon(
                    imageVector = vector,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier
                        .size(20.dp)
                        .shadow(elevation = 6.dp, shape = CircleShape, clip = false),
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                // Issue 8: theme caption (work/attraction/...) as the title.
                Text(
                    text = themeLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = CreamText,
                )
                // Issue 9 + 10: "<Sanskrit> Koota · 0/8 → 8/8" subtitle.
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = CreamDim,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
            ScoreBadge(kuta = kuta)
            Spacer(Modifier.width(6.dp))
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(28.dp)
                    .semantics { contentDescription = "orbit_tooltip_close" },
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = closeDesc,
                    tint = CreamDim,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        // Dosha indicator
        if (kuta.doshaPresent) {
            val doshaColor = if (kuta.doshaCancelled) successColor else errorColor
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(doshaColor.copy(alpha = 0.06f))
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (kuta.doshaCancelled) {
                        stringResource(R.string.kuta_dosha_cancelled)
                    } else {
                        stringResource(R.string.kuta_dosha_active)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = doshaColor,
                )
                kuta.cancellationReason?.let { reason ->
                    Text(
                        text = " — $reason",
                        style = MaterialTheme.typography.labelSmall,
                        color = CreamDim,
                    )
                }
            }
        }

        // Description — uses KutaTextBuilder.descriptionParagraph() equivalent
        // (kutaRichDescription) ported in KutaTextBuilder.kt.
        Text(
            text = kutaRichDescription(kuta, boyName, girlName),
            style = MaterialTheme.typography.bodySmall,
            color = CreamDim,
            lineHeight = 20.sp,
            modifier = Modifier.padding(16.dp),
        )

        HorizontalDivider(color = Gold.copy(alpha = 0.12f))

        // Classical analysis CTA — mirrors iOS button which calls
        // onClassicalAnalysis(KutaTextBuilder.classicalPrompt()).
        TextButton(
            onClick = {
                val prompt = kutaClassicalPrompt(kuta, boyName, girlName)
                onClassicalAnalysis(prompt)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
                .semantics { contentDescription = "orbit_tooltip_classical_cta" },
        ) {
            Text(
                text = stringResource(R.string.see_classical_analysis),
                style = MaterialTheme.typography.labelMedium,
                color = Gold,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ScoreBadge — pill that swaps between four variants matching iOS scoreBadge:
//   1. dosha active (no cancel) → red "⚠ Active"
//   2. dosha cancelled         → green "✓ Cancelled · adj/max"
//   3. perfect score           → green "✓ score/max"
//   4. partial score           → gold  "◑ score/max"
@Composable
internal fun ScoreBadge(kuta: KutaDetail) {
    val successColor = Color(0xFF48BB78)
    val errorColor = Color(0xFFFC8181)

    val (label, color) = when {
        kuta.doshaPresent && !kuta.doshaCancelled ->
            "⚠ Active" to errorColor
        kuta.doshaPresent && kuta.doshaCancelled -> {
            val adj = kuta.adjustedScore ?: kuta.maxScore
            "✓ Cancelled · ${formatScore(adj)}/${formatScore(kuta.maxScore)}" to successColor
        }
        kuta.score >= kuta.maxScore ->
            "✓ ${formatScore(kuta.score)}/${formatScore(kuta.maxScore)}" to successColor
        else ->
            "◑ ${formatScore(kuta.score)}/${formatScore(kuta.maxScore)}" to Gold
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            fontSize = 9.sp,
        )
    }
}
