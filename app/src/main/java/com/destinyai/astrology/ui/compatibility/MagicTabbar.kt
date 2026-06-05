package com.destinyai.astrology.ui.compatibility

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.domain.model.DestinyTileType
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface

/**
 * Weighted-Row tab bar — mirrors iOS Compatibility/Components/MagicTabbar.swift.
 * Each tab takes equal width (weight = 1f) so the tab strip is balanced rather
 * than scrollable. Active tab gets uppercase + tracking text styling and a
 * 3.dp animated indicator dot below the label, with smooth color/size animation.
 */
@Composable
internal fun MagicTabbar(
    tabs: List<DestinyTileType>,
    selectedTab: DestinyTileType,
    tileCounts: Map<DestinyTileType, Int>,
    onSelect: (DestinyTileType) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            val isSelected = selectedTab == tab
            val count = tileCounts[tab] ?: 0
            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) Gold else NavySurface,
                animationSpec = tween(durationMillis = 300),
                label = "magic_tab_bg",
            )
            val borderColor by animateColorAsState(
                targetValue = if (isSelected) Gold else Gold.copy(alpha = 0.2f),
                animationSpec = tween(durationMillis = 300),
                label = "magic_tab_border",
            )
            val labelColor by animateColorAsState(
                targetValue = if (isSelected) Color(0xFF0D0D1A) else CreamDim,
                animationSpec = tween(durationMillis = 300),
                label = "magic_tab_label",
            )
            val indicatorSize by animateDpAsState(
                targetValue = if (isSelected) 3.dp else 0.dp,
                animationSpec = tween(durationMillis = 300),
                label = "magic_tab_dot",
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(backgroundColor)
                    .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(text = tab.icon, fontSize = 13.sp)
                    Text(
                        text = tab.label.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        letterSpacing = 1.sp,
                        color = labelColor,
                        textAlign = TextAlign.Center,
                    )
                    if (count > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) Color(0xFF0D0D1A).copy(alpha = 0.2f)
                                    else Gold.copy(alpha = 0.15f),
                                )
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "$count",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color(0xFF0D0D1A) else Gold,
                            )
                        }
                    }
                }
                // Animated active indicator dot — mirrors iOS sliding 3.pt dot with shadow
                Box(
                    modifier = Modifier
                        .size(indicatorSize)
                        .shadow(
                            elevation = if (isSelected) 4.dp else 0.dp,
                            shape = CircleShape,
                            ambientColor = Color(0xFF0D0D1A),
                            spotColor = Color(0xFF0D0D1A),
                        )
                        .clip(CircleShape)
                        .background(
                            if (isSelected) Color(0xFF0D0D1A).copy(alpha = 0.6f)
                            else Color.Transparent,
                        ),
                )
            }
        }
    }
}
