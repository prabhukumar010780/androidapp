package com.destinyai.astrology.ui.compatibility

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.R
import com.destinyai.astrology.domain.model.CompatibilityHistoryItem
import com.destinyai.astrology.domain.model.ComparisonGroup
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.NavyVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompatibilityHistoryScreen(
    viewModel: CompatibilityViewModel,
    onBack: () -> Unit,
    onGroupSelect: ((ComparisonGroup) -> Unit)? = null,
    onItemSelect: ((CompatibilityHistoryItem) -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
) {
    val items by viewModel.historyItems.collectAsStateWithLifecycle()
    val isHistoryEnabled by viewModel.isHistoryEnabled.collectAsStateWithLifecycle()
    var searchText by remember { mutableStateOf("") }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.loadHistory() }

    val filtered = remember(items, searchText) {
        if (searchText.isBlank()) items
        else items.filter {
            historySearchFilter(searchText, boyName = it.boyName, girlName = it.girlName, userName = it.boyName)
        }
    }

    // Group items by comparisonGroupId (or boyName as fallback) — mirrors iOS ComparisonGroup logic
    // Pinned groups float to top, then sorted by most recent timestamp
    val groups = remember(filtered) {
        filtered
            .groupBy { it.comparisonGroupId ?: it.boyName }
            .map { (_, groupItems) ->
                ComparisonGroup(
                    userName = groupItems.first().boyName,
                    items = groupItems.sortedByDescending { it.totalScore },
                    timestamp = groupItems.maxOf { it.timestampMs },
                )
            }
            .sortedWith(
                compareByDescending<ComparisonGroup> { g -> g.items.any { it.isPinned } }
                    .thenByDescending { it.timestamp }
            )
    }

    CosmicBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .semantics(mergeDescendants = true) { contentDescription = "history_screen" },
        ) {
            // Delete confirmation dialog
            if (pendingDeleteId != null) {
                AlertDialog(
                    onDismissRequest = { pendingDeleteId = null },
                    title = { Text(stringResource(R.string.compat_history_delete_match_title)) },
                    text = { Text(stringResource(R.string.compat_history_delete_match_message)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.deleteHistoryItem(pendingDeleteId!!)
                                pendingDeleteId = null
                            },
                        ) {
                            Text(stringResource(R.string.delete_action), color = Color(0xFFFC8181))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDeleteId = null }) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )
            }
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = CreamText)
                }
                Text(
                    text = stringResource(R.string.match_history_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CreamText,
                    modifier = Modifier.weight(1f),
                )
            }

            if (!isHistoryEnabled) {
                HistoryDisabledState(
                    modifier = Modifier.weight(1f),
                    onOpenSettings = onOpenSettings,
                )
            } else if (items.isEmpty()) {
                HistoryEmptyState(modifier = Modifier.weight(1f))
            } else {
                // Search bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = CreamDim, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = CreamText),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (searchText.isEmpty()) {
                                Text(stringResource(R.string.search_matches_placeholder), style = MaterialTheme.typography.bodyMedium, color = CreamDim.copy(alpha = 0.6f))
                            }
                            inner()
                        },
                    )
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { searchText = "" }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_clear_search), tint = CreamDim, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                if (filtered.isEmpty()) {
                    val emptyMsg = if (searchText.isBlank()) {
                        stringResource(R.string.history_no_compatibility_yet)
                    } else {
                        stringResource(R.string.no_results_found)
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(emptyMsg, style = MaterialTheme.typography.bodyMedium, color = CreamDim)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(groups, key = { it.id }) { group ->
                            if (group.partnerCount > 1) {
                                SwipeToDeleteGroupRow(
                                    group = group,
                                    onTap = { onGroupSelect?.invoke(group) },
                                    onDeleteRequest = { item -> pendingDeleteId = item.sessionId },
                                    onPin = { item -> viewModel.toggleHistoryPin(item.sessionId) },
                                )
                            } else {
                                val item = group.items.first()
                                val maxGroupScore = group.items.maxOfOrNull { it.totalScore } ?: 0
                                SwipeToDeleteHistoryItem(
                                    item = item,
                                    onTap = { onItemSelect?.invoke(item) },
                                    onPin = { viewModel.toggleHistoryPin(item.sessionId) },
                                    onDeleteRequest = { pendingDeleteId = item.sessionId },
                                    isBestInGroup = item.totalScore >= maxGroupScore && maxGroupScore > 0,
                                )
                            }
                        }
                        item { Spacer(Modifier.height(32.dp)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteHistoryItem(
    item: CompatibilityHistoryItem,
    onTap: () -> Unit = {},
    onPin: () -> Unit,
    onDeleteRequest: () -> Unit,
    isBestInGroup: Boolean = false,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> onDeleteRequest()
                SwipeToDismissBoxValue.StartToEnd -> onPin()
                else -> Unit
            }
            false // Always return false — we manually handle actions; do not let row dismiss
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        // Both edges enabled — leading=Pin (gold), trailing=Delete (red)
        backgroundContent = {
            val isPinSwipe = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val bg = if (isPinSwipe) Gold.copy(alpha = 0.85f) else Color(0xFFFC8181).copy(alpha = 0.85f)
            val align = if (isPinSwipe) Alignment.CenterStart else Alignment.CenterEnd
            val pad = if (isPinSwipe) PaddingValues(start = 20.dp) else PaddingValues(end = 20.dp)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .padding(pad),
                contentAlignment = align,
            ) {
                if (isPinSwipe) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = if (item.isPinned) stringResource(R.string.cd_unpin) else stringResource(R.string.cd_pin),
                        tint = Color(0xFF0D0D1A),
                    )
                } else {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cd_delete), tint = Color.White)
                }
            }
        },
    ) {
        HistoryItemRow(item = item, onPin = onPin, onTap = onTap, isBestInGroup = isBestInGroup, onDeleteRequest = onDeleteRequest)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteGroupRow(
    group: ComparisonGroup,
    onTap: () -> Unit,
    onDeleteRequest: (CompatibilityHistoryItem) -> Unit,
    onPin: (CompatibilityHistoryItem) -> Unit,
) {
    val firstItem = group.items.firstOrNull()
    val pinTarget = group.items.firstOrNull { it.isPinned } ?: group.bestMatch ?: firstItem
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> firstItem?.let { onDeleteRequest(it) }
                SwipeToDismissBoxValue.StartToEnd -> pinTarget?.let { onPin(it) }
                else -> Unit
            }
            false
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val isPinSwipe = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val bg = if (isPinSwipe) Gold.copy(alpha = 0.85f) else Color(0xFFFC8181).copy(alpha = 0.85f)
            val align = if (isPinSwipe) Alignment.CenterStart else Alignment.CenterEnd
            val pad = if (isPinSwipe) PaddingValues(start = 20.dp) else PaddingValues(end = 20.dp)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .padding(pad),
                contentAlignment = align,
            ) {
                if (isPinSwipe) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = if (pinTarget?.isPinned == true) stringResource(R.string.cd_unpin) else stringResource(R.string.cd_pin),
                        tint = Color(0xFF0D0D1A),
                    )
                } else {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cd_delete), tint = Color.White)
                }
            }
        },
    ) {
        GroupHistoryRow(
            group = group,
            onTap = onTap,
            onDeleteRequest = onDeleteRequest,
            onPin = onPin,
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HistoryItemRow(
    item: CompatibilityHistoryItem,
    onPin: () -> Unit,
    onTap: () -> Unit = {},
    isBestInGroup: Boolean = false,
    onDeleteRequest: () -> Unit = {},
) {
    val scoreColor = when {
        item.scorePercentage >= 70 -> Color(0xFF48BB78)
        item.scorePercentage >= 50 -> Color(0xFFED8936)
        else -> Color(0xFFFC8181)
    }
    var showContextMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NavySurface)
            .border(1.dp, Gold.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onTap,
                onLongClick = { showContextMenu = true },
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Score badge
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(scoreColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${item.totalScore}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor,
                    lineHeight = 20.sp,
                )
                Text(
                    text = "/${item.maxScore}",
                    style = MaterialTheme.typography.labelSmall,
                    color = CreamDim,
                    lineHeight = 14.sp,
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.isPinned) {
                    Icon(Icons.Filled.PushPin, contentDescription = null, tint = Gold, modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(4.dp))
                }
                // R2-CM15: Best-score star indicator
                if (isBestInGroup) {
                    Icon(Icons.Filled.Star, contentDescription = stringResource(R.string.compat_history_best_match_cd), tint = Gold, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = item.displayTitle,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = CreamText,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = item.displayDate,
                style = MaterialTheme.typography.labelSmall,
                color = CreamDim,
            )
        }

        Spacer(Modifier.width(8.dp))

        // Pin toggle
        IconButton(onClick = onPin, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Filled.PushPin,
                contentDescription = if (item.isPinned) stringResource(R.string.cd_unpin) else stringResource(R.string.cd_pin),
                tint = if (item.isPinned) Gold else CreamDim.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp),
            )
        }

        // R2-CM17: Chat message count bubble (follow-up questions)
        val msgCount = userMessageCount(item.chatMessages)
        if (msgCount > 0) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Gold.copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(
                    text = "$msgCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = Gold,
                )
            }
            Spacer(Modifier.width(4.dp))
        }

        // R2-CM16: Long-press context menu (pin/unpin)
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text(if (item.isPinned) stringResource(R.string.unpin) else stringResource(R.string.pin)) },
                onClick = {
                    showContextMenu = false
                    onPin()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete_action), color = Color(0xFFFC8181)) },
                onClick = {
                    showContextMenu = false
                    onDeleteRequest()
                },
            )
        }
    }
}

