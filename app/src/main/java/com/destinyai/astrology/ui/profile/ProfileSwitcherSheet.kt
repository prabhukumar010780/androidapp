package com.destinyai.astrology.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.People
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
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.NavyVariant

private val GoldDeepSwitcher = Color(0xFFA8862A)
private val AvatarGradientSwitcher = Brush.linearGradient(listOf(Gold, GoldDeepSwitcher, Gold))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSwitcherSheet(
    onDismiss: () -> Unit,
    onNavigateToPartners: () -> Unit = {},
    onNavigateToSubscription: () -> Unit = {},
    viewModel: ProfileSwitcherViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val activeEmail by viewModel.activeEmail.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // snackbar for upgrade_required
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.upgradeRequiredPrompt) {
        if (uiState.upgradeRequiredPrompt) {
            snackbarHostState.showSnackbar("Upgrade required to switch profiles")
            viewModel.dismissUpgradePrompt()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NavySurface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .size(36.dp, 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Gold.copy(alpha = 0.3f)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
        ) {
            Text(
                text = "Switch Profile",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CanelaFontFamily,
                color = Gold,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Gold, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else {
                // R2-P18 Active profile card (first/self entry)
                val activeProfile = profiles.firstOrNull { it.email == activeEmail } ?: profiles.firstOrNull()
                if (activeProfile != null) {
                    ActiveProfileCard(profile = activeProfile)
                    Spacer(Modifier.height(16.dp))
                }

                // R2-P20 Empty state for other profiles
                val otherProfiles = profiles.filter { it.email != activeEmail }
                if (otherProfiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.People,
                                contentDescription = null,
                                tint = CreamDim.copy(alpha = 0.4f),
                                modifier = Modifier.size(40.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "No saved birth charts yet",
                                fontSize = 14.sp,
                                color = CreamDim,
                            )
                        }
                    }
                } else {
                    otherProfiles.forEach { profile ->
                        val isActive = profile.email == activeEmail
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isActive) NavyVariant else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (isActive) Gold.copy(alpha = 0.5f) else Gold.copy(alpha = 0.15f),
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable {
                                    if (!isActive) {
                                        viewModel.switchProfile(profile.email)
                                        onDismiss()
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = profile.name,
                                        fontSize = 16.sp,
                                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isActive) Gold else CreamText,
                                    )
                                    // R2-P19 "(You)" badge
                                    if (profile.isSelf) {
                                        Spacer(Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Gold.copy(alpha = 0.15f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        ) {
                                            Text(text = "You", fontSize = 10.sp, color = Gold, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                            if (isActive) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = Gold,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                // R2-P21 Manage birth charts link
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        onDismiss()
                        onNavigateToPartners()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Manage birth charts", color = Gold, fontSize = 14.sp)
                }
            }

            SnackbarHost(hostState = snackbarHostState)
        }
    }
}

@Composable
private fun ActiveProfileCard(profile: ProfileEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NavyVariant)
            .border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 60dp gradient avatar
        val initials = profile.name.split(" ")
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "?" }
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(AvatarGradientSwitcher)
                .border(1.dp, Gold.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = initials, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D0D1A))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = profile.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = CreamText)
            Spacer(Modifier.height(4.dp))
            // "Active" gold badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Gold.copy(alpha = 0.2f))
                    .border(0.5.dp, Gold, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(text = "Active", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Gold)
            }
        }
    }
}
