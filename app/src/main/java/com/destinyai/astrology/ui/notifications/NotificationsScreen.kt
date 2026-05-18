package com.destinyai.astrology.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
fun NotificationsScreen(
    onBack: () -> Unit,
    onNotifPrefs: () -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadNotifications() }

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
                // Close button (circle)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(NavySurface),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = CreamDim,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Notifications",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CanelaFontFamily,
                        color = CreamText,
                    )
                    if (state.unreadCount > 0) {
                        Text(
                            text = "${state.unreadCount} unread",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Gold,
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // Mark all read (circle)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(NavySurface),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        onClick = { if (state.unreadCount > 0) viewModel.markAllRead() },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Preferences",
                            tint = if (state.unreadCount > 0) Gold else CreamDim,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // Personalize alerts button (gold CTA)
            Button(
                onClick = onNotifPrefs,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Gold,
                    contentColor = androidx.compose.ui.graphics.Color(0xFF0D0D1A),
                ),
            ) {
                Text("Notification Preferences", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }

            Spacer(Modifier.height(8.dp))

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Gold, modifier = Modifier.size(28.dp))
                }
            } else if (state.notifications.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "No notifications yet", color = CreamDim, fontSize = 16.sp)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.notifications, key = { it.id }) { notif ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(NavySurface)
                                .border(
                                    1.dp,
                                    if (!notif.isRead) Gold.copy(alpha = 0.3f) else androidx.compose.ui.graphics.Color.Transparent,
                                    RoundedCornerShape(16.dp),
                                )
                                .padding(16.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            // Icon circle
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (!notif.isRead) Gold.copy(alpha = 0.15f) else NavySurface,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(text = "✦", fontSize = 18.sp, color = if (!notif.isRead) Gold else CreamDim)
                            }

                            Spacer(Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = notif.title,
                                    fontSize = 15.sp,
                                    fontWeight = if (!notif.isRead) FontWeight.Bold else FontWeight.Medium,
                                    color = CreamText,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = notif.body,
                                    fontSize = 13.sp,
                                    color = CreamDim,
                                    lineHeight = 18.sp,
                                )
                            }

                            // Unread dot
                            if (!notif.isRead) {
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Gold),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
