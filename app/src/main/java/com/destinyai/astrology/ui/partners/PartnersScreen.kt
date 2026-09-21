package com.destinyai.astrology.ui.partners

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.R
import com.destinyai.astrology.data.remote.LocationResult
import com.destinyai.astrology.services.HapticManager
import com.destinyai.astrology.services.SoundManager
import com.destinyai.astrology.ui.theme.adaptiveContentWidth
import com.destinyai.astrology.ui.theme.AppType
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.NavyVariant
import com.destinyai.astrology.ui.theme.Spacing
import com.destinyai.astrology.ui.theme.TouchMin
import com.destinyai.astrology.ui.auth.PremiumFieldButton
import com.destinyai.astrology.ui.components.PremiumInputField
import com.destinyai.astrology.ui.components.ShimmerButton
import com.destinyai.astrology.ui.components.DatePickerSheetStyled
import com.destinyai.astrology.ui.components.TimePickerSheetStyled
import com.destinyai.astrology.data.remote.PartnerDto
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PartnersScreen(
    onBack: () -> Unit,
    onUpgrade: () -> Unit = {},
    // iOS parity (ProfileView.swift:339-347 + QuotaExhaustedView.onSignIn):
    // defense-in-depth route for guests. ProfileScreen already gates entry to
    // this screen behind GuestSignInPromptSheet, but if any other entry point
    // lands a guest here we must redirect to AuthScreen — never to home.
    onNavigateToAuth: () -> Unit = {},
    viewModel: PartnersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = remember { HapticManager(context) }
    var partnerToDelete by remember { mutableStateOf<PartnerDto?>(null) }
    // iOS parity (PartnerManagerView.swift:90-92): present SubscriptionView as
    // a sheet from this screen instead of relying on host nav (Gap 4).
    var showSubscriptionSheet by remember { mutableStateOf(false) }

    // Local SnackbarHost — surfaces successEvent (Add/Edit/Delete) the way iOS
    // emits SoundManager.playSuccess + HapticManager. Android needs a visible
    // toast in addition to the audio/haptic cue (Audit gap #1, #2a, #2b).
    val snackbarHostState = remember { SnackbarHostState() }
    val addedTemplate = stringResource(R.string.birth_chart_added_toast)
    val updatedTemplate = stringResource(R.string.birth_chart_updated_toast)
    val deletedTemplate = stringResource(R.string.birth_chart_deleted_toast)
    LaunchedEffect(state.successEvent) {
        val event = state.successEvent ?: return@LaunchedEffect
        val message = when (event) {
            is PartnerSuccessEvent.Added -> addedTemplate.format(event.partnerName)
            is PartnerSuccessEvent.Updated -> updatedTemplate.format(event.partnerName)
            is PartnerSuccessEvent.Deleted -> deletedTemplate.format(event.partnerName)
        }
        snackbarHostState.showSnackbar(message)
        viewModel.consumeSuccessEvent()
    }

    LaunchedEffect(Unit) { viewModel.loadPartners() }

    // iOS parity (ProfileView.swift:339-347): guests must never reach
    // PartnerManagerView. ProfileScreen already gates entry, but if a guest
    // somehow lands here we route to AuthScreen — NOT home — matching iOS
    // QuotaExhaustedView.onSignIn behavior.
    LaunchedEffect(state.isGuest) {
        if (state.isGuest) onNavigateToAuth()
    }

    // Styled wheel date/time pickers — same components the initial birth-data
    // screen uses (consistency), replacing the old system DatePickerDialog/
    // TimePickerDialog whose theme clashed and which sometimes failed to open.
    if (state.showDatePicker) {
        val cal = Calendar.getInstance()
        val dobParts = state.formDob.split("-").mapNotNull { it.toIntOrNull() }
        DatePickerSheetStyled(
            initialYear = dobParts.getOrNull(0) ?: (cal.get(Calendar.YEAR) - 25),
            initialMonth = dobParts.getOrNull(1)?.minus(1) ?: cal.get(Calendar.MONTH),
            initialDay = dobParts.getOrNull(2) ?: cal.get(Calendar.DAY_OF_MONTH),
            onDateSelected = { y, m, d ->
                viewModel.setFormDob("%04d-%02d-%02d".format(y, m + 1, d))
                viewModel.setShowDatePicker(false)
            },
            onDismiss = { viewModel.setShowDatePicker(false) },
        )
    }
    if (state.showTimePicker) {
        val timeParts = state.formTime.split(":").mapNotNull { it.toIntOrNull() }
        TimePickerSheetStyled(
            initialHour = timeParts.getOrNull(0) ?: 12,
            initialMinute = timeParts.getOrNull(1) ?: 0,
            is24Hour = android.text.format.DateFormat.is24HourFormat(context),
            onTimeSelected = { h, min ->
                viewModel.setFormTime("%02d:%02d".format(h, min))
                viewModel.setShowTimePicker(false)
            },
            onDismiss = { viewModel.setShowTimePicker(false) },
        )
    }

    CosmicBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("partners_screen")
                .semantics(mergeDescendants = false) { contentDescription = "partners_screen" },
        ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = CreamDim,
                    )
                }
                Text(
                    text = stringResource(R.string.saved_birth_charts_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CanelaFontFamily,
                    color = CreamText,
                    modifier = Modifier.weight(1f),
                )
                // Add button (gold pill)
                Button(
                    onClick = {
                        if (state.showAddForm) viewModel.toggleAddForm()
                        else viewModel.requestAddPartner()
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Gold,
                        contentColor = Color(0xFF0D0D1A),
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .heightIn(min = TouchMin)
                        .testTag("add_partner_button")
                        .semantics { contentDescription = "add_partner_button" },
                ) {
                    Text(
                        stringResource(R.string.partner_form_add_button),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }

            // Add/Edit form is rendered as a ModalBottomSheet at the end of this
            // composable to mirror iOS .sheet(isPresented:) presentation. See PartnerFormSheet.

            if (state.isLoading) {
                // iOS parity (PartnerManagerView.swift loading state): label beneath the spinner.
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .testTag("partners_loading_indicator"),
                    ) {
                        CircularProgressIndicator(color = Gold, modifier = Modifier.size(28.dp))
                        Text(
                            text = stringResource(R.string.loading_birth_charts),
                            color = CreamDim,
                            fontSize = AppType.secondary,
                            lineHeight = AppType.secondaryLh,
                        )
                    }
                }
            } else if (state.partners.isEmpty()) {
                // iOS parity (PartnerManagerView.swift:189-223): rich empty state
                // with icon, headline, description, and primary gold-gradient CTA.
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.padding(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.People,
                            contentDescription = stringResource(R.string.partner_avatar_cd),
                            tint = Gold,
                            modifier = Modifier.size(80.dp),
                        )
                        Text(
                            text = stringResource(R.string.no_saved_birth_charts),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = CanelaFontFamily,
                            color = CreamText,
                        )
                        Text(
                            text = stringResource(R.string.save_birth_charts_desc),
                            color = CreamDim,
                            fontSize = 16.sp,
                        )
                        Button(
                            onClick = {
                                haptic.medium()
                                viewModel.requestAddPartner()
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Gold,
                                contentColor = Color(0xFF0D0D1A),
                            ),
                            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp),
                            modifier = Modifier
                                .background(
                                    brush = Brush.linearGradient(listOf(Gold, Color(0xFFF5D060))),
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .testTag("partners_empty_add_cta"),
                        ) {
                            Text(
                                stringResource(R.string.add_birth_chart_action),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                            )
                        }
                    }
                }
            } else {
                // Mirrors iOS PartnerManagerView.swift:254-257 .refreshable.
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        contentPadding = WindowInsets.navigationBars.add(
                            WindowInsets(left = 16.dp, right = 16.dp, top = 8.dp, bottom = 8.dp),
                        ).asPaddingValues(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .adaptiveContentWidth(),
                    ) {
                    items(state.partners, key = { it.id }) { partner ->
                        // iOS parity (PartnerManagerView.swift interpolatingSpring on insertion):
                        // animate inter-row placement when items reorder/insert/remove.
                        Row(
                            modifier = Modifier
                                .animateItemPlacement()
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(NavySurface)
                                .border(0.5.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                // iOS parity (PartnerManagerView.swift:386-399): tap whole card to edit.
                                // Protected profiles show the protection alert instead.
                                .clickable {
                                    if (partner.isProtected) viewModel.showProtectionAlert(partner)
                                    else viewModel.beginEditPartner(partner)
                                }
                                .semantics { contentDescription = "partner_list_item" }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(Gold, Color(0xFFF5D060)))),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = partner.name.firstOrNull()?.uppercase() ?: "?",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0D0D1A),
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = partner.name,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CreamText,
                                    )
                                    // iOS parity (PartnerManagerView.swift:278-293): show
                                    // primary / active / used badges next to the name.
                                    ProtectionBadge(partner = partner)
                                }
                                // iOS parity (PartnerManagerView.swift:320-342):
                                // gender symbol (gold gradient) + gender label + DOB + city,
                                // joined with " · " separators.
                                PartnerMetaRow(partner = partner)
                            }
                            // iOS parity (PartnerManagerView.swift:347-377): single ellipsis
                            // overflow menu instead of inline Edit/Delete pencil + trash. For
                            // protected profiles the overflow shows a single 'Why can't edit?'
                            // info entry that opens the protection alert (PartnerManagerView.swift:357-370).
                            var showOverflow by remember(partner.id) { mutableStateOf(false) }
                            Box {
                                IconButton(
                                    onClick = {
                                        haptic.light()
                                        showOverflow = true
                                    },
                                    modifier = Modifier.testTag("partner_more_${partner.id}"),
                                ) {
                                    Icon(
                                        Icons.Filled.MoreVert,
                                        contentDescription = stringResource(R.string.partner_more_options_cd),
                                        tint = Gold,
                                    )
                                }
                                DropdownMenu(
                                    expanded = showOverflow,
                                    onDismissRequest = { showOverflow = false },
                                ) {
                                    if (!partner.isProtected) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.partner_edit_action)) },
                                            leadingIcon = {
                                                Icon(Icons.Filled.Edit, contentDescription = null, tint = Gold)
                                            },
                                            onClick = {
                                                haptic.light()
                                                showOverflow = false
                                                viewModel.beginEditPartner(partner)
                                            },
                                            modifier = Modifier.testTag("partner_overflow_edit_${partner.id}"),
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.delete)) },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Filled.Delete,
                                                    contentDescription = null,
                                                    tint = Color(0xFFFF5252),
                                                )
                                            },
                                            onClick = {
                                                haptic.light()
                                                showOverflow = false
                                                partnerToDelete = partner
                                            },
                                            modifier = Modifier.testTag("partner_overflow_delete_${partner.id}"),
                                        )
                                    } else {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.why_cant_edit)) },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Filled.HelpOutline,
                                                    contentDescription = null,
                                                    tint = Gold,
                                                )
                                            },
                                            onClick = {
                                                haptic.light()
                                                showOverflow = false
                                                viewModel.showProtectionAlert(partner)
                                            },
                                            modifier = Modifier.testTag("partner_overflow_why_${partner.id}"),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }
        }
        // SnackbarHost overlay — sibling to the Column inside the Box wrapper.
        // Sits above the Column so the toast renders above list content / form
        // sheets (sheet has its own composition scope, but the host is rooted
        // here, surviving the sheet dismissal that triggers the success event).
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
        }
    }

    // iOS parity (PartnerManagerView.swift:357-370, 388-404): protection alert explaining
    // why a primary/active/used profile cannot be edited.
    state.showProtectionAlertFor?.let { protectedPartner ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissProtectionAlert() },
            containerColor = NavySurface,
            title = {
                Text(
                    text = stringResource(R.string.profile_protected_title),
                    color = CreamText,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                val msgRes = when {
                    protectedPartner.isSelf -> R.string.profile_edit_blocked_main_user
                    protectedPartner.isActive -> R.string.profile_edit_blocked_active
                    else -> R.string.profile_edit_blocked_used
                }
                Text(text = stringResource(msgRes), color = CreamDim)
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissProtectionAlert() }) {
                    Text(stringResource(R.string.ok), color = Gold, fontWeight = FontWeight.Bold)
                }
            },
        )
    }

    // iOS parity (PartnerManagerView.swift:108-125): upgrade dialog when at maintain_profile limit.
    if (state.showQuotaUpgradePrompt) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissQuotaUpgradePrompt() },
            containerColor = NavySurface,
            title = {
                Text(
                    text = stringResource(R.string.partner_quota_upgrade_title),
                    color = CreamText,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                // iOS parity (PartnerManagerView.swift:121): interpolate
                // result.limit into the message ("You can save up to N
                // birth charts...") for Core users at limit. Free users
                // (limit == 0) keep the generic message (Gaps 3, 6).
                val limit = state.quotaLimit
                val msg = if (limit > 0) {
                    stringResource(R.string.partner_quota_upgrade_message_with_limit, limit)
                } else {
                    stringResource(R.string.partner_quota_upgrade_message)
                }
                Text(text = msg, color = CreamDim)
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissQuotaUpgradePrompt()
                    // iOS parity (PartnerManagerView.swift:94-97): present
                    // SubscriptionView in-screen instead of delegating up (Gap 4).
                    showSubscriptionSheet = true
                }) {
                    Text(
                        stringResource(R.string.partner_quota_upgrade_action),
                        color = Gold,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissQuotaUpgradePrompt() }) {
                    Text(stringResource(R.string.cancel), color = CreamDim)
                }
            },
        )
    }

    // iOS parity (PartnerManagerView.swift:78-82): list-level errors must
    // surface to the user. Bind a top-level dialog to viewModel.error so
    // delete/refresh/load failures are visible outside the form (Gaps 2, 7).
    state.error?.takeIf { !state.showAddForm }?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            containerColor = NavySurface,
            title = {
                Text(
                    text = stringResource(R.string.error),
                    color = CreamText,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = { Text(text = msg, color = CreamDim) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text(
                        stringResource(R.string.ok),
                        color = Gold,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
        )
    }

    // iOS parity (PartnerManagerView.swift:90-92): SubscriptionView sheet.
    if (showSubscriptionSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSubscriptionSheet = false },
            containerColor = NavySurface,
        ) {
            com.destinyai.astrology.ui.subscription.SubscriptionScreen(
                onBack = { showSubscriptionSheet = false },
            )
        }
    }

    // iOS parity (PartnerFormView.swift:276-283 LocationSearchView): debounced city lookup with results list.
    if (state.showLocationSearch) {
        LocationSearchSheet(
            results = state.locationResults,
            isSearching = state.isSearchingLocation,
            onQueryChange = { viewModel.searchLocation(it) },
            onSelect = { viewModel.selectLocation(it) },
            onDismiss = { viewModel.setShowLocationSearch(false) },
        )
    }

    // iOS parity (PartnerManagerView.swift:61-77): confirmationDialog with destructive action.
    partnerToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { partnerToDelete = null },
            containerColor = NavySurface,
            title = {
                Text(
                    text = stringResource(R.string.partner_delete_confirm_title),
                    color = CreamText,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.partner_delete_confirm_message, target.name),
                    color = CreamDim,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePartner(target.id)
                    partnerToDelete = null
                }) {
                    Text(
                        stringResource(R.string.delete),
                        color = Color(0xFFFF5252),
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { partnerToDelete = null }) {
                    Text(stringResource(R.string.cancel), color = CreamDim)
                }
            },
        )
    }

    // iOS parity (PartnerManagerView.swift:39-60): Add/Edit form is presented as a
    // modal sheet, not inline above the list. Avoids cramming the list and matches
    // the iOS .sheet(isPresented:) presentation style.
    if (state.showAddForm) {
        PartnerFormSheet(
            state = state,
            viewModel = viewModel,
            onDismiss = { viewModel.toggleAddForm() },
        )
    }
}

