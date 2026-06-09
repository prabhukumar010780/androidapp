package com.destinyai.astrology.ui.partners

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.R
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.PartnerDto
import com.destinyai.astrology.services.HapticManager
import com.destinyai.astrology.services.SoundManager
import com.destinyai.astrology.ui.subscription.SubscriptionScreen
import com.destinyai.astrology.ui.theme.AppTheme
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.NavyVariant
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Partner Picker Sheet for the Partners area — mirrors iOS
 * PartnerPickerSheet.swift (Views/Partners/PartnerPickerSheet.swift).
 *
 * Differs from the compatibility-area PartnerPickerSheet (which is bound to
 * CompatibilityViewModel) by being decoupled — it takes an onSelect callback
 * so any caller (Compatibility flow, future flows) can reuse the same UI by
 * passing a different handler. Mirrors the iOS API:
 *   PartnerPickerSheet(gender, excludeIds, forCompatibilityOnly, onSelect)
 *
 * Filters the saved partners list by gender, excluded IDs (active profile +
 * already-selected partners), forCompatibility flag, and a search query
 * (name or city).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerPickerSheet(
    onDismiss: () -> Unit,
    onSelect: (PartnerDto) -> Unit,
    gender: String? = null,
    excludeIds: Set<String> = emptySet(),
    forCompatibilityOnly: Boolean = false,
    onAddNew: (() -> Unit)? = null,
    viewModel: PartnersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var searchText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val haptic = remember { HapticManager(context) }
    // iOS parity (PartnerPickerSheet.swift:104-106): success haptic + chord on
    // partner add — wired through Hilt EntryPoint since this composable is not
    // injected directly. Also exposes UserPreferences so the picker can read
    // the active profile id (iOS parity PartnerPickerSheet.swift:35-40 — the
    // redundant-but-safe filter that excludes the active profile in addition
    // to the caller-supplied excludeIds).
    val pickerEntryPoint = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            PartnerPickerSoundEntryPoint::class.java,
        )
    }
    val soundManager = remember(pickerEntryPoint) { pickerEntryPoint.soundManager() }
    val userPrefs = remember(pickerEntryPoint) { pickerEntryPoint.userPreferences() }
    // iOS parity (PartnerPickerSheet.swift:35-40): resolve active profile id
    // once and add it to the exclusion set as a redundant safety filter.
    var activeProfileId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        activeProfileId = userPrefs.getActiveProfileId()
    }
    // iOS parity (PartnerPickerSheet.swift:18-20, 99-129): inline add-partner
    // sheet, upgrade prompt sheet, and profile-limit alert hosted by the picker
    // itself rather than delegated up to the caller.
    var showAddForm by remember { mutableStateOf(false) }
    var showUpgradePrompt by remember { mutableStateOf(false) }
    var limitMessage by remember { mutableStateOf<String?>(null) }
    val profileLimitMessageTemplate =
        stringResource(R.string.partner_quota_upgrade_message_with_limit)

    LaunchedEffect(Unit) { viewModel.loadPartners() }

    // iOS parity (PartnerPickerSheet.swift:299-314 checkAndShowAddForm): observe
    // viewModel quota state and route to either the form, the upgrade sheet, or
    // the limit alert based on the same canAdd / limit==0 / limit branches.
    LaunchedEffect(state.showAddForm, state.showQuotaUpgradePrompt, state.quotaLimit) {
        if (state.showAddForm) {
            showAddForm = true
        }
        if (state.showQuotaUpgradePrompt) {
            if (state.quotaLimit == 0) {
                showUpgradePrompt = true
            } else if (state.quotaLimit > 0) {
                limitMessage = profileLimitMessageTemplate.format(state.quotaLimit)
            }
            viewModel.dismissQuotaUpgradePrompt()
        }
    }

    // iOS parity (PartnerPickerSheet.swift:22-61): apply filter chain.
    val shouldExcludeSelf = excludeIds.contains("self") ||
        activeProfileId == "self" ||
        activeProfileId == null
    val effectiveExcludeIds: Set<String> = remember(excludeIds, activeProfileId) {
        // iOS parity (PartnerPickerSheet.swift:35-40): also exclude the active
        // profile id as a fallback (redundant but safe).
        val active = activeProfileId
        if (active != null && active != "self") excludeIds + active else excludeIds
    }
    val filtered: List<PartnerDto> = remember(
        state.partners,
        searchText,
        gender,
        effectiveExcludeIds,
        shouldExcludeSelf,
        forCompatibilityOnly,
    ) {
        state.partners
            .filter { p ->
                if (shouldExcludeSelf && p.isSelf) false
                else !effectiveExcludeIds.contains(p.id)
            }
            .filter { p -> gender == null || p.gender == gender }
            .filter { p -> !forCompatibilityOnly || p.forCompatibility }
            .filter { p ->
                if (searchText.isBlank()) true
                else p.name.contains(searchText, ignoreCase = true) ||
                    (p.cityOfBirth?.contains(searchText, ignoreCase = true) == true)
            }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NavySurface,
    ) {
        // iOS parity (PartnerPickerSheet.swift:64-68): wrap the sheet content
        // with the CosmicBackground composable so the picker shares the same
        // ambient cosmic gradient as the rest of the premium surfaces.
        CosmicBackground(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp)
                    .semantics { contentDescription = "partner_picker_sheet" },
            ) {
            // iOS parity (PartnerPickerSheet.swift:91-97): top-bar Cancel button so users
            // have a visible text affordance to dismiss instead of relying on drag/scrim.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.partner_picker_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Gold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        haptic.light()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("partner_picker_cancel"),
                ) {
                    Text(
                        text = stringResource(R.string.cancel_action),
                        color = CreamDim,
                        fontSize = 14.sp,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // Search bar (iOS parity lines 135-155)
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.partner_picker_search_placeholder),
                        color = CreamDim,
                    )
                },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = Gold)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions.Default,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Gold,
                    unfocusedBorderColor = Gold.copy(alpha = 0.25f),
                    focusedTextColor = CreamText,
                    unfocusedTextColor = CreamText,
                    cursorColor = Gold,
                    unfocusedContainerColor = NavyVariant,
                    focusedContainerColor = NavyVariant,
                ),
            )
            Spacer(Modifier.height(12.dp))

            if (state.isLoading && state.partners.isEmpty()) {
                // iOS parity (PartnerPickerSheet.swift:159-169 loadingView):
                // gold spinner + "loading_birth_charts" label while the first
                // fetch is in flight and no cache is available.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                        .testTag("partner_picker_loading"),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(
                            color = Gold,
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = stringResource(R.string.loading_birth_charts),
                            color = CreamDim,
                            fontSize = 14.sp,
                        )
                    }
                }
            } else if (filtered.isEmpty()) {
                // iOS parity (PartnerPickerSheet.swift:173-192 emptyView):
                // person.2.slash icon (premium gold gradient at 80% opacity)
                // above the contextual empty-state copy.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                        .testTag("partner_picker_empty")
                        .semantics { contentDescription = "partner_picker_empty_state" },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PersonOff,
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier
                                .size(50.dp)
                                .graphicsLayer { alpha = 0.8f }
                                .background(
                                    brush = AppTheme.gradients.gold,
                                    shape = CircleShape,
                                )
                                .padding(8.dp),
                        )
                        Text(
                            text = if (state.partners.isEmpty()) {
                                stringResource(R.string.no_saved_birth_charts_yet)
                            } else {
                                stringResource(R.string.partner_picker_no_matches)
                            },
                            color = CreamDim,
                            fontSize = 15.sp,
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 360.dp),
                ) {
                    items(filtered, key = { it.id }) { partner ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(NavySurface)
                                .border(0.5.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .clickable {
                                    // iOS parity (PartnerPickerSheet.swift:211-215):
                                    // medium haptic + button-tap chord on row tap.
                                    haptic.medium()
                                    soundManager.playButtonTap()
                                    onSelect(partner)
                                    onDismiss()
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .testTag("partner_picker_row_${partner.id}")
                                .semantics {
                                    contentDescription = "partner_picker_row"
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            // iOS parity (PartnerPickerSheet.swift:218-228): 44dp gold-gradient
                            // avatar circle with the partner's first initial.
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        brush = Brush.linearGradient(
                                            listOf(Gold, Color(0xFFF5D060)),
                                        ),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = partner.name.firstOrNull()?.uppercase() ?: "?",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0D0D1A),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                // iOS parity (PartnerPickerSheet.swift:232-234): no
                                // hardcoded fallback — empty name renders as empty.
                                Text(
                                    text = partner.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CreamText,
                                )
                                // iOS parity (PartnerPickerSheet.swift:236-251):
                                // gender symbol (gold gradient) + formatted DOB + city
                                // joined by " · " in a sub-row.
                                PartnerPickerInfoRow(partner = partner)
                            }
                            // iOS parity (PartnerPickerSheet.swift:256-258): trailing chevron
                            // suggesting forward navigation / tappable row.
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = stringResource(R.string.partner_chevron_cd),
                                tint = Gold.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }

            // Add-new affordance — iOS parity (PartnerPickerSheet.swift:270-295).
            // iOS hosts the entire add flow (quota check → form / upgrade /
            // limit alert) inside the picker. Android now does the same: tap
            // routes through PartnersViewModel.requestAddPartner(), which sets
            // showAddForm / showQuotaUpgradePrompt / quotaLimit; the
            // LaunchedEffect above translates those into the local sheet/alert
            // state. onAddNew remains as an opt-in override for rare callers
            // that want to drive the form themselves.
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(0.5.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .clickable {
                        haptic.light()
                        if (onAddNew != null) {
                            onAddNew()
                            onDismiss()
                        } else {
                            viewModel.requestAddPartner()
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = Gold,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.partner_picker_add_new),
                    color = Gold,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            }
        }
    }

    // iOS parity (PartnerPickerSheet.swift:99-109): inline add-partner sheet
    // hosted by the picker. Wraps the same PartnerFormSheet used by
    // PartnerManagerView. On successful add (showAddForm flips false while
    // the sheet is open and not saving) play the success chord + shimmer
    // haptic, matching iOS PartnerProfileViewModel.addPartner.
    if (showAddForm) {
        // Track partner-list growth to detect a successful add — iOS plays
        // SoundManager.shared.playSuccess() + HapticManager.shared.playShimmer().
        val baselineCount = remember(showAddForm) { state.partners.size }
        LaunchedEffect(state.partners.size, state.isSaving) {
            if (!state.isSaving && state.partners.size > baselineCount) {
                soundManager.playSuccess()
                haptic.playShimmer()
                showAddForm = false
                viewModel.toggleAddForm()
            }
        }
        PartnerFormSheet(
            state = state,
            viewModel = viewModel,
            onDismiss = {
                showAddForm = false
                if (state.showAddForm) viewModel.toggleAddForm()
            },
        )
    }

    // iOS parity (PartnerPickerSheet.swift:116-118): SubscriptionView in a
    // sheet when the picker decides the user must upgrade.
    if (showUpgradePrompt) {
        ModalBottomSheet(
            onDismissRequest = { showUpgradePrompt = false },
            containerColor = NavySurface,
        ) {
            SubscriptionScreen(
                onBack = { showUpgradePrompt = false },
            )
        }
    }

    // iOS parity (PartnerPickerSheet.swift:119-129): "Profile Limit Reached"
    // alert with Upgrade / OK buttons surfaced when the quota check returns a
    // positive limit (>0) but the user is already at it.
    if (limitMessage != null) {
        AlertDialog(
            onDismissRequest = { limitMessage = null },
            title = {
                Text(text = stringResource(R.string.profile_limit_reached_title))
            },
            text = { Text(text = limitMessage ?: "") },
            confirmButton = {
                TextButton(
                    onClick = {
                        limitMessage = null
                        showUpgradePrompt = true
                    },
                    modifier = Modifier.testTag("partner_picker_limit_upgrade"),
                ) {
                    Text(stringResource(R.string.upgrade_action), color = Gold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { limitMessage = null },
                    modifier = Modifier.testTag("partner_picker_limit_ok"),
                ) {
                    Text(stringResource(R.string.ok_action), color = CreamDim)
                }
            },
            containerColor = NavyVariant,
            titleContentColor = Gold,
            textContentColor = CreamText,
        )
    }
}

// iOS parity (PartnerPickerSheet.swift:104-105): Hilt EntryPoint that exposes
// the application-scoped SoundManager so this composable can play the success
// chord on partner add without forcing SoundManager into PartnersViewModel.
// Also exposes UserPreferences so the picker can read the active profile id
// (iOS parity PartnerPickerSheet.swift:35-40 — redundant safety filter).
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PartnerPickerSoundEntryPoint {
    fun soundManager(): SoundManager
    fun userPreferences(): UserPreferences
}

/**
 * iOS parity (PartnerPickerSheet.swift:236-251) — sub-row showing the
 * partner's gender symbol (gold gradient), formatted date of birth, and
 * city, joined by " · " separators.
 */
@Composable
private fun PartnerPickerInfoRow(partner: PartnerDto) {
    val symbol = when (partner.gender) {
        "male" -> stringResource(R.string.partner_gender_symbol_male)
        "female" -> stringResource(R.string.partner_gender_symbol_female)
        else -> ""
    }
    val dob = formatPickerDob(partner.dateOfBirth)
    val city = partner.cityOfBirth?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    if (symbol.isEmpty() && dob.isNullOrEmpty() && city.isNullOrEmpty()) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (symbol.isNotEmpty()) {
            Text(
                text = symbol,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(
                    brush = Brush.linearGradient(listOf(Gold, Color(0xFFF5D060))),
                ),
            )
        }
        if (!dob.isNullOrEmpty()) {
            Text(text = dob, color = CreamDim, fontSize = 12.sp)
        }
        if (!city.isNullOrEmpty()) {
            if (!dob.isNullOrEmpty() || symbol.isNotEmpty()) {
                Text(text = "•", color = CreamDim, fontSize = 12.sp)
            }
            Text(text = city, color = CreamDim, fontSize = 12.sp, maxLines = 1)
        }
    }
}

private fun formatPickerDob(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return try {
        val inFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = inFmt.parse(raw) ?: return raw
        val outFmt = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
        outFmt.format(date)
    } catch (_: Exception) {
        raw
    }
}
