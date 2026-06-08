package com.destinyai.astrology.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.R
import com.destinyai.astrology.domain.model.ChatThread
import com.destinyai.astrology.services.HapticManager
import com.destinyai.astrology.ui.theme.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

@Composable
fun ChatHistorySheet(
    viewModel: ChatViewModel,
    onDismiss: () -> Unit,
    onNavigateToSettings: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var searchText by remember { mutableStateOf("") }
    var threadToDelete by remember { mutableStateOf<ChatThread?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val listStateForHistory = rememberLazyListState()

    LaunchedEffect(Unit) { viewModel.loadHistory() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        CosmicBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = "history_screen" },
            ) {
                // Toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { viewModel.startNewChat(); onDismiss() },
                        modifier = Modifier.semantics { contentDescription = "new_chat_button" },
                    ) {
                        Icon(Icons.Default.EditNote, contentDescription = null, tint = Gold)
                    }
                    Text(
                        stringResource(R.string.chat_history_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = CanelaFontFamily,
                        color = Gold,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.semantics { contentDescription = "sheet_close_button" },
                    ) {
                        Text(stringResource(R.string.done), color = Gold)
                    }
                }

                // Search bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = CreamDim, modifier = Modifier.size(16.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        BasicTextField(
                            value = searchText,
                            onValueChange = { searchText = it },
                            textStyle = TextStyle(color = CreamText, fontSize = 15.sp),
                            cursorBrush = SolidColor(Gold),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (searchText.isEmpty()) Text(stringResource(R.string.chat_search_chats), color = CreamDim, fontSize = 15.sp)
                                inner()
                            },
                        )
                    }
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { searchText = "" }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Cancel, contentDescription = null, tint = CreamDim, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                val filtered = if (searchText.isBlank()) {
                    state.threads
                } else {
                    state.threads.filter {
                        it.title.contains(searchText, ignoreCase = true) ||
                            it.preview.contains(searchText, ignoreCase = true)
                    }
                }

                val grouped = groupThreadsByDate(filtered)

                if (!state.isHistoryEnabled) {
                    // Mirrors iOS ChatView.swift:531-622 'history_turned_off' empty state.
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .padding(horizontal = 32.dp)
                                .semantics { contentDescription = "history_disabled_empty_state" },
                        ) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                tint = CreamDim.copy(alpha = 0.3f),
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.history_turned_off),
                                fontSize = 16.sp,
                                color = CreamDim,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.history_off_warning),
                                fontSize = 13.sp,
                                color = CreamDim.copy(alpha = 0.7f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            // Mirrors iOS ChatView.swift:604-620 — gear button that closes the
                            // sheet and posts .openProfileSettings.  Android uses an explicit
                            // navigation lambda threaded from MainScreen.
                            if (onNavigateToSettings != null) {
                                Spacer(Modifier.height(20.dp))
                                val context = LocalContext.current
                                val haptic = remember { HapticManager(context) }
                                Button(
                                    onClick = {
                                        haptic.light()
                                        onDismiss()
                                        onNavigateToSettings.invoke()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Gold,
                                        contentColor = Color(0xFF0D0D1A),
                                    ),
                                    modifier = Modifier
                                        .testTag("history_open_settings_button")
                                        .semantics { contentDescription = "history_open_settings_button" },
                                ) {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        stringResource(R.string.open_settings),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                } else if (grouped.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Forum,
                                contentDescription = null,
                                tint = CreamDim.copy(alpha = 0.2f),
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                if (searchText.isEmpty()) stringResource(R.string.no_chat_history) else stringResource(R.string.chat_history_no_results),
                                fontSize = 16.sp,
                                color = CreamDim,
                            )
                        }
                    }
                } else {
                    // Mirrors iOS pageSize=20 trigger (ChatView.swift:512-644) — when the user nears
                    // the bottom of the list, request the next page from the ViewModel.
                    val totalItems = grouped.sumOf { it.second.size }
                    LaunchedEffect(grouped.size) {
                        snapshotFlow { listStateForHistory.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                            .collect { lastIdx ->
                                if (lastIdx != null && lastIdx >= totalItems - 4) {
                                    viewModel.loadMoreHistory()
                                }
                            }
                    }
                    LazyColumn(
                        state = listStateForHistory,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        grouped.forEach { (label, threads) ->
                            item(key = "header_$label") {
                                Text(
                                    label.uppercase(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CreamDim.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                            items(threads, key = { it.id }) { thread ->
                                HistoryThreadRow(
                                    thread = thread,
                                    isSelected = thread.id == state.activeThreadId,
                                    onTap = {
                                        viewModel.openThread(thread.id)
                                        onDismiss()
                                    },
                                    onDelete = {
                                        threadToDelete = thread
                                        showDeleteDialog = true
                                    },
                                    onPin = { viewModel.pinThread(thread.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog && threadToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; threadToDelete = null },
            title = { Text(stringResource(R.string.delete), color = CreamText) },
            text = { Text("${stringResource(R.string.delete)} \"${threadToDelete?.title}\"?", color = CreamDim) },
            confirmButton = {
                TextButton(onClick = {
                    threadToDelete?.let {
                        // Wired to repository (DAO + best-effort API) via VM.deleteThread.
                        viewModel.deleteThread(it.id)
                    }
                    showDeleteDialog = false
                    threadToDelete = null
                }) { Text(stringResource(R.string.delete), color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; threadToDelete = null }) {
                    Text(stringResource(R.string.cancel), color = CreamDim)
                }
            },
            containerColor = NavySurface,
        )
    }
}

// ── History thread row ────────────────────────────────────────────────────────

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HistoryThreadRow(
    thread: ChatThread,
    isSelected: Boolean,
    onTap: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val haptic = remember { HapticManager(context) }
    // Mirrors iOS ChatView.swift:825-837 — trailing swipe = destructive Delete,
    // leading swipe = Pin/Unpin toggle.  Material3 SwipeToDismissBox surfaces both
    // directions; the dismiss callback fires once per gesture, then the row resets
    // (we don't physically remove the row here — VM.deleteThread / pinThread drives
    // state.threads, exactly like iOS where the swipe action callbacks delegate to
    // the view model).
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    haptic.medium()
                    onDelete()
                    false // keep row in place; deletion is driven by VM state update
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    haptic.light()
                    onPin()
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier
            .testTag("history_thread_row_swipe")
            .semantics { contentDescription = "history_thread_row_swipe" },
        backgroundContent = {
            // iOS parity: only paint the swipe background WHILE the row is being swiped.
            // SwiftUI's `.swipeActions` shows actions only during the gesture; M3
            // SwipeToDismissBox would otherwise paint `backgroundContent` at rest, which
            // bleeds the red destructive band through any margin around the foreground
            // and makes every row look red-bordered.
            val isSettled = dismissState.dismissDirection == SwipeToDismissBoxValue.Settled
            if (!isSettled) {
                val isPin = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
                val bg = if (isPin) Gold.copy(alpha = 0.18f) else Color(0xFFB71C1C).copy(alpha = 0.85f)
                val align = if (isPin) Alignment.CenterStart else Alignment.CenterEnd
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(bg)
                        .padding(horizontal = 24.dp),
                    contentAlignment = align,
                ) {
                    if (isPin) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PushPin, contentDescription = null, tint = Gold, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (thread.isPinned) stringResource(R.string.unpin) else stringResource(R.string.pin),
                                color = Gold,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.delete),
                                color = CreamText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.Delete, contentDescription = null, tint = CreamText, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
    ) {
        // iOS parity (HistoryRow in ChatView.swift:781-823): non-selected rows are
        // transparent, only the selected row paints `cardBackground`. The List view
        // takes care of subtle separator lines; we add a thin manual divider below.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isSelected) NavySurface else Color.Transparent)
                // iOS parity (ChatView.swift:825-850): both swipe-actions AND a context menu surface
                // pin/delete.  SwipeToDismissBox above provides the swipe path; long-press still
                // exposes the dropdown for users who prefer tap-and-hold.
                .combinedClickable(
                    onClick = {
                        haptic.light()
                        onTap()
                    },
                    onLongClick = {
                        haptic.medium()
                        showMenu = true
                    },
                )
                .semantics { contentDescription = "history_thread_row" }
                .testTag("history_thread_row")
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (thread.isPinned) {
                Icon(Icons.Default.PushPin, contentDescription = null, tint = Gold, modifier = Modifier.size(14.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    thread.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = CreamText,
                    maxLines = 1,
                )
                if (thread.preview.isNotEmpty()) {
                    Text(
                        thread.preview,
                        fontSize = 13.sp,
                        color = CreamDim,
                        maxLines = 1,
                    )
                }
            }
            if (thread.messageCount > 0) {
                Text(
                    "${thread.messageCount}",
                    fontSize = 11.sp,
                    color = CreamDim.copy(alpha = 0.6f),
                )
            }
            // Long-press dropdown — mirrors iOS .contextMenu.  Always-visible action icons
            // were removed to match iOS visual quietness; users now reach pin/delete via
            // a long-press, which surfaces the menu anchored to the row.
            Box {
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (thread.isPinned) stringResource(R.string.unpin) else stringResource(R.string.pin),
                                color = CreamText,
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.PushPin, contentDescription = null, tint = Gold)
                        },
                        onClick = {
                            haptic.light()
                            showMenu = false
                            onPin()
                        },
                        modifier = Modifier
                            .testTag("history_pin_action")
                            .semantics { contentDescription = "history_pin_action" },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.delete),
                                color = Color.Red,
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red)
                        },
                        onClick = {
                            haptic.medium()
                            showMenu = false
                            onDelete()
                        },
                        modifier = Modifier
                            .testTag("history_delete_action")
                            .semantics { contentDescription = "history_delete_action" },
                    )
                }
            }
        }
    }
}