@Composable
private fun PickerRow(
    label: String,
    value: String,
    isPlaceholder: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NavySurface)
            .border(0.5.dp, Gold.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = CreamDim, fontSize = 12.sp)
            Text(
                text = value,
                color = if (isPlaceholder) CreamDim.copy(alpha = 0.6f) else CreamText,
                fontSize = AppType.secondary,
                lineHeight = AppType.secondaryLh,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun GuardianConsentRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(0.5.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = Gold,
                uncheckedColor = CreamDim,
            ),
        )
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(
                text = stringResource(R.string.partner_form_guardian_consent_title),
                color = CreamText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.partner_form_guardian_consent_message),
                color = CreamDim,
                fontSize = 12.sp,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationSearchSheet(
    results: List<LocationResult>,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onSelect: (LocationResult) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    // Consistency with the initial birth-data screen's city search: auto-focus +
    // keyboard on open, centered "Select City" title with a Cancel action, and the
    // gold drag handle. (Type-ahead search was already shared behaviour.)
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
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
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.select_city_title),
                    color = Gold,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .testTag("location_search_cancel"),
                ) {
                    Text(
                        text = stringResource(R.string.cancel_action),
                        color = CreamText,
                        fontSize = 15.sp,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    onQueryChange(it)
                },
                placeholder = {
                    Text(stringResource(R.string.partner_form_search_city), color = CreamDim)
                },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Gold) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
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
            if (isSearching) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = Gold, modifier = Modifier.size(22.dp)) }
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 320.dp),
            ) {
                items(results, key = { it.displayName }) { result ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(NavyVariant)
                            .clickable { onSelect(result) }
                            .padding(horizontal = Spacing.md, vertical = Spacing.md),
                    ) {
                        Text(
                            text = result.displayName,
                            color = CreamText,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun GenderSelector(
    selected: String,
    onSelect: (String) -> Unit,
) {
    val options = listOf(
        "male" to R.string.gender_male,
        "female" to R.string.gender_female,
        "non-binary" to R.string.gender_non_binary,
        "prefer_not_to_say" to R.string.gender_prefer_not_say,
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.partner_form_gender_label),
            color = CreamDim,
            fontSize = 13.sp,
        )
        // iOS parity (PartnerFormView.swift:120-128, 304-316): iOS uses a dedicated
        // PremiumSelectionSheet. On Android we keep the chips inline but wrap with
        // FlowRow so 4 options reflow on narrow phones instead of overflowing.
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("partner_form_gender_selector"),
        ) {
            options.forEach { (value, labelRes) ->
                val isSelected = selected == value
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(value) },
                    label = {
                        Text(
                            stringResource(labelRes),
                            fontSize = 12.sp,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Gold,
                        selectedLabelColor = Color(0xFF0D0D1A),
                        containerColor = NavyVariant,
                        labelColor = CreamDim,
                    ),
                    modifier = Modifier.testTag("partner_form_gender_chip_$value"),
                )
            }
        }
    }
}

