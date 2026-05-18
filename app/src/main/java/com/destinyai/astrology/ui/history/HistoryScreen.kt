package com.destinyai.astrology.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
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
                    text = "Chat History",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CanelaFontFamily,
                    color = Gold,
                    modifier = Modifier.weight(1f),
                )
            }

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
                                Text(
                                    text = thread.title.ifEmpty { "Conversation" },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CreamText,
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
    }
}
