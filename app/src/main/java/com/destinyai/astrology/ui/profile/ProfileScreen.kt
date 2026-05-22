package com.destinyai.astrology.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.destinyai.astrology.ui.theme.NavyVariant

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSubscription: () -> Unit,
    onDeletedAccount: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadProfile() }
    LaunchedEffect(state.isDeleted) { if (state.isDeleted) onDeletedAccount() }

    if (state.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteConfirmation() },
            title = { Text("Delete Account", color = CreamText, fontFamily = CanelaFontFamily) },
            text = { Text("This will permanently delete your account and all data. This cannot be undone.", color = CreamDim) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDeleteAccount() }) {
                    Text("Delete", color = Color(0xFFFF5252), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteConfirmation() }) {
                    Text("Cancel", color = CreamDim)
                }
            },
            containerColor = NavySurface,
        )
    }

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
                    text = "Profile",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CanelaFontFamily,
                    color = Gold,
                    modifier = Modifier.weight(1f),
                )
            }

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Gold, modifier = Modifier.size(32.dp))
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Spacer(Modifier.height(8.dp))

                    // User card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(listOf(NavySurface, NavyVariant)))
                            .border(0.5.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                            .padding(20.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Avatar circle with initials
                            val initials = state.userName.split(" ")
                                .filter { it.isNotEmpty() }
                                .take(2)
                                .joinToString("") { it.first().uppercase() }
                                .ifEmpty { "?" }
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Gold.copy(alpha = 0.2f))
                                    .border(1.dp, Gold.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = initials,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Gold,
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = state.userName.ifEmpty { "Guest" },
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = CanelaFontFamily,
                                    color = CreamText,
                                )
                                if (state.email.isNotEmpty()) {
                                    Text(
                                        text = state.email,
                                        fontSize = 13.sp,
                                        color = CreamDim,
                                    )
                                }
                                if (state.isPremium) {
                                    Spacer(Modifier.height(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Gold.copy(alpha = 0.2f))
                                            .border(0.5.dp, Gold, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 3.dp),
                                    ) {
                                        Text(
                                            text = "✦  Premium",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Gold,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Daily quota card
                    if (state.dailyQuota > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(NavySurface)
                                .border(0.5.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                                .padding(16.dp),
                        ) {
                            Column {
                                Text(
                                    text = "Daily Questions",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Gold.copy(alpha = 0.7f),
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "${state.dailyUsed} / ${state.dailyQuota} used today",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CreamText,
                                )
                            }
                        }
                    }

                    // Settings button
                    OutlinedButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(width = 1.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = CreamText,
                            containerColor = NavySurface,
                        ),
                    ) {
                        Text("Settings", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }

                    // Upgrade button (non-premium only)
                    if (!state.isPremium) {
                        Button(
                            onClick = onNavigateToSubscription,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Gold,
                                contentColor = Color(0xFF0D0D1A),
                            ),
                        ) {
                            Text("✦  Upgrade to Premium", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    if (state.error != null) {
                        Text(
                            text = state.error ?: "",
                            color = Color(0xFFFF8A80),
                            fontSize = 13.sp,
                        )
                    }

                    TextButton(
                        onClick = { viewModel.showDeleteConfirmation() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Delete Account",
                            color = Color(0xFFFF5252),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}