@Composable
private fun PartnerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = CreamDim) },
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier),
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Gold,
            unfocusedBorderColor = Gold.copy(alpha = 0.25f),
            focusedTextColor = CreamText,
            unfocusedTextColor = CreamText,
            cursorColor = Gold,
            focusedLabelColor = Gold,
            unfocusedContainerColor = NavySurface,
            focusedContainerColor = NavySurface,
        ),
    )
}

/**
 * Mirrors iOS PartnerManagerView.swift:278-293 protectionBadge ViewBuilder —
 * shows Primary (green check), Active (orange star), or Used (blue clock) inline
 * next to the partner name.
 */
@Composable
private fun ProtectionBadge(partner: PartnerDto) {
    when {
        partner.isSelf -> BadgeChip(
            label = stringResource(R.string.primary_badge),
            tint = Color(0xFF48BB78),
            icon = Icons.Filled.CheckCircle,
        )
        partner.isActive -> BadgeChip(
            label = stringResource(R.string.active_badge),
            tint = Color(0xFFED8936),
            icon = Icons.Filled.Star,
        )
        partner.firstSwitchedAt != null -> BadgeChip(
            label = stringResource(R.string.used_badge),
            tint = Color(0xFF63B3ED),
            icon = Icons.Filled.AccessTime,
        )
    }
}

