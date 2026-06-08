package com.destinyai.astrology.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.R
import com.destinyai.astrology.services.HapticManager
import com.destinyai.astrology.ui.partners.PartnerFormSheet
import com.destinyai.astrology.ui.partners.PartnersViewModel
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
    partnersViewModel: PartnersViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val activeProfileId by viewModel.activeProfileId.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isSwitching by viewModel.isSwitching.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val partnersState by partnersViewModel.uiState.collectAsStateWithLifecycle()
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

    // iOS parity (ProfileSwitcherSheet.swift:209-230): on quota pass, present
    // PartnerFormView inline as a .sheet directly within the switcher (no
    // navigation to the PartnerManager screen). Android equivalent: when the
    // ProfileSwitcherViewModel flips uiState.showAddForm true after the quota
    // check, open the shared PartnersViewModel's add-form so the existing
    // PartnerFormSheet composable can render inline below.
    LaunchedEffect(uiState.showAddForm) {
        if (uiState.showAddForm && !partnersState.showAddForm) {
            partnersViewModel.toggleAddForm()
        }
        if (uiState.showAddForm) {
            // Reset the trigger flag — the inline sheet now owns the visibility.
            viewModel.dismissAddForm()
        }
    }

    // When the inline form closes after a successful save, partnersState.showAddForm
    // flips false with no error. Reload the switcher's profile list so the new
    // partner appears immediately — iOS parity (ProfileSwitcherSheet.swift:193-200
    // .onChange refresh) without requiring a navigation round-trip.
    var partnersFormWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(partnersState.showAddForm) {
        if (partnersState.showAddForm) {
            partnersFormWasOpen = true
        } else if (partnersFormWasOpen) {
            partnersFormWasOpen = false
            viewModel.reloadProfiles()
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
                // Triggers the QuotaManager.canAddProfile pre-check; on pass surfaces
                // the inline add-partner form, on free-tier exceedance opens the
                // upgrade sheet, and on core-tier exceedance shows the limit alert.
                IconButton(
                    onClick = {
                        if (!isSwitching) {
                            haptic.light()
                            viewModel.requestAddPartner()
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
                // iOS parity: ProfileSwitcherSheet.swift:49-56 — spinner + caption.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .semantics { contentDescription = "profile_switcher_loading" },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Gold, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.loading_profiles),
                            fontSize = 14.sp,
                            color = CreamDim,
                        )
                    }
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
                        horizontalArrangement = Arrangement.spacedBy(15.dp),
                    ) {
                        // iOS parity: ProfileSwitcherSheet.swift:373-380 — 44dp avatar circle
                        // with the first letter of the name.
                        ProfileAvatar(name = selfProfile.name)
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
                if (otherProfiles.isEmpty() && selfProfile == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp)
                            .semantics { contentDescription = "profile_switcher_empty_state" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.People,
                                contentDescription = null,
                                tint = CreamDim.copy(alpha = 0.4f),
                                modifier = Modifier.size(40.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.profile_no_saved_birth_charts),
                                fontSize = 16.sp,
                                color = CreamDim,
                            )
                            Spacer(Modifier.height(4.dp))
                            // iOS parity: ProfileSwitcherSheet.swift:147-149 — secondary
                            // "add_partner_to_start" caption beneath the empty-state line.
                            Text(
                                text = stringResource(R.string.add_partner_to_start),
                                fontSize = 14.sp,
                                color = CreamDim.copy(alpha = 0.7f),
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
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .semantics { contentDescription = "profile_row_${profile.id}" },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(15.dp),
                        ) {
                            // iOS parity: ProfileSwitcherSheet.swift:373-380 — 44dp avatar
                            // circle with the first letter of the profile name.
                            ProfileAvatar(name = profile.name)
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

                // R2-P21 Manage birth charts link — iOS parity
                // (ProfileSwitcherSheet.swift:160-181): bordered card with leading
                // plus icon, gold label, trailing chevron, gold border + 12dp corner.
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(NavySurface)
                        .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .clickable(enabled = !isSwitching) {
                            haptic.light()
                            onNavigateToPartners()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("profile_switcher_manage_link")
                        .semantics { contentDescription = "manage_birth_charts_link" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddCircle,
                        contentDescription = null,
                        tint = Gold,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.profile_manage_birth_charts_link),
                        color = Gold,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = Gold,
                        modifier = Modifier.size(14.dp),
                    )
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

    // iOS parity (ProfileSwitcherSheet.swift:231-241): Profile-Limit-Reached alert.
    // Shown when the maintain_profile quota is exhausted on a paid (non-zero) tier.
    // Upgrade routes to the subscription sheet; OK dismisses without further action.
    val limitMessage = uiState.limitMessage
    if (limitMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissLimitMessage() },
            title = {
                Text(text = stringResource(R.string.profile_limit_reached_title))
            },
            text = {
                Text(text = limitMessage)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.light()
                        viewModel.upgradeFromLimit()
                    },
                    modifier = Modifier.testTag("profile_limit_upgrade"),
                ) {
                    Text(stringResource(R.string.upgrade_action), color = Gold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        haptic.light()
                        viewModel.dismissLimitMessage()
                    },
                    modifier = Modifier.testTag("profile_limit_ok"),
                ) {
                    Text(stringResource(R.string.ok_action))
                }
            },
            modifier = Modifier.testTag("profile_limit_dialog"),
        )
    }

    // iOS parity (ProfileSwitcherSheet.swift:209-230 .sheet(isPresented: $showAddForm)
    // PartnerFormView): inline add-partner form presented as a ModalBottomSheet
    // directly from the switcher. Reuses the canonical PartnerFormSheet bound to
    // PartnersViewModel — same fields, validation, save flow, and success cues
    // as the full PartnerManager screen, without the navigation hop.
    if (partnersState.showAddForm) {
        PartnerFormSheet(
            state = partnersState,
            viewModel = partnersViewModel,
            onDismiss = { partnersViewModel.toggleAddForm() },
        )
    }

    // iOS parity (ProfileSwitcherSheet.swift:193-200 .onChange refresh): when the
    // user returns from PartnerManager, reload the profile list so a newly-added
    // partner shows up immediately without re-opening the sheet.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.reloadProfiles()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
    // iOS parity: ProfileSwitcherSheet.swift:309-360 — vertical centered card
    // with premium gradient avatar, shadow, name + (You) badge, and Active pill.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Gold.copy(alpha = 0.3f),
                spotColor = Gold.copy(alpha = 0.3f),
            )
            .clip(RoundedCornerShape(16.dp))
            .background(NavySurface)
            .border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(20.dp)
            .semantics { contentDescription = "active_profile_card" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 60dp gradient avatar (initial only — iOS uses prefix(1))
        val initial = profile.name.firstOrNull()?.uppercase() ?: "?"
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(AvatarGradientSwitcher)
                .border(1.dp, Gold.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = initial, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D0D1A), fontFamily = CanelaFontFamily)
        }

        // Name + (You) badge row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = profile.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = CreamText,
                fontFamily = CanelaFontFamily,
            )
            if (profile.isSelf) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Gold.copy(alpha = 0.10f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.profile_you_badge),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Gold,
                    )
                }
            }
        }

        // "Active" gold pill badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Gold.copy(alpha = 0.10f))
                .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.profile_active_badge),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Gold,
            )
        }
    }
}

/**
 * iOS parity: ProfileSwitcherSheet.swift:373-380 — 44dp circular avatar showing
 * the first letter of the profile name on the surface background.
 */
@Composable
private fun ProfileAvatar(name: String) {
    val initial = name.firstOrNull()?.uppercase() ?: "?"
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(NavyVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = CreamText,
            fontFamily = CanelaFontFamily,
        )
    }
}
