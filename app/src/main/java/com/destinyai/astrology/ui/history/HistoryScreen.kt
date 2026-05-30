package com.destinyai.astrology.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadHistory() }

    CosmicBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = CreamDim,
                    )
                }
                Text(
                    text = "History",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CanelaFontFamily,
                    color = Gold,
                    modifier = Modifier.weight(1f),
                )
            }

            // Tabs
            TabRow(
                selectedTabIndex = state.selectedTab,
                containerColor = Color.Transparent,
                contentColor = Gold,
                divider = { HorizontalDivider(color = Gold.copy(alpha = 0.15f)) },
            ) {
                Tab(
                    selected = state.selectedTab == 0,
                    onClick = { viewModel.setTab(0) },
                    text = {
                        Text(
                            text = "Chat",
                            color = if (state.selectedTab == 0) Gold else CreamDim,
                            fontWeight = if (state.selectedTab == 0) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                )
                Tab(
                    selected = state.selectedTab == 1,
                    onClick = { viewModel.setTab(1) },
                    text = {
                        Text(
                            text = "Compatibility",
                            color = if (state.selectedTab == 1) Gold else CreamDim,
                            fontWeight = if (state.selectedTab == 1) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                )
            }

            when (state.selectedTab) {
                0 -> ChatHistoryTab(state = state, viewModel = viewModel)
                1 -> CompatibilityHistoryTab(state = state, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun ChatHistoryTab(
    state: HistoryUiState,
    viewModel: HistoryViewModel,
) {
    // Search bar — glass style
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
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.searchText.isEmpty()) {
                Text("Search conversations…", color = CreamDim.copy(alpha = 0.6f), fontSize = 15.sp)
            }
        }
    }

    Spacer(Modifier.height(4.dp))

    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Gold, modifier = Modifier.size(28.dp))
        }
    } else if (state.filteredThreads.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = if (state.searchText.isEmpty()) "No conversations yet" else "No results",
                color = CreamDim,
                fontSize = 16.sp,
            )
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.filteredThreads, key = { it.id }) { thread ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(NavySurface)
                        .border(0.5.dp, Gold.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (thread.isPinned) {
                                Icon(
                                    Icons.Filled.PushPin,
                                    contentDescription = null,
                                    tint = Gold,
                                    modifier = Modifier.size(12.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                text = thread.title.ifEmpty { "Conversation" },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = CreamText,
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.pinThread(thread.id) }) {
                        Icon(
                            if (thread.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (thread.isPinned) "Unpin" else "Pin",
                            tint = if (thread.isPinned) Gold else CreamDim.copy(alpha = 0.5f),
                        )
                    }
                    IconButton(onClick = { viewModel.deleteThread(thread.id) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFFF5252),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompatibilityHistoryTab(
    state: HistoryUiState,
    viewModel: HistoryViewModel,
) {
    if (state.isCompatibilityLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Gold, modifier = Modifier.size(28.dp))
        }
        return
    }

    if (state.compatibilityItems.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No compatibility history yet",
                color = CreamDim,
                fontSize = 16.sp,
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.compatibilityItems, key = { it.sessionId }) { item ->
            CompatibilityHistoryItemRow(
                item = item,
                onDelete = { viewModel.deleteCompatibilityItem(item.sessionId) },
            )
        }
    }
}

@Composable
private fun CompatibilityHistoryItemRow(
    item: CompatibilityHistoryDisplayItem,
    onDelete: () -> Unit,
) {
    val scorePercent = if (item.maxScore > 0) (item.totalScore * 100 / item.maxScore) else 0
    val scoreColor = when {
        scorePercent >= 70 -> Color(0xFF48BB78)
        scorePercent >= 50 -> Color(0xFFED8936)
        else -> Color(0xFFFC8181)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NavySurface)
            .border(0.5.dp, Gold.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
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

        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Delete",
                tint = Color(0xFFFF5252),
            )
        }
    }
}
