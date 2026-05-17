package com.destinyai.astrology.ui.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.destinyai.astrology.R
import com.destinyai.astrology.ui.chat.ChatScreen
import com.destinyai.astrology.ui.compatibility.CompatibilityScreen
import com.destinyai.astrology.ui.home.HomeScreen

@Composable
fun MainScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateToCharts: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToPartners: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_home)) },
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_chat)) },
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_match)) },
                )
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> HomeScreen(
                modifier = Modifier.padding(padding),
                onNavigateToCharts = onNavigateToCharts,
                onNavigateToHistory = onNavigateToHistory,
                onNavigateToNotifications = onNavigateToNotifications,
                onNavigateToProfile = onNavigateToProfile,
            )
            1 -> ChatScreen(modifier = Modifier.padding(padding))
            2 -> CompatibilityScreen(
                modifier = Modifier.padding(padding),
                onBack = {},
                onNavigateToPartners = onNavigateToPartners,
            )
        }
    }
}
