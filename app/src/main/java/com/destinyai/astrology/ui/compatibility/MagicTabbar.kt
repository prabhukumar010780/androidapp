package com.destinyai.astrology.ui.compatibility

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.R
import com.destinyai.astrology.domain.model.DestinyTileType
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.Gold

/**
 * Weighted-Row tab bar — mirrors iOS Compatibility/Components/MagicTabbar.swift.
 * Each tab takes equal width (weight = 1f) so the tab strip is balanced rather
 * than scrollable. Active tab gets a subtle 7% white rounded fill, an enlarged
 * 32.dp active icon image (1.12x scale), and a per-tile accent indicator dot
 * below the label. The whole row is wrapped in an 18.dp rounded pill with a
 * 3% white fill and 6% white stroke to match iOS.
 *
 * Note: counts are received but intentionally NOT rendered — iOS reads the
 * counts map but never displays the values on the tab pills.
 */
@Composable
internal fun MagicTabbar(
    tabs: List<DestinyTileType>,
    selectedTab: DestinyTileType,
    @Suppress("UNUSED_PARAMETER") tileCounts: Map<DestinyTileType, Int>,
    onSelect: (DestinyTileType) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics { contentDescription = "magic_tabbar" },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            val isSelected = selectedTab == tab
            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) Color.White.copy(alpha = 0.07f) else Color.Transparent,
                animationSpec = tween(durationMillis = 300),
                label = "magic_tab_bg",
            )
            val labelColor by animateColorAsState(
                targetValue = if (isSelected) Color.White else CreamDim,
                animationSpec = tween(durationMillis = 300),
                label = "magic_tab_label",
            )
            val iconScale by animateFloatAsState(
                targetValue = if (isSelected) 1.12f else 1.0f,
                animationSpec = tween(durationMillis = 300),
                label = "magic_tab_icon_scale",
            )
            val indicatorSize by animateDpAsState(
                targetValue = if (isSelected) 3.dp else 0.dp,
                animationSpec = tween(durationMillis = 300),
                label = "magic_tab_dot",
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(backgroundColor)
                    .clickable {
                        if (!isSelected) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        onSelect(tab)
                    }
                    .padding(top = 8.dp, bottom = 6.dp, start = 2.dp, end = 2.dp)
                    .semantics { contentDescription = tab.semanticsId() },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Active/inactive icon image (32.dp) with 1.12x scaleEffect when selected
                Image(
                    painter = painterResource(id = tab.iconResource(isSelected)),
                    contentDescription = stringResource(id = tab.labelResource()),
                    modifier = Modifier
                        .size(32.dp)
                        .scale(iconScale),
                )
                Text(
                    text = stringResource(id = tab.labelResource()).uppercase(),
                    fontSize = 9.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                    color = labelColor,
                    textAlign = TextAlign.Center,
                )
                // Animated active indicator dot — uses per-tile accent color, mirrors iOS
                Box(
                    modifier = Modifier
                        .size(indicatorSize)
                        .shadow(
                            elevation = if (isSelected) 6.dp else 0.dp,
                            shape = CircleShape,
                            ambientColor = tab.accentColor,
                            spotColor = tab.accentColor,
                        )
                        .clip(CircleShape)
                        .background(
                            if (isSelected) tab.accentColor else Color.Transparent,
                        ),
                )
            }
        }
    }
}

/**
 * Maps a DestinyTileType + selection state to its drawable resource —
 * mirrors iOS activeIconImage / inactiveIconImage helpers.
 */
