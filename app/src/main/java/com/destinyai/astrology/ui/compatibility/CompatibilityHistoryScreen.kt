package com.destinyai.astrology.ui.compatibility

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.domain.model.CompatibilityHistoryItem
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
) {
    val items by viewModel.historyItems.collectAsStateWithLifecycle()
    var searchText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.loadHistory() }

    val filtered = remember(items, searchText) {
        if (searchText.isBlank()) items
        else items.filter {
            it.boyName.contains(searchText, ignoreCase = true) ||
                it.girlName.contains(searchText, ignoreCase = true)
        }
    }

    CosmicBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CreamText)
                }
                Text(
                    text = "Match History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CreamText,
                    modifier = Modifier.weight(1f),
                )
            }

            if (items.isEmpty()) {
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
                                Text("Search matches...", style = MaterialTheme.typography.bodyMedium, color = CreamDim.copy(alpha = 0.6f))
                            }
                            inner()
                        },
                    )
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { searchText = "" }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear", tint = CreamDim, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No results found", style = MaterialTheme.typography.bodyMedium, color = CreamDim)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filtered, key = { it.sessionId }) { item ->
                            SwipeToDeleteHistoryItem(
                                item = item,
                                onPin = { viewModel.toggleHistoryPin(item.sessionId) },
                                onDelete = { viewModel.deleteHistoryItem(item.sessionId) },
                            )
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
    onPin: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFC8181).copy(alpha = 0.85f))
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.White)
            }
        },
    ) {
        HistoryItemRow(item = item, onPin = onPin)
    }
}

@Composable
private fun HistoryItemRow(
    item: CompatibilityHistoryItem,
    onPin: () -> Unit,
) {
    val scoreColor = when {
        item.scorePercentage >= 70 -> Color(0xFF48BB78)
        item.scorePercentage >= 50 -> Color(0xFFED8936)
        else -> Color(0xFFFC8181)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NavySurface)
            .border(1.dp, Gold.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
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
                contentDescription = if (item.isPinned) "Unpin" else "Pin",
                tint = if (item.isPinned) Gold else CreamDim.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp),
            )
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
                text = "No Match History",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = CreamText,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Your compatibility analyses will\nappear here after running them.",
                style = MaterialTheme.typography.bodyMedium,
                color = CreamDim,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )
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
