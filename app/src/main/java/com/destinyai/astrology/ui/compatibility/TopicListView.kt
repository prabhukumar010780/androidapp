package com.destinyai.astrology.ui.compatibility

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.R
import com.destinyai.astrology.domain.model.DestinyTileType
import com.destinyai.astrology.domain.model.VariantCounter
import com.destinyai.astrology.domain.model.YogaItem
import com.destinyai.astrology.ui.theme.AppTheme
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavyDeep
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.PlayfairFontFamily
import com.destinyai.astrology.util.DoshaDescriptions

private val SuccessGreen = Color(0xFF48BB78)
private val ErrorRed = Color(0xFFFC8181)
private val ReducedAmber = Color(0xFFED8936)

/**
 * Maps a DestinyTileType to its active glyph drawable — mirrors iOS
 * `tile.activeIconImage` SVG used in the header glow.
 */
private fun DestinyTileType.activeIconRes(): Int = when (this) {
    DestinyTileType.WEALTH -> R.drawable.ic_yoga_wealth_active
    DestinyTileType.CAREER -> R.drawable.ic_yoga_career_active
    DestinyTileType.LOVE -> R.drawable.ic_yoga_love_active
    DestinyTileType.FAMILY -> R.drawable.ic_yoga_family_active
    DestinyTileType.WISDOM -> R.drawable.ic_yoga_wisdom_active
    DestinyTileType.HEALTH -> R.drawable.ic_yoga_health_active
    DestinyTileType.DOSHA -> R.drawable.ic_yoga_challenges_active
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TopicListView(
    tile: DestinyTileType,
    items: List<YogaItem>,
    personName: String,
    modifier: Modifier = Modifier,
    key: String = "",
) {
    val deduped = remember(items, key) { VariantCounter.calculatePositions(items) }
    // iOS parity: active = status != "C" (so both A and R are active);
    // blocked = status == "C". YogaItem.isActive treats R as inactive — bypass it here.
    val activeItems = remember(deduped, key) {
        deduped.filter { it.status != "C" }.sortedByDescending { it.strengthRaw }
    }
    val blockedItems = remember(deduped, key) { deduped.filter { it.status == "C" } }
    var expandedIds by remember { mutableStateOf(emptySet<String>()) }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "topic_list_${tile.name.lowercase()}" },
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        // Header — SVG tile icon with glow + serif large title (iOS parity).
        // personName intentionally omitted to match iOS.
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .semantics { contentDescription = "topic_header" },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // SVG tile glyph with shadow glow
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = tile.accentColor.copy(alpha = 0.18f),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(id = tile.activeIconRes()),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(40.dp),
                    )
                }
                // Serif large title — iOS uses .serif 20sp bold
                Text(
                    tile.label,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = PlayfairFontFamily,
                    color = CreamText,
                )
                // Counts row
                if (activeItems.isNotEmpty() || blockedItems.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "${activeItems.size} ${stringResource(R.string.status_active)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tile.accentColor,
                        )
                        if (blockedItems.isNotEmpty()) {
                            Text(
                                "•",
                                fontSize = 13.sp,
                                color = AppTheme.colors.textTertiary,
                            )
                            Text(
                                "${blockedItems.size} ${stringResource(R.string.status_inactive)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = AppTheme.colors.textSecondary,
                            )
                        }
                    }
                }
            }
        }

        // Active yogas — sticky header (iOS pinnedViews parity)
        if (activeItems.isNotEmpty()) {
            stickyHeader {
                SectionHeader(
                    icon = Icons.Filled.AutoAwesome,
                    title = stringResource(R.string.active_factors_title),
                    count = activeItems.size,
                    color = tile.accentColor,
                    modifier = Modifier
                        .background(NavyDeep)
                        .semantics { contentDescription = "active_section_header" },
                )
            }
            items(activeItems, key = { "active_${it.id}" }) { yoga ->
                ActiveYogaCard(
                    item = yoga,
                    tile = tile,
                    isExpanded = expandedIds.contains(yoga.id),
                    onTap = {
                        expandedIds = if (expandedIds.contains(yoga.id)) {
                            expandedIds - yoga.id
                        } else {
                            expandedIds + yoga.id
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        // Blocked / Cancelled yogas — sticky header
        if (blockedItems.isNotEmpty()) {
            stickyHeader {
                SectionHeader(
                    icon = Icons.Filled.Block,
                    title = stringResource(R.string.inactive_factors_title),
                    count = blockedItems.size,
                    color = AppTheme.colors.textTertiary,
                    modifier = Modifier
                        .background(NavyDeep)
                        .semantics { contentDescription = "inactive_section_header" },
                )
            }
            items(blockedItems, key = { "blocked_${it.id}" }) { yoga ->
                BlockedYogaCard(
                    item = yoga,
                    tile = tile,
                    isExpanded = expandedIds.contains(yoga.id),
                    onTap = {
                        expandedIds = if (expandedIds.contains(yoga.id)) {
                            expandedIds - yoga.id
                        } else {
                            expandedIds + yoga.id
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        if (activeItems.isEmpty() && blockedItems.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp)
                        .semantics { contentDescription = "topic_empty_state" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Tile-specific emoji visual (iOS parity)
                    Text(
                        tile.icon,
                        fontSize = 48.sp,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                    Text(
                        stringResource(R.string.no_yogas_in_category),
                        fontSize = 16.sp,
                        color = AppTheme.colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp),
        )
        Text(
            title.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.colors.textSecondary,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.weight(1f))
        // Count chip
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = 0.12f))
                .border(0.5.dp, color.copy(alpha = 0.25f), RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Text(
                count.toString(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
    }
}

@Composable
private fun StatusCapsule(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .border(0.5.dp, color.copy(alpha = 0.25f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun ActiveYogaCard(
    item: YogaItem,
    tile: DestinyTileType,
    isExpanded: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "yoga_card_${item.name.take(20)}" }
            .clip(RoundedCornerShape(14.dp))
            .background(NavySurface)
            .border(1.dp, SuccessGreen.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .clickable(onClick = onTap)
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Leading sparkles emoji (iOS parity)
            Text("✨", fontSize = 20.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.displayName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = CreamText,
                )
                // Skip category when it duplicates the current tile name
                if (!item.category.isNullOrBlank() &&
                    !item.category.equals(tile.name, ignoreCase = true) &&
                    !item.category.equals(tile.label, ignoreCase = true)
                ) {
                    Text(
                        item.category,
                        fontSize = 10.sp,
                        color = AppTheme.colors.textTertiary,
                        letterSpacing = 0.3.sp,
                    )
                }
            }
            // Status capsule badge: A -> Active (green), R -> Reduced (gold)
            when (item.status.uppercase()) {
                "A" -> StatusCapsule(stringResource(R.string.status_active), SuccessGreen)
                "R" -> StatusCapsule(stringResource(R.string.status_reduced), Gold)
                else -> {}
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = AppTheme.colors.textTertiary,
                modifier = Modifier.size(20.dp),
            )
        }

        if (isExpanded) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
            Spacer(Modifier.height(10.dp))

            // Outcome accent box — OUTCOME header with sparkles + textPrimary body (iOS parity)
            item.localizedOutcome?.takeIf { it.isNotEmpty() }?.let { outcome ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = tile.accentColor,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        stringResource(R.string.yoga_outcome_label).uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = tile.accentColor,
                        letterSpacing = 1.sp,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(tile.accentColor.copy(alpha = 0.08f))
                        .border(1.dp, tile.accentColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                ) {
                    Text(
                        outcome,
                        fontSize = 13.sp,
                        color = AppTheme.colors.textPrimary,
                        lineHeight = 20.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            // Formation — info icon + localized header
            item.localizedFormation?.takeIf { it.isNotEmpty() }?.let { formation ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = AppTheme.colors.textTertiary,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        stringResource(R.string.yoga_formation_label).uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textTertiary,
                        letterSpacing = 1.sp,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    formation,
                    fontSize = 13.sp,
                    color = AppTheme.colors.textSecondary,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(8.dp))
            }

            // Planet + House chips — sparkle prefix on planets, H prefix + house icon on houses
            val planetStr = item.uniquePlanets
            val houseStr = item.uniqueHouses
            if (planetStr != null || houseStr != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    planetStr?.let {
                        DetailChip(
                            icon = Icons.Filled.AutoAwesome,
                            text = it,
                            color = tile.accentColor,
                        )
                    }
                    houseStr?.let {
                        DetailChip(
                            icon = Icons.Filled.Home,
                            text = "H$it",
                            color = AppTheme.colors.textSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Reason for "R" status — warning icon + localized header + exception-key translation
            if (item.status == "R") {
                item.reason?.takeIf { it.isNotEmpty() }?.let { reason ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = ReducedAmber,
                            modifier = Modifier.size(11.dp),
                        )
                        Text(
                            stringResource(R.string.yoga_why_reduced).uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = ReducedAmber,
                            letterSpacing = 1.sp,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ReducedAmber.copy(alpha = 0.08f))
                            .border(1.dp, ReducedAmber.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                    ) {
                        Text(
                            DoshaDescriptions.localizeExceptionKeys(reason),
                            fontSize = 13.sp,
                            color = ReducedAmber.copy(alpha = 0.9f),
                            lineHeight = 20.sp,
                        )
                    }
                }
            }
            // Description fallback removed to match iOS — iOS never renders item.description.
        }
    }
}

@Composable
private fun BlockedYogaCard(
    item: YogaItem,
    tile: DestinyTileType,
    isExpanded: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "blocked_yoga_card_${item.name.take(20)}" }
            .clip(RoundedCornerShape(14.dp))
            .background(ErrorRed.copy(alpha = 0.04f))
            .border(1.dp, ErrorRed.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            .clickable(onClick = onTap)
            .padding(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Circle background with circle-slash (Block) icon — iOS parity
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(AppTheme.colors.inputBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Block,
                    contentDescription = null,
                    tint = AppTheme.colors.textTertiary,
                    modifier = Modifier.size(14.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.displayName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppTheme.colors.textSecondary,
                )
                // Skip category when it duplicates the current tile name
                if (!item.category.isNullOrBlank() &&
                    !item.category.equals(tile.name, ignoreCase = true) &&
                    !item.category.equals(tile.label, ignoreCase = true)
                ) {
                    Text(
                        item.category,
                        fontSize = 10.sp,
                        color = AppTheme.colors.textTertiary,
                        letterSpacing = 0.3.sp,
                    )
                }
            }
            // Localized inactive capsule badge (iOS parity)
            StatusCapsule(stringResource(R.string.status_inactive), AppTheme.colors.textTertiary)
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = AppTheme.colors.textTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
        if (isExpanded) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = ErrorRed.copy(alpha = 0.15f))
            Spacer(Modifier.height(10.dp))

            // Outcome
            item.localizedOutcome?.takeIf { it.isNotEmpty() }?.let { outcome ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = AppTheme.colors.textSecondary,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        stringResource(R.string.yoga_outcome_label).uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textSecondary,
                        letterSpacing = 1.sp,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                ) {
                    Text(
                        outcome,
                        fontSize = 13.sp,
                        color = AppTheme.colors.textPrimary,
                        lineHeight = 20.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            // Why inactive — error tint, xmark.circle icon, localized header, exception-key translation
            item.reason?.takeIf { it.isNotEmpty() }?.let { reason ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        stringResource(R.string.yoga_why_inactive).uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ErrorRed,
                        letterSpacing = 1.sp,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ErrorRed.copy(alpha = 0.08f))
                        .border(1.dp, ErrorRed.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                ) {
                    Text(
                        DoshaDescriptions.localizeExceptionKeys(reason),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = ErrorRed.copy(alpha = 0.9f),
                        lineHeight = 20.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            // Formation (technical details)
            item.localizedFormation?.takeIf { it.isNotEmpty() }?.let { formation ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = AppTheme.colors.textTertiary,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        stringResource(R.string.yoga_formation_label).uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textTertiary,
                        letterSpacing = 1.sp,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    formation,
                    fontSize = 13.sp,
                    color = AppTheme.colors.textSecondary,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(8.dp))
            }

            // Planet + House chips
            val planetStr = item.uniquePlanets
            val houseStr = item.uniqueHouses
            if (planetStr != null || houseStr != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    planetStr?.let {
                        DetailChip(
                            icon = Icons.Filled.AutoAwesome,
                            text = it,
                            color = AppTheme.colors.textSecondary,
                        )
                    }
                    houseStr?.let {
                        DetailChip(
                            icon = Icons.Filled.Home,
                            text = "H$it",
                            color = AppTheme.colors.textTertiary,
                        )
                    }
                }
            }
            // Description fallback removed to match iOS — iOS never renders item.description.
        }
    }
}

@Composable
private fun DetailChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.05f))
            .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(9.dp),
        )
        Text(
            text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = color,
        )
    }
}
