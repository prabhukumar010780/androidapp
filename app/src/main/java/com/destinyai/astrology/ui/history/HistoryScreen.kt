package com.destinyai.astrology.ui.history

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HistoryToggleOff
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.R
import com.destinyai.astrology.domain.model.ChatThread
import com.destinyai.astrology.services.HapticManager
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.WarningOrange
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Mirrors iOS HistoryView.swift:380 — purple tint reserved for grouped match rows.
private val GroupPurple = Color(red = 0.75f, green = 0.55f, blue = 0.95f)

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onChatSelected: ((String) -> Unit)? = null,
    onMatchSelected: ((String) -> Unit)? = null,
    onMatchGroupSelected: ((String) -> Unit)? = null,
    onOpenProfileSettings: (() -> Unit)? = null,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = remember { HapticManager(context) }
    // Mirrors iOS HistoryView.swift:209-236 `handleSelection` — hydrate the full
    // CompatibilityHistoryItem / ComparisonGroup before invoking the navigation
    // callback so callers never receive a stripped lite payload.
    val coroutineScope = rememberCoroutineScope()

    // Mirrors iOS HistoryView.swift:53-58 — destructive actions require explicit
    // confirmation via an alert with title + message + Cancel/Delete buttons.
    var pendingDelete by remember { mutableStateOf<DeleteRequest?>(null) }

    // Snackbar host for surfacing load/mutation errors — mirrors iOS pattern of
    // showing transient error feedback for failed history operations (gap #5).
    val snackbarHostState = remember { SnackbarHostState() }
    val errorFallback = stringResource(R.string.history_load_error)
    val dismissLabel = stringResource(R.string.dismiss)
    LaunchedEffect(state.error) {
        val message = state.error
        if (!message.isNullOrBlank()) {
            val result = snackbarHostState.showSnackbar(
                message = errorFallback,
                actionLabel = dismissLabel,
                duration = SnackbarDuration.Short,
            )
            // Clear after either auto-timeout or user dismiss so the Snackbar can
            // re-fire if the next mutation also fails.
            if (result == SnackbarResult.Dismissed || result == SnackbarResult.ActionPerformed) {
                viewModel.clearError()
            } else {
                viewModel.clearError()
            }
        }
    }

    LaunchedEffect(Unit) { viewModel.loadHistory() }

    // Mirrors iOS HistoryView.swift:211-235 — invoke the navigation callback
    // after a 300ms sheet-dismiss delay so the disappearance animation completes
    // before the next screen pushes in.
    fun navigateAfterDismiss(action: () -> Unit) {
        coroutineScope.launch {
            delay(300L)
            action()
        }
    }

    CosmicBackground {
        Column(modifier = Modifier.fillMaxSize().testTag("history_screen")) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    haptic.light()
                    onBack()
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = CreamDim,
                    )
                }
                Text(
                    text = stringResource(R.string.history_screen_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CanelaFontFamily,
                    color = Gold,
                    modifier = Modifier.weight(1f),
                )
                // Trailing Done text button — mirrors iOS HistoryView.swift:38-48
                // (.toolbar { ToolbarItem(.topBarTrailing) { Button("done_action") } }).
                TextButton(
                    onClick = {
                        haptic.light()
                        onBack()
                    },
                    modifier = Modifier
                        .testTag("history_done_button")
                        .semantics { contentDescription = "history_done_button" },
                ) {
                    Text(
                        text = stringResource(R.string.done_action),
                        color = Gold,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // Mirrors iOS HistoryView.swift:21-22 — when history saving is off,
            // show a dedicated empty-state with a "Open Settings" CTA instead of
            // the (potentially stale) thread list.
            if (!state.isHistoryEnabled) {
                HistoryDisabledState(
                    onOpenSettings = {
                        haptic.light()
                        if (onOpenProfileSettings != null) onOpenProfileSettings() else onBack()
                    },
                )
                return@Column
            }

            UnifiedHistoryList(
                state = state,
                viewModel = viewModel,
                haptic = haptic,
                onChatSelected = if (onChatSelected != null) {
                    { id ->
                        navigateAfterDismiss { onChatSelected(id) }
                    }
                } else null,
                // Mirrors iOS HistoryView.swift:218-221 — hydrate the full match
                // item via CompatibilityHistoryService before invoking the
                // navigation callback. We re-fetch here even though the caller
                // also rehydrates, to guarantee the lookup happens on the screen
                // boundary (parity with iOS) and so any future caller that
                // forgets to rehydrate still gets a valid sessionId backed by a
                // confirmed entity.
                onMatchSelected = if (onMatchSelected != null) {
                    { sessionId ->
                        coroutineScope.launch {
                            val hydrated = viewModel.getCompatibilityItem(sessionId)
                            if (hydrated != null) {
                                delay(300L)
                                onMatchSelected(hydrated.sessionId)
                            }
                        }
                    }
                } else null,
                // Mirrors iOS HistoryView.swift:222-233 — hydrate the full group
                // (loading every member item) before invoking the navigation
                // callback so downstream screens never see a lite payload.
                onMatchGroupSelected = if (onMatchGroupSelected != null) {
                    { groupId ->
                        coroutineScope.launch {
                            val hydrated = viewModel.getCompatibilityGroup(groupId)
                            if (hydrated != null) {
                                delay(300L)
                                onMatchGroupSelected(hydrated.id)
                            }
                        }
                    }
                } else null,
                onRequestDeleteChat = { id, title ->
                    pendingDelete = DeleteRequest(DeleteKind.CHAT, id, title)
                },
                onRequestDeleteMatch = { id, title ->
                    pendingDelete = DeleteRequest(DeleteKind.COMPATIBILITY, id, title)
                },
                onRequestDeleteGroup = { groupId, title ->
                    pendingDelete = DeleteRequest(DeleteKind.COMPATIBILITY_GROUP, groupId, title)
                },
            )
        }

        // Confirmation dialog — mirrors iOS .alert("Delete", isPresented:) flow
        pendingDelete?.let { req ->
            AlertDialog(
                modifier = Modifier.testTag("history_delete_confirm"),
                onDismissRequest = { pendingDelete = null },
                title = { Text(stringResource(R.string.delete_dialog_title)) },
                text = {
                    Text(stringResource(R.string.delete_item_confirm_format, req.title))
                },
                confirmButton = {
                    TextButton(
                        modifier = Modifier.testTag("history_delete_confirm_button"),
                        onClick = {
                            haptic.light()
                            when (req.kind) {
                                DeleteKind.CHAT -> viewModel.deleteThread(req.id)
                                DeleteKind.COMPATIBILITY -> viewModel.deleteCompatibilityItem(req.id)
                                DeleteKind.COMPATIBILITY_GROUP -> viewModel.deleteCompatibilityGroup(req.id)
                            }
                            pendingDelete = null
                        },
                    ) {
                        Text(stringResource(R.string.delete_action), color = Color(0xFFFF5252))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text(stringResource(R.string.cancel_action))
                    }
                },
            )
        }

        // Error Snackbar host — surfaces load/mutation failures bound to
        // state.error (gap #5). Anchored to the bottom of the screen above
        // system bars so it doesn't collide with gesture insets.
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .testTag("history_error_snackbar")
                    .semantics { contentDescription = "history_error_snackbar" },
            )
        }
    }
}

