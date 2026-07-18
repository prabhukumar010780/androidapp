package com.destinyai.astrology.ui.compatibility

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.GoldChampagne
import com.destinyai.astrology.ui.theme.NavyDeep

@Composable
fun FloatingContextButton(
    onClick: () -> Unit,
    icon: ImageVector = Icons.Filled.Forum,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "fab_scale",
    )

    Box(
        modifier = modifier.semantics { contentDescription = "ask_destiny_button" },
        contentAlignment = Alignment.Center,
    ) {
        // Glow aura behind button (blurred halo to match iOS)
        Box(
            modifier = Modifier
                .size(70.dp)
                .blur(12.dp)
                .background(Gold.copy(alpha = 0.30f), CircleShape),
        )

        // Main gold circle button
        Box(
            modifier = Modifier
                .scale(buttonScale)
                .size(56.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = CircleShape,
                    ambientColor = Gold,
                    spotColor = Gold,
                )
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(GoldChampagne, Gold),
                        start = Offset(0f, 0f),
                        end = Offset(100f, 100f),
                    )
                )
                .clickable(
                    indication = null,
                    interactionSource = interactionSource,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = NavyDeep,
                modifier = Modifier.size(24.dp),
            )
        }

        // Outer ring
        Box(
            modifier = Modifier
                .size(68.dp)
                .border(1.dp, Gold.copy(alpha = 0.3f), CircleShape),
        )
    }
}
