package com.destinyai.astrology.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.R
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.NavyVariant
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import java.util.Calendar

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onNavigateToCharts: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToProfile: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadHomeData() }

    val greeting = timeBasedGreeting()

    CosmicBackground(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            HomeHeader(
                displayName = state.displayName,
                greeting = greeting,
                onHistoryTap = onNavigateToHistory,
                onNotificationsTap = onNavigateToNotifications,
                onProfileTap = onNavigateToProfile,
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Life area orbs
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Your life areas",
                        style = MaterialTheme.typography.labelLarge,
                        color = Gold.copy(alpha = 0.7f),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    LifeAreaOrbs(onNavigateToCharts = onNavigateToCharts)
                }

                // Daily Insight card
                if (state.dailyInsight != null) {
                    item {
                        InsightCard(insight = state.dailyInsight!!)
                    }
                }

                // Charts shortcut
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        QuickActionCard(
                            emoji = "🔮",
                            label = "Birth Chart",
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToCharts,
                        )
                        QuickActionCard(
                            emoji = "📜",
                            label = "History",
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToHistory,
                        )
                    }
                }

                // Suggested questions
                if (state.suggestedQuestions.isNotEmpty()) {
                    item {
                        Text(
                            text = "Ask the stars",
                            style = MaterialTheme.typography.labelLarge,
                            color = Gold.copy(alpha = 0.7f),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    items(state.suggestedQuestions) { question ->
                        SuggestedQuestion(
                            text = question,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }

                if (state.isLoading) {
                    item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Gold, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    displayName: String,
    greeting: String,
    onHistoryTap: () -> Unit,
    onNotificationsTap: () -> Unit,
    onProfileTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$greeting,",
                fontSize = 13.sp,
                color = CreamDim,
            )
            Text(
                text = if (displayName.isNotBlank()) displayName else "Welcome",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CanelaFontFamily,
                color = CreamText,
            )
        }
        IconButton(onClick = onHistoryTap) {
            Icon(Icons.Filled.History, contentDescription = "History", tint = CreamDim)
        }
        IconButton(onClick = onNotificationsTap) {
            Icon(Icons.Filled.Notifications, contentDescription = "Notifications", tint = CreamDim)
        }
        // Profile avatar with initials
        val initials = displayName.split(" ")
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "?" }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Gold.copy(alpha = 0.2f))
                .border(1.dp, Gold.copy(alpha = 0.5f), CircleShape)
                .clickable(onClick = onProfileTap),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initials,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Gold,
            )
        }
        Spacer(Modifier.width(4.dp))
    }
}

private val lifeAreas = listOf(
    Pair("💰", "Wealth"),
    Pair("❤️", "Love"),
    Pair("💼", "Career"),
    Pair("🏥", "Health"),
    Pair("👨‍👩‍👧", "Family"),
    Pair("🎓", "Education"),
    Pair("🕉️", "Spiritual"),
    Pair("✦", "Destiny"),
)

@Composable
private fun LifeAreaOrbs(onNavigateToCharts: () -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(lifeAreas) { (emoji, label) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onNavigateToCharts() },
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(NavySurface, NavyVariant),
                            )
                        )
                        .border(1.dp, Gold.copy(alpha = 0.25f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = emoji, fontSize = 26.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = CreamDim,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun InsightCard(insight: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(NavySurface, NavyVariant)
                    )
                )
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "✦  Today's Insight",
                    style = MaterialTheme.typography.labelLarge,
                    color = Gold,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = insight,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CreamText,
                    lineHeight = 22.sp,
                )
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    emoji: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = emoji, fontSize = 28.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = CreamDim,
            )
        }
    }
}

@Composable
private fun SuggestedQuestion(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(NavySurface)
            .border(0.5.dp, Gold.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "→", color = Gold, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = CreamText,
            )
        }
    }
}

private fun timeBasedGreeting(): String {
    return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        in 18..21 -> "Good evening"
        else -> "Good night"
    }
}