private enum class DeleteKind { CHAT, COMPATIBILITY, COMPATIBILITY_GROUP }
private data class DeleteRequest(val kind: DeleteKind, val id: String, val title: String)

// Mirrors HistoryView.swift:73-107 (`historyDisabledView`).
@Composable
private fun HistoryDisabledState(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Mirrors iOS HistoryView.swift:75-77 — `clock.badge.xmark` system icon
        // at 48pt sitting above the title (gap #7).
        Icon(
            Icons.Filled.HistoryToggleOff,
            contentDescription = stringResource(R.string.cd_history_disabled_icon),
            tint = CreamDim.copy(alpha = 0.4f),
            modifier = Modifier.size(48.dp).testTag("history_disabled_icon"),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.history_turned_off),
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = CreamText,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.history_not_saved_desc),
            fontSize = 15.sp,
            color = CreamDim,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onOpenSettings,
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.testTag("history_open_settings"),
        ) {
            Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.open_settings), fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Single unified date-bucketed list that interleaves chats, single matches, and
 * match groups — mirrors iOS HistoryView.swift:128-195 / `filteredGroupedItems`.
 */
@Composable
private fun UnifiedHistoryList(
    state: HistoryUiState,
    viewModel: HistoryViewModel,
    haptic: HapticManager,
    onChatSelected: ((String) -> Unit)?,
    onMatchSelected: ((String) -> Unit)?,
    onMatchGroupSelected: ((String) -> Unit)?,
    onRequestDeleteChat: (id: String, title: String) -> Unit,
    onRequestDeleteMatch: (id: String, title: String) -> Unit,
    onRequestDeleteGroup: (groupId: String, title: String) -> Unit,
) {
    // Search bar — single field that filters chats + matches uniformly.
    // Mirrors iOS HistoryView.swift:133-156 including trailing clear (X) button.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = CreamDim, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = state.searchText,
                onValueChange = viewModel::setSearchText,
                textStyle = TextStyle(color = CreamText, fontSize = 15.sp),
                cursorBrush = SolidColor(Gold),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("history_search_field"),
            )
            if (state.searchText.isEmpty()) {
                Text(
                    text = stringResource(R.string.search_history_placeholder),
                    color = CreamDim.copy(alpha = 0.6f),
                    fontSize = 15.sp,
                )
            }
        }
        if (state.searchText.isNotEmpty()) {
            IconButton(
                onClick = {
                    haptic.light()
                    viewModel.setSearchText("")
                },
                modifier = Modifier.size(24.dp).testTag("history_search_clear"),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cd_clear_search),
                    tint = CreamDim,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }

    Spacer(Modifier.height(4.dp))

    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Gold, modifier = Modifier.size(28.dp))
        }
        return
    }

    val sections = state.unifiedSections
    if (sections.isEmpty()) {
        // Mirrors iOS HistoryView.swift:110-125 — gold-tinted clock icon at 48pt
        // above title + body text (gap #6 — was a bare Text before).
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Filled.History,
                contentDescription = stringResource(R.string.cd_history_empty_icon),
                tint = Gold.copy(alpha = 0.3f),
                modifier = Modifier.size(48.dp).testTag("history_empty_icon"),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (state.searchText.isEmpty())
                    stringResource(R.string.no_history_yet)
                else
                    stringResource(R.string.history_no_results),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = CreamText,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.chats_matches_appear_here),
                color = CreamDim,
                fontSize = 16.sp,
            )
        }
        return
    }

    val flatThreads: List<ChatThread> = state.filteredThreads
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.testTag("history_unified_list"),
    ) {
        sections.forEach { (key, items) ->
            item(key = "header_${sectionId(key)}") { UnifiedSectionHeader(key) }
            items.forEach { item ->
                item(key = item.id) {
                    // Trigger pagination spinner when reaching the bottom of the
                    // chat sub-list (mirrors iOS HistoryView.swift:171-175).
                    if (item is UnifiedHistoryItem.Chat) {
                        val flatIndex = flatThreads.indexOf(item.thread)
                        LaunchedEffect(flatIndex) {
                            if (flatIndex >= 0) viewModel.loadMoreIfNeeded(flatIndex)
                        }
                    }
                    UnifiedRow(
                        item = item,
                        haptic = haptic,
                        onTap = {
                            haptic.light()
                            when (item) {
                                is UnifiedHistoryItem.Chat ->
                                    onChatSelected?.invoke(item.thread.id)
                                is UnifiedHistoryItem.Match ->
                                    onMatchSelected?.invoke(item.item.sessionId)
                                is UnifiedHistoryItem.MatchGroup ->
                                    onMatchGroupSelected?.invoke(item.group.id)
                            }
                        },
                        onPin = {
                            haptic.light()
                            when (item) {
                                is UnifiedHistoryItem.Chat ->
                                    viewModel.pinThread(item.thread.id)
                                is UnifiedHistoryItem.Match ->
                                    viewModel.pinCompatibilityItem(item.item.sessionId)
                                is UnifiedHistoryItem.MatchGroup ->
                                    viewModel.pinCompatibilityGroup(item.group.id)
                            }
                        },
                        onDelete = {
                            haptic.light()
                            when (item) {
                                is UnifiedHistoryItem.Chat -> {
                                    val title = item.thread.title.ifEmpty { "Conversation" }
                                    onRequestDeleteChat(item.thread.id, title)
                                }
                                is UnifiedHistoryItem.Match -> {
                                    val title = "${item.item.boyName} ♥ ${item.item.girlName}"
                                    onRequestDeleteMatch(item.item.sessionId, title)
                                }
                                is UnifiedHistoryItem.MatchGroup -> {
                                    val partners = item.group.items.joinToString(", ") { it.girlName }
                                    val title = "${item.group.userName} + $partners"
                                    onRequestDeleteGroup(item.group.id, title)
                                }
                            }
                        },
                    )
                }
            }
        }
        // Mirrors iOS HistoryView.swift:180-190 — pagination spinner footer.
        if (state.isLoadingMore) {
            item(key = "loading_more") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Gold, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

private fun sectionId(key: HistorySectionKey): String = when (key) {
    HistorySectionKey.Today -> "today"
    HistorySectionKey.Yesterday -> "yesterday"
    HistorySectionKey.ThisWeek -> "this_week"
    is HistorySectionKey.Day -> "day_${key.startOfDayMs}"
}

@Composable
private fun UnifiedSectionHeader(key: HistorySectionKey) {
    val label = when (key) {
        HistorySectionKey.Today -> stringResource(R.string.history_section_today)
        HistorySectionKey.Yesterday -> stringResource(R.string.history_section_yesterday)
        HistorySectionKey.ThisWeek -> stringResource(R.string.history_section_this_week)
        is HistorySectionKey.Day -> remember(key.startOfDayMs) {
            SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(key.startOfDayMs))
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = CreamDim,
        )
    }
}

@Composable
private fun UnifiedRow(
    item: UnifiedHistoryItem,
    haptic: HapticManager,
    onTap: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
) {
    when (item) {
        is UnifiedHistoryItem.Chat -> ChatThreadRow(
            thread = item.thread,
            haptic = haptic,
            onClick = onTap,
            onPin = onPin,
            onDelete = onDelete,
        )
        is UnifiedHistoryItem.Match -> CompatibilityHistoryItemRow(
            item = item.item,
            haptic = haptic,
            onClick = onTap,
            onPin = onPin,
            onDelete = onDelete,
        )
        is UnifiedHistoryItem.MatchGroup -> CompatibilityGroupRow(
            group = item.group,
            haptic = haptic,
            onClick = onTap,
            onPin = onPin,
            onDelete = onDelete,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ChatThreadRow(
    thread: ChatThread,
    haptic: HapticManager,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
) {
    val defaultTitle = stringResource(R.string.history_conversation_default)
    val resolvedTitle = thread.title.ifEmpty { defaultTitle }
    val relativeTime = remember(thread.updatedAtMs) {
        if (thread.updatedAtMs <= 0L) "" else DateUtils.getRelativeTimeSpanString(
            thread.updatedAtMs,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE,
        ).toString()
    }
    var showContextMenu by remember { mutableStateOf(false) }

    // Swipe gestures — mirrors iOS HistoryView.swift:307-320 trailing
    // .swipeActions which expose BOTH a destructive Delete (trailing) AND a
    // Pin/Unpin toggle. On Compose we map:
    //   EndToStart (trailing swipe) → Delete (red bg + trash icon)
    //   StartToEnd (leading swipe)  → Pin/Unpin toggle (gold bg + pin icon)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> onDelete()
                SwipeToDismissBoxValue.StartToEnd -> onPin()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            // Keep the row visible — pin toggles state-only and delete defers
            // to the AlertDialog (matches iOS allowsFullSwipe = false).
            false
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier.testTag("history_chat_row"),
        backgroundContent = {
            val target = dismissState.targetValue
            val isPinSwipe = target == SwipeToDismissBoxValue.StartToEnd
            val bgColor = if (isPinSwipe)
                Gold.copy(alpha = 0.85f)
            else
                Color(0xFFFC8181).copy(alpha = 0.85f)
            val alignment = if (isPinSwipe) Alignment.CenterStart else Alignment.CenterEnd
            val padStart = if (isPinSwipe) 20.dp else 0.dp
            val padEnd = if (isPinSwipe) 0.dp else 20.dp
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(bgColor)
                    .padding(start = padStart, end = padEnd),
                contentAlignment = alignment,
            ) {
                Icon(
                    if (isPinSwipe) {
                        if (thread.isPinned) Icons.Outlined.PushPin else Icons.Filled.PushPin
                    } else {
                        Icons.Filled.Delete
                    },
                    contentDescription = if (isPinSwipe) {
                        if (thread.isPinned)
                            stringResource(R.string.cd_unpin)
                        else
                            stringResource(R.string.cd_pin)
                    } else {
                        stringResource(R.string.cd_delete)
                    },
                    tint = Color.White,
                )
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(NavySurface)
                // Mirrors iOS HistoryView.swift:297-300 — gold-border for pinned rows.
                .border(
                    0.5.dp,
                    if (thread.isPinned) Gold.copy(alpha = 0.3f) else Gold.copy(alpha = 0.15f),
                    RoundedCornerShape(16.dp),
                )
                // Mirrors iOS HistoryView.swift:303-306 + .contextMenu (321-332):
                // tap fires haptic+onTap, long-press opens pin/delete menu.
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptic.medium()
                        showContextMenu = true
                    },
                )
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Leading pin slot (fixed width whether pinned or not).
            Box(modifier = Modifier.width(12.dp)) {
                if (thread.isPinned) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = stringResource(R.string.cd_pin),
                        tint = Gold,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            AreaIconCircle(area = thread.primaryArea)

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = resolvedTitle,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CreamText,
                    maxLines = 1,
                )
                if (thread.preview.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = thread.preview,
                        fontSize = 13.sp,
                        color = CreamDim,
                        maxLines = 1,
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(end = 4.dp),
            ) {
                if (relativeTime.isNotBlank()) {
                    Text(
                        text = relativeTime,
                        fontSize = 12.sp,
                        color = CreamDim.copy(alpha = 0.7f),
                    )
                }
                if (thread.messageCount > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${thread.messageCount}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Gold,
                    )
                    Text(
                        text = stringResource(R.string.messages_label),
                        fontSize = 11.sp,
                        color = CreamDim,
                    )
                }
            }
            // Inline pin/delete IconButtons removed for parity with iOS
            // HistoryView.swift:307-320 — pin/delete are reachable only via the
            // trailing/leading swipe actions (and the long-press context menu).

            // Long-press context menu — mirrors iOS .contextMenu (321-332).
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false },
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            if (thread.isPinned)
                                stringResource(R.string.cd_unpin)
                            else
                                stringResource(R.string.cd_pin),
                        )
                    },
                    onClick = {
                        showContextMenu = false
                        onPin()
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.delete_action),
                            color = Color(0xFFFC8181),
                        )
                    },
                    onClick = {
                        showContextMenu = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun AreaIconCircle(area: String?) {
    val (icon, tint) = areaIconAndTint(area)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

private fun areaIconAndTint(area: String?): Pair<ImageVector, Color> {
    return when (area?.lowercase()) {
        "career" -> Icons.Filled.Work to Gold
        "marriage" -> Icons.Filled.Favorite to Gold
        "health" -> Icons.Filled.MonitorHeart to Gold
        "finance" -> Icons.Filled.AttachMoney to Gold
        "compatibility" -> Icons.Filled.Group to Gold
        else -> Icons.Filled.AutoAwesome to Gold
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun CompatibilityHistoryItemRow(
    item: CompatibilityHistoryDisplayItem,
    haptic: HapticManager,
    onClick: () -> Unit = {},
    onPin: () -> Unit = {},
    onDelete: () -> Unit,
) {
    val scorePercent = if (item.maxScore > 0) (item.totalScore * 100 / item.maxScore) else 0
    // Mirrors iOS HistoryView.swift:462-466 `matchScoreColor` — green ≥70,
    // yellow ≥50, orange otherwise. Earlier Android revision used red (FC8181)
    // for the lowest bucket which made low-but-recoverable scores look fatal.
    val scoreColor = when {
        scorePercent >= 70 -> Color(0xFF48BB78)
        scorePercent >= 50 -> Color(0xFFECC94B)
        else -> WarningOrange
    }
    var showContextMenu by remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> onDelete()
                SwipeToDismissBoxValue.StartToEnd -> onPin()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier.testTag("history_compat_row"),
        backgroundContent = {
            val target = dismissState.targetValue
            val isPinSwipe = target == SwipeToDismissBoxValue.StartToEnd
            val bgColor = if (isPinSwipe)
                Gold.copy(alpha = 0.85f)
            else
                Color(0xFFFC8181).copy(alpha = 0.85f)
            val alignment = if (isPinSwipe) Alignment.CenterStart else Alignment.CenterEnd
            val padStart = if (isPinSwipe) 20.dp else 0.dp
            val padEnd = if (isPinSwipe) 0.dp else 20.dp
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(bgColor)
                    .padding(start = padStart, end = padEnd),
                contentAlignment = alignment,
            ) {
                Icon(
                    if (isPinSwipe) {
                        if (item.isPinned) Icons.Outlined.PushPin else Icons.Filled.PushPin
                    } else {
                        Icons.Filled.Delete
                    },
                    contentDescription = if (isPinSwipe) {
                        if (item.isPinned)
                            stringResource(R.string.cd_unpin)
                        else
                            stringResource(R.string.cd_pin)
                    } else {
                        stringResource(R.string.cd_delete)
                    },
                    tint = Color.White,
                )
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(NavySurface)
                .border(
                    0.5.dp,
                    if (item.isPinned) Gold.copy(alpha = 0.3f) else Gold.copy(alpha = 0.15f),
                    RoundedCornerShape(16.dp),
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptic.medium()
                        showContextMenu = true
                    },
                )
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(12.dp)) {
                if (item.isPinned) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = stringResource(R.string.cd_pin),
                        tint = Gold,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

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
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = scoreColor,
                    )
                    Text(
                        text = "/${item.maxScore}",
                        fontSize = 10.sp,
                        color = CreamDim,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${item.boyName} ♥ ${item.girlName}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CreamText,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.displayDate,
                    fontSize = 12.sp,
                    color = CreamDim,
                )
            }

            // Mirrors iOS HistoryView.swift:413-426 (`extraInfoView` for .match) —
            // chat-bubble icon + count of follow-up user questions on this match.
            // Badge stays hidden when count is 0 (gap #8).
            if (item.userQuestionCount > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .testTag("history_match_user_question_count"),
                ) {
                    Icon(
                        Icons.Filled.ChatBubble,
                        contentDescription = stringResource(R.string.cd_user_questions),
                        tint = Gold,
                        modifier = Modifier.size(10.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "${item.userQuestionCount}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Gold,
                    )
                }
            }

            // Inline pin/delete IconButtons removed for parity with iOS
            // HistoryView.swift:307-320 — pin/delete are reachable only via the
            // trailing/leading swipe actions (and the long-press context menu).

            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false },
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            if (item.isPinned)
                                stringResource(R.string.cd_unpin)
                            else
                                stringResource(R.string.cd_pin),
                        )
                    },
                    onClick = {
                        showContextMenu = false
                        onPin()
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.delete_action),
                            color = Color(0xFFFC8181),
                        )
                    },
                    onClick = {
                        showContextMenu = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun CompatibilityGroupRow(
    group: CompatibilityGroup,
    haptic: HapticManager,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
) {
    val partnerNames = group.items.joinToString(", ") { it.girlName }
    val title = stringResource(R.string.history_group_title_format, group.userName, partnerNames)
    val best = group.bestItem
    var showContextMenu by remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> onDelete()
                SwipeToDismissBoxValue.StartToEnd -> onPin()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier.testTag("history_group_row"),
        backgroundContent = {
            val target = dismissState.targetValue
            val isPinSwipe = target == SwipeToDismissBoxValue.StartToEnd
            val bgColor = if (isPinSwipe)
                Gold.copy(alpha = 0.85f)
            else
                Color(0xFFFC8181).copy(alpha = 0.85f)
            val alignment = if (isPinSwipe) Alignment.CenterStart else Alignment.CenterEnd
            val padStart = if (isPinSwipe) 20.dp else 0.dp
            val padEnd = if (isPinSwipe) 0.dp else 20.dp
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(bgColor)
                    .padding(start = padStart, end = padEnd),
                contentAlignment = alignment,
            ) {
                Icon(
                    if (isPinSwipe) {
                        if (group.isPinned) Icons.Outlined.PushPin else Icons.Filled.PushPin
                    } else {
                        Icons.Filled.Delete
                    },
                    contentDescription = if (isPinSwipe) {
                        if (group.isPinned)
                            stringResource(R.string.cd_unpin)
                        else
                            stringResource(R.string.cd_pin)
                    } else {
                        stringResource(R.string.cd_delete)
                    },
                    tint = Color.White,
                )
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(NavySurface)
                .border(
                    0.5.dp,
                    if (group.isPinned) Gold.copy(alpha = 0.3f) else Gold.copy(alpha = 0.15f),
                    RoundedCornerShape(16.dp),
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptic.medium()
                        showContextMenu = true
                    },
                )
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(12.dp)) {
                if (group.isPinned) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = stringResource(R.string.cd_pin),
                        tint = Gold,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(GroupPurple.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Group,
                    contentDescription = stringResource(R.string.cd_compatibility_group),
                    tint = GroupPurple,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CreamText,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.history_group_partners_format, group.partnerCount),
                    fontSize = 12.sp,
                    color = CreamDim,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                if (best != null) {
                    Text(
                        text = stringResource(R.string.history_best_score_format, best.totalScore, best.maxScore),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GroupPurple,
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    text = "${group.partnerCount}",
                    fontSize = 12.sp,
                    color = GroupPurple.copy(alpha = 0.85f),
                )
            }

            // Pin button + delete button removed — pin/delete are reachable
            // only via the trailing/leading swipe actions and the long-press
            // context menu, mirroring iOS .swipeActions on .matchGroup rows
            // (HistoryView.swift:307-320).

            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false },
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            if (group.isPinned)
                                stringResource(R.string.cd_unpin)
                            else
                                stringResource(R.string.cd_pin),
                        )
                    },
                    onClick = {
                        showContextMenu = false
                        onPin()
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.delete_action),
                            color = Color(0xFFFC8181),
                        )
                    },
                    onClick = {
                        showContextMenu = false
                        onDelete()
                    },
                )
            }
        }
    }
}