@Composable
private fun BadgeChip(
    label: String,
    tint: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.14f))
            .border(0.5.dp, tint.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(horizontal = Spacing.xs, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(11.dp),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text = label,
            color = tint,
            fontSize = AppType.caption,
            lineHeight = AppType.captionLh,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Mirrors iOS PartnerFormView.swift presented via .sheet(isPresented:) — a
 * ModalBottomSheet wrapper around the same fields used by the inline form
 * previously. Matches iOS modal presentation pattern (Gap 5).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PartnerFormSheet(
    state: PartnersUiState,
    viewModel: PartnersViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = remember { HapticManager(context) }
    // iOS parity (PartnerFormView.swift): SoundManager.playSuccess + Shimmer haptic on save.
    val soundManager = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            PartnerSoundEntryPoint::class.java,
        ).soundManager()
    }
    // iOS parity (PartnerFormView.swift:27, 116, 126,137,150,245): @FocusState for the
    // name field — auto-focus on open and dismiss on row taps.
    val nameFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        runCatching { nameFocusRequester.requestFocus() }
    }
    // iOS parity (PartnerFormView.swift:127, 304-316): gender row opens a
    // PremiumSelectionSheet (ModalBottomSheet on Android), not inline chips.
    var showGenderSheet by remember { mutableStateOf(false) }
    // Track previous saving state to detect a successful save transition.
    val wasSaving = remember { mutableStateOf(false) }
    LaunchedEffect(state.isSaving, state.showAddForm, state.error) {
        if (wasSaving.value && !state.isSaving && !state.showAddForm && state.error == null) {
            // iOS parity (PartnerFormView.swift:361 + ShimmerButton): success cue on save.
            soundManager.playSuccess()
            haptic.playShimmer()
        }
        wasSaving.value = state.isSaving
    }
    // iOS parity (PartnerFormView.swift sheet presentation): iOS `.sheet`
    // defaults to a full-height card with no partial detent, and the keyboard
    // pushes the content up via SwiftUI's automatic safe-area handling. On
    // Android, ModalBottomSheet's default partial-expanded detent makes the
    // form half-height so the keyboard covers fields. Force the sheet to
    // skip the partial detent (full-height only) and pad for the IME so
    // the Name/Date/Time fields stay visible while typing.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NavySurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Without this the tall form (Name→…→Place of Birth→Save) is clipped:
                // fields below the fold, and the Save button, are unreachable once the
                // keyboard is up. Make the sheet body scrollable.
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // iOS parity (PartnerFormView.swift:267-275): top-left Cancel always
            // available regardless of form footer button.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        haptic.light()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("partner_form_cancel_top"),
                ) {
                    Text(
                        text = stringResource(R.string.cancel_action),
                        color = CreamDim,
                        fontSize = 14.sp,
                    )
                }
                Spacer(Modifier.weight(1f))
            }
            Text(
                text = stringResource(
                    if (state.editingPartnerId != null) {
                        R.string.partner_form_edit_title
                    } else {
                        R.string.partner_form_add_title
                    },
                ),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Gold,
            )
            // Consistency: reuse the exact field components from the initial
            // "Create your birth chart" screen (BirthDataScreen) — PremiumInputField,
            // PremiumFieldButton, Date/Time side-by-side, styled wheel pickers.
            PremiumInputField(
                value = state.formName,
                onValueChange = viewModel::setFormName,
                label = stringResource(R.string.partner_form_name_label),
                placeholder = stringResource(R.string.enter_your_name),
                icon = Icons.Filled.Person,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    keyboardType = KeyboardType.Text,
                ),
                modifier = Modifier
                    .focusRequester(nameFocusRequester)
                    .testTag("partner_form_name_field"),
            )
            val genderDisplay = when (state.formGender) {
                "male" -> stringResource(R.string.gender_male)
                "female" -> stringResource(R.string.gender_female)
                "non-binary" -> stringResource(R.string.gender_non_binary)
                "prefer_not_to_say" -> stringResource(R.string.gender_prefer_not_say)
                else -> stringResource(R.string.select_gender)
            }
            PremiumFieldButton(
                icon = Icons.Filled.Person,
                text = genderDisplay,
                isPlaceholder = state.formGender.isBlank(),
                contentDescription = "Gender identity",
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("partner_form_gender_selector"),
                onClick = {
                    haptic.light()
                    keyboardController?.hide()
                    showGenderSheet = true
                },
            )
            // Date + Time side by side (mirrors BirthDataScreen)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PremiumFieldButton(
                    icon = Icons.Filled.CalendarMonth,
                    text = state.formDob.ifBlank { stringResource(R.string.partner_form_select_date) },
                    isPlaceholder = state.formDob.isBlank(),
                    contentDescription = "Date of birth",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("partner_form_dob_field"),
                    onClick = {
                        keyboardController?.hide()
                        viewModel.setShowDatePicker(true)
                    },
                )
                PremiumFieldButton(
                    icon = Icons.Filled.Schedule,
                    text = if (state.formBirthTimeUnknown) stringResource(R.string.birth_time_unknown)
                    else state.formTime.ifBlank { stringResource(R.string.partner_form_select_time) },
                    isPlaceholder = state.formTime.isBlank() && !state.formBirthTimeUnknown,
                    enabled = !state.formBirthTimeUnknown,
                    contentDescription = "Time of birth",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("partner_form_time_field"),
                    onClick = {
                        if (!state.formBirthTimeUnknown) {
                            keyboardController?.hide()
                            viewModel.setShowTimePicker(true)
                        }
                    },
                )
            }
            // Birth-time-unknown toggle — icon-checkbox style matching BirthDataScreen.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = TouchMin)
                    .clickable {
                        haptic.light()
                        viewModel.setFormBirthTimeUnknown(!state.formBirthTimeUnknown)
                    }
                    .testTag("partner_form_birth_time_unknown_checkbox"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (state.formBirthTimeUnknown) Icons.Filled.CheckBox
                    else Icons.Filled.CheckBoxOutlineBlank,
                    contentDescription = null,
                    tint = if (state.formBirthTimeUnknown) Gold else CreamDim,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.partner_form_birth_time_unknown_toggle),
                    color = CreamDim,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
            }
            if (state.formBirthTimeUnknown) {
                Text(
                    text = stringResource(R.string.birth_time_warning),
                    color = Color(0xFFFFB74D),
                    fontSize = AppType.caption,
                    lineHeight = AppType.captionLh,
                    modifier = Modifier
                        .padding(start = 28.dp)
                        .testTag("partner_form_birth_time_warning"),
                )
            }
            PremiumFieldButton(
                icon = Icons.Filled.LocationOn,
                text = state.formCity.ifBlank { stringResource(R.string.partner_form_search_city) },
                isPlaceholder = state.formCity.isBlank(),
                contentDescription = "Place of birth",
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("partner_form_city_field"),
                onClick = {
                    keyboardController?.hide()
                    viewModel.setShowLocationSearch(true)
                },
            )
            if (state.isUnder13) {
                GuardianConsentRow(
                    checked = state.formGuardianConsentGiven,
                    onCheckedChange = viewModel::setFormGuardianConsent,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // iOS parity (PartnerFormView.swift:209-228): checkbox-style toggle, not switch.
                Checkbox(
                    checked = state.formForCompatibility && !state.isUnder18,
                    onCheckedChange = { checked ->
                        if (!state.isUnder18) {
                            haptic.light()
                            viewModel.setFormForCompatibility(checked)
                        }
                    },
                    enabled = !state.isUnder18,
                    colors = CheckboxDefaults.colors(
                        checkedColor = Gold,
                        uncheckedColor = CreamDim,
                        disabledCheckedColor = CreamDim.copy(alpha = 0.4f),
                        disabledUncheckedColor = CreamDim.copy(alpha = 0.4f),
                    ),
                    modifier = Modifier.testTag("partner_form_for_compatibility_checkbox"),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.partner_form_for_compatibility),
                        color = if (state.isUnder18) CreamDim.copy(alpha = 0.5f) else CreamDim,
                        fontSize = 14.sp,
                    )
                    if (state.isUnder18) {
                        Text(
                            text = stringResource(R.string.partner_form_minor_no_compat),
                            color = Color(0xFFFFB74D),
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            if (state.error != null) {
                Text(
                    text = state.error,
                    color = Color(0xFFFF8A80),
                    fontSize = 13.sp,
                )
            }
            // Single primary action, same ShimmerButton as the initial birth-data
            // screen. Cancel lives once, in the top bar (a footer Cancel duplicated it).
            ShimmerButton(
                text = if (state.isSaving) "…" else stringResource(R.string.partner_form_save_button),
                onClick = {
                    haptic.light()
                    viewModel.addPartner()
                },
                enabled = state.isFormValid && !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("partner_form_save")
                    .testTag("partner_form_save_button"),
            )
        }
    }

    // iOS parity (PartnerFormView.swift:304-316): gender selection presented as a
    // modal sheet, not inline chips.
    if (showGenderSheet) {
        GenderModalSheet(
            selected = state.formGender,
            onSelect = { value ->
                haptic.light()
                viewModel.setFormGender(value)
            },
            onDismiss = { showGenderSheet = false },
        )
    }
}