private fun DestinyTileType.iconResource(isSelected: Boolean): Int = when (this) {
    DestinyTileType.WEALTH ->
        if (isSelected) R.drawable.ic_yoga_wealth_active else R.drawable.ic_yoga_wealth_inactive
    DestinyTileType.CAREER ->
        if (isSelected) R.drawable.ic_yoga_career_active else R.drawable.ic_yoga_career_inactive
    DestinyTileType.LOVE ->
        if (isSelected) R.drawable.ic_yoga_love_active else R.drawable.ic_yoga_love_inactive
    DestinyTileType.FAMILY ->
        if (isSelected) R.drawable.ic_yoga_family_active else R.drawable.ic_yoga_family_inactive
    DestinyTileType.WISDOM ->
        if (isSelected) R.drawable.ic_yoga_wisdom_active else R.drawable.ic_yoga_wisdom_inactive
    DestinyTileType.HEALTH ->
        if (isSelected) R.drawable.ic_yoga_health_active else R.drawable.ic_yoga_health_inactive
    DestinyTileType.DOSHA ->
        if (isSelected) R.drawable.ic_yoga_challenges_active else R.drawable.ic_yoga_challenges_inactive
}

/**
 * Maps a DestinyTileType to its localized label string resource — mirrors iOS
 * `tile.localizedLabel`. Sourcing the label from strings.xml ensures all 13
 * locales render correctly instead of falling back to the hardcoded English
 * enum field.
 */
private fun DestinyTileType.labelResource(): Int = when (this) {
    DestinyTileType.WEALTH -> R.string.tile_wealth_label
    DestinyTileType.CAREER -> R.string.tile_career_label
    DestinyTileType.LOVE -> R.string.tile_love_label
    DestinyTileType.FAMILY -> R.string.tile_family_label
    DestinyTileType.WISDOM -> R.string.tile_wisdom_label
    DestinyTileType.HEALTH -> R.string.tile_health_label
    DestinyTileType.DOSHA -> R.string.doshas
}

/**
 * Stable snake_case semantics id for E2E test targeting — matches iOS
 * accessibilityIdentifier conventions ("magic_tab_wealth", etc.).
 */
private fun DestinyTileType.semanticsId(): String = "magic_tab_${name.lowercase()}"

/**
 * Get first name from a full name — mirrors iOS getFirstName(from:) which splits
 * the name on whitespace and returns the first segment (or the original string
 * if it contains no whitespace).
 */
internal fun getFirstName(name: String): String = name.substringBefore(' ')

/**
 * ProfileSwitcher — mirrors iOS Compatibility/Components/MagicTabbar.swift ProfileSwitcher.
 * A sliding gold pill toggle for switching between partner names. The active pill
 * animates its position via animateDpAsState (Compose analogue of SwiftUI's
 * matchedGeometryEffect). Each toggle button emits a medium haptic on tap and
 * displays only the first name of the supplied partner.
 */
@Composable
internal fun ProfileSwitcher(
    selectedIndex: Int,
    names: List<String>,
    onSelect: (Int) -> Unit,
) {
    if (names.isEmpty()) return
    val haptics = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
            .padding(4.dp)
            .semantics { contentDescription = "profile_switcher" },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        names.forEachIndexed { index, name ->
            val isSelected = selectedIndex == index
            val firstName = getFirstName(name).ifEmpty { name }

            // Animated background — sliding gold pill effect (Compose analogue of matchedGeometryEffect)
            val pillElevation by animateDpAsState(
                targetValue = if (isSelected) 12.dp else 0.dp,
                animationSpec = tween(durationMillis = 300),
                label = "profile_switcher_elevation",
            )
            val labelColor by animateColorAsState(
                targetValue = if (isSelected) Color(0xFF0D0D1A) else CreamDim,
                animationSpec = tween(durationMillis = 300),
                label = "profile_switcher_label",
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .then(
                        if (isSelected) {
                            Modifier
                                .shadow(
                                    elevation = pillElevation,
                                    shape = RoundedCornerShape(20.dp),
                                    ambientColor = Gold,
                                    spotColor = Gold,
                                )
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Gold, Gold.copy(alpha = 0.85f)),
                                    ),
                                    RoundedCornerShape(20.dp),
                                )
                        } else {
                            Modifier
                        },
                    )
                    .clickable {
                        if (selectedIndex != index) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSelect(index)
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics { contentDescription = "profile_switcher_option_$index" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = firstName,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = labelColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}
