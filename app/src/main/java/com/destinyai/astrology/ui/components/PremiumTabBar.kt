package com.destinyai.astrology.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface

/**
 * Mirrors iOS PremiumTabBar (Views/Components/PremiumTabBar.swift). A reusable
 * horizontal-scrolling pill filter row driven by a `List<String>` and a
 * `selected` binding. The active pill renders a blurred gold-tinted Capsule
 * glow (radius 10dp, gold @ 20% opacity) behind a DivineGlassCard-style pill
 * (CircleShape Surface with subtle navy fill + gold border). Pill labels are
 * 13sp gold when active and white@0.8 when inactive. Tapping a pill fires a
 * light haptic and updates the selected value via [onSelect] with a spring
 * animation.
 */
@Composable
fun PremiumTabBar(
    tabs: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .horizontalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        tabs.forEachIndexed { index, tab ->
            if (index > 0) Spacer(modifier = Modifier.width(12.dp))
            PremiumTabPill(
                label = tab,
                isSelected = tab == selected,
                onClick = { onSelect(tab) },
            )
        }
    }
}

@Composable
private fun PremiumTabPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    // Spring scale animation on press (mirrors iOS ScaleButtonStyle + spring).
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "premium_pill_scale",
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        // Active gold glow Capsule — blur 10dp, gold @ 20% opacity.
        // Mirrors iOS Capsule().fill(gold.opacity(0.2)).blur(radius: 10).
        if (isSelected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .blur(radius = 10.dp)
                    .clip(CircleShape)
                    .background(Gold.copy(alpha = 0.2f)),
            )
        }
        // DivineGlassCard-style pill (cornerRadius 100 → CircleShape).
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(NavySurface.copy(alpha = 0.6f))
                .border(
                    width = 1.dp,
                    color = if (isSelected) Gold.copy(alpha = 0.5f) else CreamDim.copy(alpha = 0.2f),
                    shape = CircleShape,
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    },
                )
                .semantics { contentDescription = label }
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (isSelected) Gold else Color.White.copy(alpha = 0.8f),
            )
        }
    }
}