/**
 * iOS parity (PartnerManagerView.swift:320-342) — meta line with gender symbol
 * (gold gradient), gender label, formatted DOB, and city joined by " · ".
 */
@Composable
private fun PartnerMetaRow(partner: PartnerDto) {
    val symbol = when (partner.gender) {
        "male" -> stringResource(R.string.partner_gender_symbol_male)
        "female" -> stringResource(R.string.partner_gender_symbol_female)
        else -> ""
    }
    val genderLabelRes = when (partner.gender) {
        "male" -> R.string.gender_male
        "female" -> R.string.gender_female
        "non-binary" -> R.string.gender_non_binary
        "prefer_not_to_say" -> R.string.gender_prefer_not_say
        else -> null
    }
    val genderLabel = genderLabelRes?.let { stringResource(it) }
    val dob = formatPartnerDob(partner.dateOfBirth)
    val city = partner.cityOfBirth?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (symbol.isNotEmpty()) {
            Text(
                text = symbol,
                fontSize = AppType.secondary,
                lineHeight = AppType.secondaryLh,
                style = androidx.compose.ui.text.TextStyle(
                    brush = Brush.linearGradient(listOf(Gold, Color(0xFFF5D060))),
                ),
                fontWeight = FontWeight.Bold,
            )
        }
        if (genderLabel != null) {
            Text(
                text = genderLabel,
                color = CreamDim,
                fontSize = AppType.secondary,
                lineHeight = AppType.secondaryLh,
            )
        }
        if (!dob.isNullOrEmpty()) {
            Text(
                text = "·",
                color = CreamDim,
                fontSize = AppType.secondary,
                lineHeight = AppType.secondaryLh,
            )
            Text(
                text = dob,
                color = CreamDim,
                fontSize = AppType.secondary,
                lineHeight = AppType.secondaryLh,
            )
        }
        if (city != null) {
            Text(
                text = "·",
                color = CreamDim,
                fontSize = AppType.secondary,
                lineHeight = AppType.secondaryLh,
            )
            Text(
                text = city,
                color = CreamDim,
                fontSize = AppType.secondary,
                lineHeight = AppType.secondaryLh,
                maxLines = 1,
            )
        }
    }
}