// ── Date grouping ─────────────────────────────────────────────────────────────

private fun groupThreadsByDate(threads: List<ChatThread>): List<Pair<String, List<ChatThread>>> {
    if (threads.isEmpty()) return emptyList()
    val now = System.currentTimeMillis()
    val dayMs = TimeUnit.DAYS.toMillis(1)
    val cal = Calendar.getInstance()

    fun isToday(ms: Long): Boolean {
        val a = Calendar.getInstance().apply { timeInMillis = now }
        val b = Calendar.getInstance().apply { timeInMillis = ms }
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    fun isYesterday(ms: Long): Boolean {
        val a = Calendar.getInstance().apply { timeInMillis = now - dayMs }
        val b = Calendar.getInstance().apply { timeInMillis = ms }
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    val grouped = mutableMapOf<String, MutableList<ChatThread>>()
    threads.forEach { t ->
        val ms = t.updatedAtMs
        val key = when {
            ms == 0L -> "Older"
            isToday(ms) -> "Today"
            isYesterday(ms) -> "Yesterday"
            now - ms < TimeUnit.DAYS.toMillis(7) -> "Last 7 Days"
            now - ms < TimeUnit.DAYS.toMillis(30) -> "Last 30 Days"
            else -> "Older"
        }
        grouped.getOrPut(key) { mutableListOf() }.add(t)
    }
    val order = listOf("Today", "Yesterday", "Last 7 Days", "Last 30 Days", "Older")
    return order.mapNotNull { key -> grouped[key]?.let { key to it } }
}
