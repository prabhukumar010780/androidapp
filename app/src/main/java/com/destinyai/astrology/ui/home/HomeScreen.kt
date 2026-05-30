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

@OptIn(ExperimentalMaterial3Api::class)
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

    // Life area questions bottom sheet
    state.selectedLifeArea?.let { lifeArea ->
        LifeAreaQuestionsSheet(
            lifeArea = lifeArea,
            onDismiss = { viewModel.dismissLifeArea() },
        )
    }

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
                    LifeAreaOrbs(
                        lifeAreas = state.lifeAreas,
                        onAreaTap = { area -> viewModel.selectLifeArea(area) },
                        onNavigateToCharts = onNavigateToCharts,
                    )
                }

                // Daily Insight card
                if (state.dailyInsight != null) {
                    item {
                        InsightCard(insight = state.dailyInsight.orEmpty())
                    }
                }

                // Dasha insight card
                if (state.dashaInfo != null) {
                    item {
                        state.dashaInfo?.let { DashaInsightCard(dashaInfo = it) }
                    }
                }

                // Transit alerts (horizontal scroll)
                if (state.transits.isNotEmpty()) {
                    item {
                        Text(
                            text = "Current Transits",
                            style = MaterialTheme.typography.labelLarge,
                            color = Gold.copy(alpha = 0.7f),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        TransitAlertsRow(transits = state.transits)
                    }
                }

                // Yoga highlights
                if (state.yogas.isNotEmpty()) {
                    item {
                        YogaHighlightRow(yogas = state.yogas)
                    }
                }

                // Dosha status chips
                if (state.doshas.hasMangalDosha || state.doshas.hasKalasarpa) {
                    item {
                        DoshaStatusRow(doshas = state.doshas)
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

                if (state.isLoading || state.isRichDataLoading) {
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

@Composable
private fun LifeAreaOrbs(
    lifeAreas: List<HomeLifeArea>,
    onAreaTap: (HomeLifeArea) -> Unit,
    onNavigateToCharts: () -> Unit,
) {
    // Fall back to static list if state is still empty on first render
    val areas = lifeAreas.ifEmpty {
        listOf(
            HomeLifeArea("Wealth", "💰", emptyList()),
            HomeLifeArea("Love", "❤️", emptyList()),
            HomeLifeArea("Career", "💼", emptyList()),
            HomeLifeArea("Health", "🏥", emptyList()),
            HomeLifeArea("Family", "👨‍👩‍👧", emptyList()),
            HomeLifeArea("Education", "🎓", emptyList()),
            HomeLifeArea("Spiritual", "🕉️", emptyList()),
            HomeLifeArea("Destiny", "✦", emptyList()),
        )
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(areas) { area ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable {
                    if (area.questions.isNotEmpty()) onAreaTap(area)
                    else onNavigateToCharts()
                },
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
                    Text(text = area.emoji, fontSize = 26.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = area.name,
                    fontSize = 11.sp,
                    color = CreamDim,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun DashaInsightCard(dashaInfo: HomeDashaInfo) {
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
                    Brush.linearGradient(listOf(Color(0xFF1A1040), Color(0xFF0D0826)))
                )
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "♈  Dasha Period",
                    style = MaterialTheme.typography.labelLarge,
                    color = Gold,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DashaChip(label = "Maha", value = dashaInfo.mahadasha)
                    DashaChip(label = "Antar", value = dashaInfo.antardasha)
                }
                if (dashaInfo.endsAt.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Period ends: ${dashaInfo.endsAt}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CreamDim,
                    )
                }
            }
        }
    }
}

@Composable
private fun DashaChip(label: String, value: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Gold.copy(alpha = 0.12f))
            .border(0.5.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, fontSize = 10.sp, color = CreamDim)
            Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Gold)
        }
    }
}

@Composable
private fun TransitAlertsRow(transits: List<HomeTransit>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(transits) { transit ->
            TransitAlertCard(transit = transit)
        }
    }
}

@Composable
private fun TransitAlertCard(transit: HomeTransit) {
    val chipColor = if (transit.isFavorable) Color(0xFF48BB78) else Color(0xFFFC8181)
    Card(
        modifier = Modifier.width(140.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(0.5.dp, Gold.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                .padding(12.dp),
        ) {
            Text(
                text = transit.planet,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = CreamText,
            )
            Text(
                text = "in ${transit.sign}",
                fontSize = 12.sp,
                color = CreamDim,
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(chipColor.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(
                    text = transit.influence,
                    fontSize = 10.sp,
                    color = chipColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun YogaHighlightRow(yogas: List<HomeYoga>) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "Active Yogas",
            style = MaterialTheme.typography.labelLarge,
            color = Gold.copy(alpha = 0.7f),
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(yogas) { yoga ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF2D1F5E).copy(alpha = 0.8f))
                        .border(0.5.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = yoga.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Gold,
                    )
                }
            }
        }
    }
}

@Composable
private fun DoshaStatusRow(doshas: HomeDoshaStatus) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "Dosha Alerts",
            style = MaterialTheme.typography.labelLarge,
            color = Gold.copy(alpha = 0.7f),
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (doshas.hasMangalDosha) {
                DoshaChip(
                    label = "Mangal Dosha",
                    severity = doshas.mangalSeverity,
                    color = Color(0xFFED8936),
                )
            }
            if (doshas.hasKalasarpa) {
                DoshaChip(
                    label = "Kala Sarpa",
                    severity = doshas.kalasarpaType,
                    color = Color(0xFFFC8181),
                )
            }
        }
    }
}

@Composable
private fun DoshaChip(label: String, severity: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .border(0.5.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Column {
            Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
            if (severity.isNotEmpty()) {
                Text(text = severity, fontSize = 10.sp, color = color.copy(alpha = 0.8f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LifeAreaQuestionsSheet(
    lifeArea: HomeLifeArea,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0D0826),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(36.dp, 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Gold.copy(alpha = 0.3f)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = lifeArea.emoji, fontSize = 28.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = lifeArea.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CanelaFontFamily,
                    color = CreamText,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Tap a question to ask the stars",
                fontSize = 13.sp,
                color = CreamDim,
            )
            Spacer(Modifier.height(16.dp))
            lifeArea.questions.forEach { question ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(NavySurface)
                        .border(0.5.dp, Gold.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .clickable { onDismiss() }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "→", color = Gold, fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = question,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CreamText,
                        )
                    }
                }
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
