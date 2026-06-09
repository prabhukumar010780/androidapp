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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.destinyai.astrology.services.AppEvents
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.NavyVariant
import com.destinyai.astrology.ui.theme.TextTertiary
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    var pendingDeleteTitle by remember { mutableStateOf("") }
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.loadHistory() }

    // iOS parity: NotificationCenter.default.publisher(for: .openProfileSettings)
    // routes external "open settings" signals (deep-link, notification taps,
    // cross-screen prompts) into this surface even when the caller did not wire
    // a direct onOpenSettings callback. Mirrors the bridge in
    // CompatibilityResultScreen so every compatibility surface honours the
    // global SharedFlow bus identically.
    val appEvents = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            CompatHistoryAppEventsEntryPoint::class.java,
        ).appEvents()
    }
    LaunchedEffect(appEvents, onOpenSettings) {
        appEvents.openProfileSettings.collect {
            onOpenSettings?.invoke()
        }
    }

    val filtered = remember(items, searchText) {
        if (searchText.isBlank()) items
        else items.filter {
            historySearchFilter(searchText, boyName = it.boyName, girlName = it.girlName, userName = it.boyName)
        }
    }

    // Mirror iOS CompatibilityHistoryService.loadGroups():
    // - Items WITH a comparisonGroupId are grouped together (multi-partner analysis)
    // - Items WITHOUT a comparisonGroupId each become their own single-item group
    // Using boyName as fallback key (old logic) incorrectly merged all of one user's
    // single matches into one group.
    val groups = remember(filtered) {
        val grouped = mutableMapOf<String, MutableList<CompatibilityHistoryItem>>()
        val ungrouped = mutableListOf<CompatibilityHistoryItem>()
        for (item in filtered) {
            val gid = item.comparisonGroupId
            if (gid != null) {
                grouped.getOrPut(gid) { mutableListOf() }.add(item)
            } else {
                ungrouped.add(item)
            }
        }
        val result = mutableListOf<ComparisonGroup>()
        // Multi-partner groups — sort partners by partnerIndex within group
        for ((gid, groupItems) in grouped) {
            result.add(ComparisonGroup(
                id = gid,
                userName = groupItems.first().boyName,
                items = groupItems.sortedBy { it.partnerIndex ?: 0 },
                timestamp = groupItems.minOf { it.timestampMs },
            ))
        }
        // Single matches — each becomes its own group (id = sessionId, mirrors iOS)
        for (item in ungrouped) {
            result.add(ComparisonGroup(
                id = item.sessionId,
                userName = item.boyName,
                items = listOf(item),
                timestamp = item.timestampMs,
            ))
        }
        result.sortedWith(
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
                    text = {
                        Text(
                            stringResource(
                                R.string.compat_history_delete_match_message_format,
                                pendingDeleteTitle,
                            ),
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
                    .statusBarsPadding()
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
                                    onDeleteRequest = { item ->
                                        pendingDeleteId = item.sessionId
                                        pendingDeleteTitle = group.displayTitle
                                    },
                                    onPin = { item ->
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.toggleHistoryPin(item.sessionId)
                                    },
                                    modifier = Modifier.animateItemPlacement(),
                                )
                            } else {
                                val item = group.items.first()
                                SwipeToDeleteHistoryItem(
                                    item = item,
                                    onTap = { onItemSelect?.invoke(item) },
                                    onPin = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.toggleHistoryPin(item.sessionId)
                                    },
                                    onDeleteRequest = {
                                        pendingDeleteId = item.sessionId
                                        pendingDeleteTitle = item.displayTitle
                                    },
                                    modifier = Modifier.animateItemPlacement(),
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
    modifier: Modifier = Modifier,
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
        modifier = modifier,
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
        HistoryItemRow(item = item, onPin = onPin, onTap = onTap, onDeleteRequest = onDeleteRequest)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteGroupRow(
    group: ComparisonGroup,
    onTap: () -> Unit,
    onDeleteRequest: (CompatibilityHistoryItem) -> Unit,
    onPin: (CompatibilityHistoryItem) -> Unit,
    modifier: Modifier = Modifier,
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
        modifier = modifier,
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
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics { contentDescription = "history_item_row" },
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

        // Trailing chevron — iOS parity (CompatibilityHistorySheet.swift line 445)
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.cd_chevron_right),
            tint = TextTertiary,
            modifier = Modifier.size(16.dp),
        )

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
        // iOS parity: 3-person icon with stacked count inside the circle (no badge overlay).
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(Gold.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.Group,
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "${group.partnerCount}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Gold,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            // iOS parity: full partner list joined by commas — "User + Partner1, Partner2".
            val partnerNames = group.items.joinToString(", ") { it.girlName }
            Text(
                text = stringResource(
                    R.string.compat_history_group_title_format,
                    group.userName,
                    partnerNames,
                ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = CreamText,
                maxLines = 1,
            )
            Spacer(Modifier.height(3.dp))
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
            Spacer(Modifier.width(8.dp))
        }
        // Trailing chevron — iOS parity (CompatibilityHistorySheet.swift line 373).
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.cd_chevron_right),
            tint = TextTertiary,
            modifier = Modifier.size(16.dp),
        )
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

/**
 * Hilt entry point that exposes the singleton [AppEvents] bus to this
 * non-Hilt history composable. Mirrors [CompatResultAppEventsEntryPoint] in
 * CompatibilityResultScreen and lets the screen subscribe to
 * [AppEvents.openProfileSettings] without adding the bus to
 * [CompatibilityViewModel]'s constructor.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface CompatHistoryAppEventsEntryPoint {
    fun appEvents(): AppEvents
}
