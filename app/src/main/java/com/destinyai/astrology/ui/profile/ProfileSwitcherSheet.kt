package com.destinyai.astrology.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.R
import com.destinyai.astrology.services.HapticManager
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
    val activeProfileId by viewModel.activeProfileId.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isSwitching by viewModel.isSwitching.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = remember { HapticManager(context) }

    // Upgrade-required prompt — iOS parity (ProfileSwitcherSheet.swift:128-132):
    // when switching fails because the user's tier doesn't allow it, present
    // SubscriptionView directly instead of just flashing a snackbar.
    LaunchedEffect(uiState.upgradeRequiredPrompt) {
        if (uiState.upgradeRequiredPrompt) {
            viewModel.dismissUpgradePrompt()
            onDismiss()
            onNavigateToSubscription()
        }
    }

    // Auto-dismiss after a successful profile switch — observe the falling edge
    // of isSwitching. iOS dismisses on completion; Android matches by tracking the
    // transition true→false and closing the sheet (no upgrade prompt = success path).
    var wasSwitching by remember { mutableStateOf(false) }
    LaunchedEffect(isSwitching) {
        if (isSwitching) {
            wasSwitching = true
        } else if (wasSwitching && !uiState.upgradeRequiredPrompt) {
            wasSwitching = false
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isSwitching) onDismiss() },
        containerColor = NavySurface,
        properties = ModalBottomSheetProperties(
            securePolicy = androidx.compose.ui.window.SecureFlagPolicy.Inherit,
            shouldDismissOnBackPress = !isSwitching,
        ),
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
                .padding(bottom = 40.dp)
                .testTag("profile_switcher_sheet"),
        ) {
            // Header — title + (X close button OR ProgressView while switching) + Add chart button.
            // iOS parity: ProfileSwitcherSheet.swift:67-81 and 209-230.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.profile_switch_profile),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CanelaFontFamily,
                    color = Gold,
                    modifier = Modifier.weight(1f),
                )
                // R2-A: Add new birth chart button — iOS ProfileSwitcherSheet.swift:209-230
                // Routes to Partners screen which performs the quota check before opening
                // the partner-add form. This is the minimum-viable parity per the fix spec.
                IconButton(
                    onClick = {
                        if (!isSwitching) {
                            haptic.light()
                            onDismiss()
                            onNavigateToPartners()
                        }
                    },
                    enabled = !isSwitching,
                    modifier = Modifier.testTag("profile_switcher_add_chart"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.profile_switcher_add_chart_cd),
                        tint = Gold,
                    )
                }
                // Switching spinner (iOS ProfileSwitcherSheet.swift:67-70) replaces
                // the X close button while a profile switch is in flight.
                if (isSwitching) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("profile_switcher_switching_indicator"),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = Gold,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            haptic.light()
                            onDismiss()
                        },
                        modifier = Modifier.testTag("profile_switcher_close"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.profile_switcher_close_cd),
                            tint = CreamDim,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Gold, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else {
                // R2-P18 Active profile card (first/self entry)
                val activeProfile = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.firstOrNull()
                if (activeProfile != null) {
                    ActiveProfileCard(profile = activeProfile)
                    Spacer(Modifier.height(16.dp))
                }

                // R2-P19 Switch-to-self row — iOS ProfileSwitcherSheet.swift:101-117.
                // When the active profile is a partner (not self), surface the self
                // profile as a tappable row so the user can return to their own chart.
                val selfProfile = profiles.firstOrNull { it.isSelf }
                if (selfProfile != null && activeProfileId != selfProfile.id) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, Gold.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                            .clickable(enabled = !isSwitching) {
                                haptic.light()
                                viewModel.switchProfile(selfProfile.id)
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .testTag("profile_switcher_switch_to_self"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = selfProfile.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = CreamText,
                                )
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Gold.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.profile_you_badge),
                                        fontSize = 10.sp,
                                        color = Gold,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                            // iOS parity: ProfileSwitcherSheet.swift:399-401 — DOB caption
                            // beneath the name in 12sp CreamDim.
                            val selfDob = formatDateOfBirth(selfProfile.dateOfBirth)
                            if (selfDob != null) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = selfDob,
                                    fontSize = 12.sp,
                                    color = CreamDim,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // R2-P20 Empty state for other profiles — partners only (self handled above).
                val otherProfiles = profiles.filter { it.id != activeProfileId && !it.isSelf }
                if (otherProfiles.isEmpty() && (selfProfile == null || activeProfileId == selfProfile.id)) {
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
                                text = stringResource(R.string.profile_no_saved_birth_charts),
                                fontSize = 14.sp,
                                color = CreamDim,
                            )
                        }
                    }
                } else {
                    otherProfiles.forEach { profile ->
                        val isActive = profile.id == activeProfileId
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
                                .clickable(enabled = !isSwitching) {
                                    if (!isActive) {
                                        haptic.light()
                                        viewModel.switchProfile(profile.id)
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
                                            Text(text = stringResource(R.string.profile_you_badge), fontSize = 10.sp, color = Gold, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                                // iOS parity: ProfileSwitcherSheet.swift:399-401 — DOB caption
                                // beneath the name in 12sp CreamDim.
                                val partnerDob = formatDateOfBirth(profile.dateOfBirth)
                                if (partnerDob != null) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = partnerDob,
                                        fontSize = 12.sp,
                                        color = CreamDim,
                                    )
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
                        haptic.light()
                        onDismiss()
                        onNavigateToPartners()
                    },
                    enabled = !isSwitching,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_switcher_manage_link"),
                ) {
                    Text(stringResource(R.string.profile_manage_birth_charts_link), color = Gold, fontSize = 14.sp)
                }
            }
        }
    }

    // iOS parity: ProfileSwitcherSheet.swift:201-205 — non-upgrade switch failures
    // surface as a "Profile Switch Failed" alert with an OK dismiss button.
    val switchError = uiState.switchError
    if (switchError != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = {
                Text(text = stringResource(R.string.profile_switch_failed_title))
            },
            text = {
                Text(
                    text = if (switchError.isBlank()) {
                        stringResource(R.string.profile_switch_failed_message)
                    } else {
                        switchError
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.light()
                        viewModel.dismissError()
                    },
                    modifier = Modifier.testTag("profile_switch_error_ok"),
                ) {
                    Text(stringResource(R.string.ok_action))
                }
            },
            modifier = Modifier.testTag("profile_switch_error_dialog"),
        )
    }
}

/**
 * Format an ISO yyyy-MM-dd birth date as a long, locale-aware caption (e.g.
 * "July 1, 1980"). Mirrors iOS PartnerProfile.formattedDateOfBirth which uses
 * DateFormatter.dateStyle = .long.
 */
private fun formatDateOfBirth(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return try {
        val parsed = java.time.LocalDate.parse(raw)
        val formatter = java.time.format.DateTimeFormatter
            .ofLocalizedDate(java.time.format.FormatStyle.LONG)
            .withLocale(java.util.Locale.getDefault())
        parsed.format(formatter)
    } catch (_: Exception) {
        raw
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
                Text(text = stringResource(R.string.profile_active_badge), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Gold)
            }
        }
    }
}