@Composable
private fun HistoryDisabledState(
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("🕐", fontSize = 56.sp)
            Text(
                text = stringResource(R.string.compat_history_disabled_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = CreamText,
            )
            Text(
                text = stringResource(R.string.compat_history_disabled_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = CreamDim,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            if (onOpenSettings != null) {
                OutlinedButton(
                    onClick = onOpenSettings,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Gold.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Gold),
                ) {
                    Text(stringResource(R.string.open_settings))
                }
            }
        }
    }
}

@Composable
private fun HistoryEmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🕐", fontSize = 60.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.compat_history_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = CreamText,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.compat_history_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = CreamDim,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )
        }
    }
}

// ─── Group History Row (iOS GroupHistoryRow) ──────────────────────────────────

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun GroupHistoryRow(
    group: ComparisonGroup,
    onTap: () -> Unit = {},
    onDeleteRequest: (CompatibilityHistoryItem) -> Unit = {},
    onPin: (CompatibilityHistoryItem) -> Unit = {},
) {
    val best = group.bestMatch
    val scoreColor = when {
        (best?.scorePercentage ?: 0.0) >= 70 -> Color(0xFF48BB78)
        (best?.scorePercentage ?: 0.0) >= 50 -> Color(0xFFED8936)
        else -> Color(0xFFFC8181)
    }
    var showContextMenu by remember { mutableStateOf(false) }
    val pinTarget = group.items.firstOrNull { it.isPinned } ?: best ?: group.items.firstOrNull()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NavySurface)
            .border(1.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onTap,
                onLongClick = { showContextMenu = true },
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics { contentDescription = "history_group_row" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // R2-CM16: Long-press context menu (Pin/Unpin + Delete) — group parity
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text(if (pinTarget?.isPinned == true) stringResource(R.string.unpin) else stringResource(R.string.pin)) },
                onClick = {
                    showContextMenu = false
                    pinTarget?.let { onPin(it) }
                },
                modifier = Modifier.semantics { contentDescription = "history_group_pin_action" },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete_action), color = Color(0xFFFC8181)) },
                onClick = {
                    showContextMenu = false
                    pinTarget?.let { onDeleteRequest(it) }
                },
                modifier = Modifier.semantics { contentDescription = "history_group_delete_action" },
            )
        }
        // R2-CM14: Group icon with partner count overlay
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(Gold.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Group,
                contentDescription = null,
                tint = Gold,
                modifier = Modifier.size(26.dp),
            )
            // Count badge overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Gold),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${group.partnerCount}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0D0D1A),
                    fontSize = 9.sp,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.displayTitle,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = CreamText,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(
                    R.string.compat_history_partners_best_format,
                    group.partnerCount,
                    best?.girlName ?: stringResource(R.string.compat_history_partners_best_dash),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = CreamDim,
            )
            Text(
                text = group.displayDate,
                style = MaterialTheme.typography.labelSmall,
                color = CreamDim,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (best != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(scoreColor.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${best.totalScore}/${best.maxScore}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor,
                )
            }
        }
    }
}

// Thin wrapper so LazyColumn doesn't need `androidx.compose.foundation.text` import
@Composable
private fun BasicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean,
    textStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier,
    decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit,
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = textStyle,
        modifier = modifier,
        decorationBox = decorationBox,
    )
}

// Pure helpers — unit testable

internal fun historySearchFilter(
    query: String,
    boyName: String,
    girlName: String,
    userName: String,
): Boolean {
    if (query.isBlank()) return true
    return boyName.contains(query, ignoreCase = true) ||
        girlName.contains(query, ignoreCase = true) ||
        userName.contains(query, ignoreCase = true)
}

internal fun userMessageCount(messages: List<com.destinyai.astrology.domain.model.CompatChatMessageData>): Int =
    messages.count { it.isUser }

internal fun emptyHistoryMessage(searchText: String): String =
    if (searchText.isBlank()) "No compatibility history yet" else "No results found"
