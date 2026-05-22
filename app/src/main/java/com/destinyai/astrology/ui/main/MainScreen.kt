package com.destinyai.astrology.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.R
import com.destinyai.astrology.ui.chat.ChatScreen
import com.destinyai.astrology.ui.compatibility.CompatibilityScreen
import com.destinyai.astrology.ui.home.HomeScreen
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.GoldGradient
import com.destinyai.astrology.ui.theme.NavyDeep

@Composable
fun MainScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateToCharts: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToPartners: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize().background(NavyDeep)) {
        when (selectedTab) {
            0 -> HomeScreen(
                modifier = Modifier.fillMaxSize(),
                onNavigateToCharts = onNavigateToCharts,
                onNavigateToHistory = onNavigateToHistory,
                onNavigateToNotifications = onNavigateToNotifications,
                onNavigateToProfile = onNavigateToProfile,
            )
            1 -> ChatScreen(modifier = Modifier.fillMaxSize())
            2 -> CompatibilityScreen(
                modifier = Modifier.fillMaxSize(),
                onBack = {},
                onNavigateToPartners = onNavigateToPartners,
            )
        }

        // Tab bar — hidden on chat tab (index 1)
        if (selectedTab != 1) {
            DestinyTabBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun DestinyTabBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        // Background bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.BottomCenter)
                .background(NavyDeep)
        ) {
            // Gold top border line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, Gold.copy(alpha = 0.6f), Color.Transparent)
                        )
                    )
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Home tab
            TabBarItem(
                icon = { Icon(Icons.Filled.Home, contentDescription = null, tint = if (selectedTab == 0) CreamText else Gold.copy(alpha = 0.4f), modifier = Modifier.size(22.dp)) },
                label = stringResource(R.string.home),
                selected = selectedTab == 0,
                onClick = { onTabSelected(0) },
                modifier = Modifier.weight(1f),
            )

            // Center Ask FAB
            Column(
                modifier = Modifier
                    .weight(1f)
                    .offset(y = (-16).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFD4AF37), Color(0xFFF5D060), Color(0xFFD4AF37)),
                                start = Offset(0f, 0f),
                                end = Offset(56f, 56f),
                            )
                        )
                        .clickable { onTabSelected(1) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        tint = Color(0xFF0D0D1A),
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.ask),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (selectedTab == 1) CreamText else Gold.copy(alpha = 0.4f),
                )
            }

            // Match tab
            TabBarItem(
                icon = { Icon(Icons.Filled.Favorite, contentDescription = null, tint = if (selectedTab == 2) CreamText else Gold.copy(alpha = 0.4f), modifier = Modifier.size(22.dp)) },
                label = stringResource(R.string.match),
                selected = selectedTab == 2,
                onClick = { onTabSelected(2) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TabBarItem(
    icon: @Composable () -> Unit,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) CreamText else Gold.copy(alpha = 0.4f),
            textAlign = TextAlign.Center,
        )
    }
}