/**
 * Mirrors iOS PartnerProfile.formattedDateOfBirth — parses "yyyy-MM-dd" and
 * renders in the user's locale long date format. Falls back to raw input.
 */
private fun formatPartnerDob(raw: String?): String? {
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

/**
 * Mirrors iOS ShimmerButton — animated gold gradient that subtly drifts
 * across the button surface to imply premium polish.
 */
@Composable
private fun shimmeringGoldBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmer_offset",
    )
    val mid = androidx.compose.ui.graphics.lerp(Gold, Color(0xFFF5D060), offset)
    return Brush.linearGradient(
        colors = listOf(Gold, mid, Color(0xFFF5D060)),
    )
}

/**
 * iOS parity (PartnerFormView.swift:120-128): tappable gender selection row.
 * Replaces the previous inline FilterChip set with a row that opens a modal sheet,
 * matching iOS PremiumSelectionRow visual + interaction model.
 */
@Composable
private fun GenderSelectionRow(
    selected: String,
    onClick: () -> Unit,
) {
    val labelRes = when (selected) {
        "male" -> R.string.gender_male
        "female" -> R.string.gender_female
        "non-binary" -> R.string.gender_non_binary
        "prefer_not_to_say" -> R.string.gender_prefer_not_say
        else -> null
    }
    val display = labelRes?.let { stringResource(it) }
        ?: stringResource(R.string.select_gender)
    PickerRow(
        label = stringResource(R.string.partner_form_gender_label),
        value = display,
        isPlaceholder = labelRes == null,
        onClick = onClick,
    )
}

