package com.destinyai.astrology.ui.compatibility

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.domain.model.DestinyTileType
import com.destinyai.astrology.domain.model.VariantCounter
import com.destinyai.astrology.domain.model.YogaItem
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface

private val SuccessGreen = Color(0xFF48BB78)
private val ErrorRed = Color(0xFFFC8181)
private val ReducedAmber = Color(0xFFED8936)

@Composable
fun TopicListView(
    tile: DestinyTileType,
    items: List<YogaItem>,
    personName: String,
    modifier: Modifier = Modifier,
    key: String = "",
) {
    val deduped = remember(items, key) { VariantCounter.calculatePositions(items) }
    val activeItems = remember(deduped, key) { deduped.filter { it.isActive }.sortedByDescending { it.strengthRaw } }
    val blockedItems = remember(deduped, key) { deduped.filter { !it.isActive } }
    var expandedIds by remember { mutableStateOf(emptySet<String>()) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        // Header with tile icon + item counts (iOS parity)
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(tile.icon, fontSize = 18.sp)
                    Text(
                        tile.label.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Gold.copy(alpha = 0.7f),
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    if (activeItems.isNotEmpty() || blockedItems.isNotEmpty()) {
                        Text(
                            "${activeItems.size + blockedItems.size} yogas",
                            fontSize = 11.sp,
                            color = CreamDim.copy(alpha = 0.6f),
                        )
                    }
                }
                Text(
                    personName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = CreamText,
                )
            }
        }

        // Active yogas
        if (activeItems.isNotEmpty()) {
            item {
                Text(
                    "ACTIVE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SuccessGreen.copy(alpha = 0.7f),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(activeItems, key = { it.id }) { yoga ->
                ActiveYogaCard(
                    item = yoga,
                    isExpanded = expandedIds.contains(yoga.id),
                    onTap = {
                        expandedIds = if (expandedIds.contains(yoga.id))
                            expandedIds - yoga.id
                        else
                            expandedIds + yoga.id
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        // Blocked/Reduced yogas
        if (blockedItems.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "BLOCKED / CANCELLED",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ErrorRed.copy(alpha = 0.7f),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(blockedItems, key = { it.id }) { yoga ->
                BlockedYogaCard(
                    item = yoga,
                    isExpanded = expandedIds.contains(yoga.id),
                    onTap = {
                        expandedIds = if (expandedIds.contains(yoga.id))
                            expandedIds - yoga.id
                        else
                            expandedIds + yoga.id
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        if (activeItems.isEmpty() && blockedItems.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No yogas found for this topic.", fontSize = 14.sp, color = CreamDim)
                }
            }
        }
    }
}

@Composable
private fun ActiveYogaCard(
    item: YogaItem,
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.displayName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = CreamText,
                )
                if (!item.category.isNullOrBlank()) {
                    Text(
                        item.category,
                        fontSize = 10.sp,
                        color = CreamDim.copy(alpha = 0.6f),
                        letterSpacing = 0.3.sp,
                    )
                }
                if (item.strengthRaw > 0) {
                    Row(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(SuccessGreen.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Strength: ${item.strengthRaw.toInt()}%", fontSize = 11.sp, color = SuccessGreen)
                    }
                }
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = CreamDim,
                modifier = Modifier.size(20.dp),
            )
        }

        if (isExpanded) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
            Spacer(Modifier.height(10.dp))

            // Outcome box (iOS localizedOutcome accent box)
            item.localizedOutcome?.takeIf { it.isNotEmpty() }?.let { outcome ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SuccessGreen.copy(alpha = 0.08f))
                        .border(1.dp, SuccessGreen.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                ) {
                    Text(outcome, fontSize = 13.sp, color = SuccessGreen, lineHeight = 20.sp)
                }
                Spacer(Modifier.height(8.dp))
            }

            // Formation (how it forms)
            item.localizedFormation?.takeIf { it.isNotEmpty() }?.let { formation ->
                Text("Formation", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = CreamDim, letterSpacing = 0.5.sp)
                Spacer(Modifier.height(2.dp))
                Text(formation, fontSize = 13.sp, color = CreamDim, lineHeight = 20.sp)
                Spacer(Modifier.height(8.dp))
            }

            // Planet + House chips
            val planetStr = item.uniquePlanets
            val houseStr = item.uniqueHouses
            if (planetStr != null || houseStr != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    planetStr?.let { ChipLabel(it, Gold) }
                    houseStr?.let { ChipLabel(it, Gold.copy(alpha = 0.7f)) }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Reason for "R" status
            if (item.status == "R") {
                item.reason?.takeIf { it.isNotEmpty() }?.let { reason ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ReducedAmber.copy(alpha = 0.08f))
                            .border(1.dp, ReducedAmber.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                    ) {
                        Text(reason, fontSize = 13.sp, color = ReducedAmber, lineHeight = 20.sp)
                    }
                }
            }

            // Legacy description fallback
            if (item.localizedOutcome.isNullOrEmpty() && item.description.isNotEmpty()) {
                Text(item.description, fontSize = 13.sp, color = CreamDim, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
private fun BlockedYogaCard(
    item: YogaItem,
    isExpanded: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
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
            Text("✕", fontSize = 14.sp, color = ErrorRed.copy(alpha = 0.6f))
            Text(
                item.displayName,
                fontSize = 14.sp,
                color = CreamDim,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = CreamDim.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp),
            )
        }
        if (isExpanded) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = ErrorRed.copy(alpha = 0.15f))
            Spacer(Modifier.height(10.dp))

            // Outcome
            item.localizedOutcome?.takeIf { it.isNotEmpty() }?.let { outcome ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ErrorRed.copy(alpha = 0.06f))
                        .border(1.dp, ErrorRed.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                ) {
                    Text(outcome, fontSize = 13.sp, color = ErrorRed.copy(alpha = 0.8f), lineHeight = 20.sp)
                }
                Spacer(Modifier.height(8.dp))
            }

            // Why inactive (reason field)
            item.reason?.takeIf { it.isNotEmpty() }?.let { reason ->
                Text("Why Inactive", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = CreamDim, letterSpacing = 0.5.sp)
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .padding(10.dp),
                ) {
                    Text(reason, fontSize = 13.sp, color = CreamDim, lineHeight = 20.sp)
                }
                Spacer(Modifier.height(8.dp))
            }

            // Formation
            item.localizedFormation?.takeIf { it.isNotEmpty() }?.let { formation ->
                Text("Formation", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = CreamDim, letterSpacing = 0.5.sp)
                Spacer(Modifier.height(2.dp))
                Text(formation, fontSize = 13.sp, color = CreamDim, lineHeight = 20.sp)
                Spacer(Modifier.height(8.dp))
            }

            // Planet + House chips
            val planetStr = item.uniquePlanets
            val houseStr = item.uniqueHouses
            if (planetStr != null || houseStr != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    planetStr?.let { ChipLabel(it, CreamDim) }
                    houseStr?.let { ChipLabel(it, CreamDim.copy(alpha = 0.7f)) }
                }
            }

            // Legacy description fallback
            if (item.reason.isNullOrEmpty() && item.localizedOutcome.isNullOrEmpty() && item.description.isNotEmpty()) {
                Text(item.description, fontSize = 13.sp, color = CreamDim, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
private fun ChipLabel(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, fontSize = 11.sp, color = color)
    }
}
