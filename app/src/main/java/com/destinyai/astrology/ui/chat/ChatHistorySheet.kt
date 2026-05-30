package com.destinyai.astrology.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.domain.model.ChatThread
import com.destinyai.astrology.ui.theme.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

@Composable
fun ChatHistorySheet(
    viewModel: ChatViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var searchText by remember { mutableStateOf("") }
    var threadToDelete by remember { mutableStateOf<ChatThread?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

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
                        "Chat History",
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
                        Text("Done", color = Gold)
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
                                if (searchText.isEmpty()) Text("Search chats", color = CreamDim, fontSize = 15.sp)
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

                if (grouped.isEmpty()) {
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
                                if (searchText.isEmpty()) "No chat history" else "No results found",
                                fontSize = 16.sp,
                                color = CreamDim,
                            )
                        }
                    }
                } else {
                    LazyColumn(
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
            title = { Text("Delete", color = CreamText) },
            text = { Text("Delete \"${threadToDelete?.title}\"?", color = CreamDim) },
            confirmButton = {
                TextButton(onClick = {
                    threadToDelete?.let {
                        // Remove from list locally (repository deletion handled separately)
                        viewModel.deleteThread(it.id)
                    }
                    showDeleteDialog = false
                    threadToDelete = null
                }) { Text("Delete", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; threadToDelete = null }) {
                    Text("Cancel", color = CreamDim)
                }
            },
            containerColor = NavySurface,
        )
    }
}

// ── History thread row ────────────────────────────────────────────────────────

@Composable
fun HistoryThreadRow(
    thread: ChatThread,
    isSelected: Boolean,
    onTap: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) NavySurface else Color.Transparent)
            .clickable { onTap() }
            .semantics { contentDescription = "history_thread_row" }
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
        // Swipe-style trailing actions as icon buttons
        Row {
            IconButton(onClick = onPin, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (thread.isPinned) Icons.Default.PushPin else Icons.Default.PushPin,
                    contentDescription = if (thread.isPinned) "Unpin" else "Pin",
                    tint = Gold.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
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