/**
 * iOS parity (PartnerFormView.swift:304-316): bottom-sheet gender picker that
 * mirrors PremiumSelectionSheet — title, four options with check on the active
 * choice, light haptic per selection, dismiss on tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenderModalSheet(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        "male" to R.string.gender_male,
        "female" to R.string.gender_female,
        "non-binary" to R.string.gender_non_binary,
        "prefer_not_to_say" to R.string.gender_prefer_not_say,
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NavySurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .testTag("partner_form_gender_sheet"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.partner_form_gender_label),
                color = Gold,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                fontFamily = CanelaFontFamily,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            options.forEach { (value, labelRes) ->
                val isSelected = value == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) Gold.copy(alpha = 0.12f) else NavyVariant)
                        .border(
                            0.5.dp,
                            if (isSelected) Gold.copy(alpha = 0.5f) else Gold.copy(alpha = 0.2f),
                            RoundedCornerShape(10.dp),
                        )
                        .clickable {
                            onSelect(value)
                            onDismiss()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("partner_form_gender_option_$value"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(labelRes),
                        color = CreamText,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Hilt EntryPoint that exposes the application-scoped SoundManager to the
 * PartnerFormSheet composable. Mirrors the HomeScreen.kt HomeSoundEntryPoint
 * pattern so we can play success cues without forcing SoundManager into the
 * ViewModel constructor.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PartnerSoundEntryPoint {
    fun soundManager(): SoundManager
}
