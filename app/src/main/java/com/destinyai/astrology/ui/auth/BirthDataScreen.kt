package com.destinyai.astrology.ui.auth

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.R
import com.destinyai.astrology.services.HapticManager
import com.destinyai.astrology.ui.components.GoldGradientText
import com.destinyai.astrology.ui.components.ShimmerButton
import com.destinyai.astrology.ui.onboarding.ResponseStyleOnboardingScreen
import com.destinyai.astrology.ui.theme.BirthDataDimens
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavyInput
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.NavyVariant
import com.destinyai.astrology.ui.theme.TextTertiary
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthDataScreen(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: BirthDataViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = remember { HapticManager(context) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // System back gesture / hardware back: only allow back-navigation when the
    // user is a guest (iOS parity — BirthDataView.swift:88-109 only renders the
    // chevron for guests, and registered users have no way to leave this screen
    // until save completes).
    BackHandler(enabled = state.isGuest) { onBack() }

    // iOS parity (BirthDataView.swift:144-146): fade the entire form in over
    // 0.4s on first composition.
    val contentAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        contentAlpha.animateTo(1f, animationSpec = tween(durationMillis = 400))
    }

    LaunchedEffect(Unit) { viewModel.loadSaved() }
    LaunchedEffect(state.isSaved) { if (state.isSaved) onSaved() }

    // R2-A9: suppress user-cancel errors. iOS parity (GuestSignInPromptView.swift:313-319):
    // matches ASAuthorizationError 1000/1001 by NSError code; we additionally accept
    // those numeric tokens for the Apple Web/CustomTabs path where only a message is available.
    LaunchedEffect(state.error) {
        val err = state.error ?: return@LaunchedEffect
        if (err.contains("cancelled", ignoreCase = true) ||
            err.contains("canceled", ignoreCase = true) ||
            err.contains("user_cancel", ignoreCase = true) ||
            err.contains("1000") ||
            err.contains("1001")
        ) {
            viewModel.clearError()
        }
    }

    // Date picker dialog
    val calendar = Calendar.getInstance()
    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            R.style.DestinyDatePickerTheme,
            { _, year, month, day ->
                viewModel.setDateOfBirth("%04d-%02d-%02d".format(year, month + 1, day))
            },
            calendar.get(Calendar.YEAR) - 30,
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
        ).apply {
            datePicker.maxDate = System.currentTimeMillis()
            // iOS parity (BirthDataView.swift:148-158 sheet onDismiss): mark
            // date as selected on ANY dismissal (OK, cancel, back) and play a
            // light haptic. The OnDateSetListener already updates the date
            // value when the user confirms; this listener handles the "selected
            // semantic" so the UI shows the chosen date even after a cancel.
            setOnDismissListener {
                viewModel.markDateSelected()
                haptic.light()
            }
        }
    }

    // Time picker dialog
    val timePickerDialog = remember {
        TimePickerDialog(
            context,
            R.style.DestinyDatePickerTheme,
            { _, hour, minute ->
                viewModel.setTimeOfBirth("%02d:%02d".format(hour, minute))
            },
            12, 0, true,
        ).apply {
            // iOS parity (BirthDataView.swift:159-169 sheet onDismiss): mark
            // time as selected on ANY dismissal + light haptic.
            setOnDismissListener {
                viewModel.markTimeSelected()
                haptic.light()
            }
        }
    }

    // Gender bottom sheet
    var showGenderSheet by remember { mutableStateOf(false) }

    // Location search dialog
    var showLocationSearch by remember { mutableStateOf(false) }

    CosmicBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = contentAlpha.value }
                // iOS parity (BirthDataView.swift:132-134 .onTapGesture): tap
                // anywhere outside the name field to dismiss the soft keyboard.
                .pointerInput(Unit) {
                    detectTapGestures {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
                },
        ) {

            // ── Top bar ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // iOS parity (BirthDataView.swift:88-109): back chevron only
                // visible to guest users. Registered users on a fresh sign-up
                // flow must not be able to escape this screen.
                if (state.isGuest) {
                    IconButton(
                        onClick = {
                            haptic.light()
                            onBack()
                        },
                        modifier = Modifier
                            .testTag("birth_data_back")
                            .semantics { contentDescription = "Back" },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = Gold,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                // R2-A4: Sound toggle
                IconToggleButton(
                    checked = state.isSoundEnabled,
                    onCheckedChange = {
                        // iOS parity (BirthDataView.swift:115-117): light haptic
                        // before toggling so the user feels the tap.
                        haptic.light()
                        viewModel.toggleSound()
                    },
                    modifier = Modifier.testTag("birth_data_sound_toggle"),
                ) {
                    Icon(
                        imageVector = if (state.isSoundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = if (state.isSoundEnabled) stringResource(R.string.sound_on_a11y) else stringResource(R.string.sound_off_a11y),
                        tint = Gold.copy(alpha = 0.8f),
                    )
                }
            }

            // R2-A6: Refreshed banner — iOS parity (BirthDataView.swift:140-143)
            // banner has no tap-to-dismiss; it auto-clears the persisted
            // `birthDataRefreshedOnServer` flag when the view appears, so the
            // user sees it once for the session. We mirror that here by NOT
            // wiring a click handler. The flag is cleared by viewModel.loadSaved()
            // on next appearance via prefs.setBackendDataRefreshed(false).
            AnimatedVisibility(
                visible = state.showRefreshedBanner,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFB8860B).copy(alpha = 0.15f))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .testTag("birth_data_refreshed_banner"),
                ) {
                    Text(
                        text = stringResource(R.string.backend_data_refreshed_banner),
                        color = Gold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // ── Scrollable content ────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {

                // ── Header (matches iOS BirthDataView.headerSection) ──────────
                BirthDataHeader()

                Spacer(Modifier.height(28.dp))

                // ── Name field ────────────────────────────────────────────────
                val nameFocus = remember { FocusRequester() }
                PremiumInputField(
                    value = state.userName,
                    onValueChange = viewModel::setUserName,
                    label = stringResource(R.string.your_name),
                    placeholder = stringResource(R.string.enter_your_name),
                    icon = Icons.Default.Person,
                    contentDescription = "Your Name field",
                )

                Spacer(Modifier.height(12.dp))

                // ── Date / Time row ───────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Date button
                    PremiumFieldButton(
                        icon = Icons.Default.CalendarMonth,
                        text = if (state.isDateSelected) state.dateOfBirth
                        else stringResource(R.string.select_date),
                        isPlaceholder = !state.isDateSelected,
                        contentDescription = "Date of birth",
                        modifier = Modifier.weight(1f),
                        onClick = { datePickerDialog.show() },
                    )
                    // Time button
                    PremiumFieldButton(
                        icon = Icons.Default.Schedule,
                        text = if (state.timeUnknown) stringResource(R.string.birth_time_unknown)
                        else if (state.isTimeSelected) state.timeOfBirth
                        else stringResource(R.string.select_time),
                        isPlaceholder = !state.isTimeSelected && !state.timeUnknown,
                        enabled = !state.timeUnknown,
                        contentDescription = "Time of birth",
                        modifier = Modifier.weight(1f),
                        onClick = { if (!state.timeUnknown) timePickerDialog.show() },
                    )
                }

                // Age warning
                AnimatedVisibility(visible = state.dateOfBirth.isNotBlank() && isUnder13(state.dateOfBirth)) {
                    Text(
                        text = stringResource(R.string.age_minimum_required),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, start = 4.dp),
                    )
                }

                Spacer(Modifier.height(8.dp))

                // ── Time unknown toggle ───────────────────────────────────────
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // iOS parity (BirthDataView.swift:369-374):
                                // light haptic on toggle of timeUnknown.
                                haptic.light()
                                viewModel.setTimeUnknown(!state.timeUnknown)
                            }
                            .testTag("birth_data_time_unknown_row")
                            .semantics { contentDescription = "I don't know my birth time" },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (state.timeUnknown)
                                Icons.Filled.CheckBox
                            else
                                Icons.Filled.CheckBoxOutlineBlank,
                            contentDescription = null,
                            tint = if (state.timeUnknown) Gold else TextTertiary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.i_dont_know_birth_time),
                            fontSize = 14.sp,
                            color = CreamDim,
                        )
                    }

                    AnimatedVisibility(
                        visible = state.timeUnknown,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Text(
                            text = stringResource(R.string.birth_time_warning),
                            fontSize = 12.sp,
                            color = Color(0xFFE6A817), // iOS .warning
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, start = 28.dp),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── Location row ──────────────────────────────────────────────
                PremiumFieldButton(
                    icon = Icons.Default.LocationOn,
                    text = if (state.cityOfBirth.isNotBlank()) state.cityOfBirth
                    else stringResource(R.string.select_birth_city),
                    isPlaceholder = state.cityOfBirth.isBlank(),
                    contentDescription = "Place of birth",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showLocationSearch = true },
                )

                Spacer(Modifier.height(12.dp))

                // ── Gender row ────────────────────────────────────────────────
                val genderLabel = when (state.gender) {
                    "male" -> stringResource(R.string.gender_male)
                    "female" -> stringResource(R.string.gender_female)
                    "non-binary" -> stringResource(R.string.gender_non_binary)
                    "prefer_not_to_say" -> stringResource(R.string.gender_prefer_not_say)
                    else -> stringResource(R.string.select_gender)
                }
                PremiumFieldButton(
                    icon = Icons.Default.Person,
                    text = genderLabel,
                    isPlaceholder = state.gender.isBlank(),
                    contentDescription = "Gender identity",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showGenderSheet = true },
                )

                // Error message
                if (state.error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = state.error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(28.dp))

                // R2-A5: Analytics consent checkbox — Android product decision diverges
                // from iOS by design: always show the consent toggle regardless of
                // locale. iOS still gates on non-US (BirthDataView.swift:33,426); on
                // Android we render the Row + trailing Spacer unconditionally so the
                // checkbox is visible to every user.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // iOS parity (BirthDataView.swift:427-431): light
                            // haptic on toggle of analytics consent.
                            haptic.light()
                            viewModel.setAnalyticsConsent(!state.analyticsConsent)
                        }
                        .testTag("birth_data_analytics_row")
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Checkbox(
                        checked = state.analyticsConsent,
                        onCheckedChange = { viewModel.setAnalyticsConsent(it) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Gold,
                            uncheckedColor = CreamDim,
                            checkmarkColor = Color(0xFF0D0D1A),
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.analytics_consent_label),
                            fontSize = 14.sp,
                            color = CreamText,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = stringResource(R.string.analytics_consent_description),
                            fontSize = 12.sp,
                            color = CreamDim,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // R2-A8: ShimmerButton replaces solid gold Button
                ShimmerButton(
                    text = if (state.isLoading) "…" else stringResource(R.string.action_continue),
                    onClick = {
                        // iOS parity (BirthDataView.swift:451-454):
                        // premiumContinue haptic + button-tap sound before save.
                        haptic.premiumContinue()
                        viewModel.save()
                    },
                    enabled = viewModel.isValid && !state.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("birth_data_continue")
                        .semantics { contentDescription = "Continue" },
                )

                Spacer(Modifier.height(40.dp))
            }
        }

        // Loading overlay
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NavySurface),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(color = Gold, strokeWidth = 2.dp)
                        Spacer(Modifier.height(12.dp))
                        Text(text = stringResource(R.string.saving_profile), color = CreamText, fontSize = 14.sp)
                    }
                }
            }
        }
    }

    // ── Gender bottom sheet ────────────────────────────────────────────────────
    if (showGenderSheet) {
        GenderSelectionSheet(
            currentGender = state.gender,
            onSelect = { gender ->
                viewModel.setGender(gender)
                showGenderSheet = false
            },
            onDismiss = { showGenderSheet = false },
        )
    }

    // ── Location search sheet ──────────────────────────────────────────────────
    if (showLocationSearch) {
        LocationSearchSheet(
            results = state.locationResults,
            isSearching = state.isSearchingLocation,
            errorRes = state.locationErrorRes,
            onQueryChange = { viewModel.searchLocation(it) },
            onSelect = { city, lat, lng ->
                viewModel.setLocation(city, lat, lng)
                viewModel.clearLocationResults()
                showLocationSearch = false
            },
            onDismiss = {
                viewModel.clearLocationResults()
                showLocationSearch = false
            },
        )
    }

    // ── Response style onboarding sheet (shown on first save) ─────────────────
    if (state.showResponseStyleSheet) {
        // iOS parity (BirthDataView.swift:191-205 fullScreenCover): the picker
        // must NOT be dismissable until the user explicitly continues — iOS uses
        // a non-dismissable fullScreenCover. On Android we mirror this by:
        //   1. Marking the sheet skipPartiallyExpanded = true so swipe-down does
        //      not collapse to a smaller state.
        //   2. Rejecting Hidden via confirmValueChange so swipe / scrim tap
        //      cannot dismiss the sheet (only onContinue / onBack inside the
        //      response-style screen can).
        //   3. Intercepting the system back press while the sheet is up.
        val sheetState = androidx.compose.material3.rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { value ->
                value != androidx.compose.material3.SheetValue.Hidden
            },
        )
        BackHandler(enabled = true) { /* swallow — must Continue inside picker */ }
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = { /* no-op — see comment above */ },
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            // Hide the drag handle since dragging is disabled.
            dragHandle = null,
        ) {
            ResponseStyleOnboardingScreen(
                isSettingsMode = false,
                onContinue = { viewModel.dismissResponseStyle(); onSaved() },
                onBack = { viewModel.dismissResponseStyle(); onSaved() },
            )
        }
    }

    // ── 409/403 conflict — show GuestSignInPromptScreen as a full-screen overlay ──
    // iOS parity (BirthDataView.swift:223-238): when birth data matches a registered
    // account, present GuestSignInPromptView with provider-filtered buttons and a
    // human-readable message that names the matching provider.
    if (state.birthDataTakenEmail != null || state.birthDataTakenProvider != null) {
        val provider = state.birthDataTakenProvider
        val maskedEmail = state.birthDataTakenEmail
        val message = when (provider?.lowercase()) {
            "apple" -> stringResource(R.string.birth_data_linked_apple)
            "google" -> if (!maskedEmail.isNullOrBlank()) {
                stringResource(R.string.birth_data_linked_google_email, maskedEmail)
            } else {
                stringResource(R.string.birth_data_linked_google)
            }
            else -> stringResource(
                R.string.birth_data_linked_email,
                maskedEmail ?: stringResource(R.string.sign_in_required),
            )
        }
        GuestSignInPromptScreen(
            message = message,
            provider = provider,
            onSignIn = {
                viewModel.clearBirthDataConflict()
                onBack()
            },
            onBack = {
                viewModel.clearBirthDataConflict()
            },
        )
    }
}

// ── Header ─────────────────────────────────────────────────────────────────────

@Composable
private fun BirthDataHeader() {
    val infinite = rememberInfiniteTransition(label = "header-glow")
    val pulse by infinite.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Pulsing gold icon (person.crop.circle.badge.plus equivalent)
        Box(
            modifier = Modifier.size(BirthDataDimens.headerGlowSize),
            contentAlignment = Alignment.Center,
        ) {
            // Outer glow
            Canvas(modifier = Modifier.size(BirthDataDimens.headerGlowSize)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Gold.copy(alpha = 0.3f * pulse), Color.Transparent),
                        center = center,
                        radius = size.width / 2f,
                    ),
                    radius = size.width / 2f,
                    center = center,
                )
            }
            // Circle container
            Box(
                modifier = Modifier
                    .size(BirthDataDimens.headerIconSize)
                    .clip(CircleShape)
                    .background(NavyInput)
                    .border(1.dp, Gold.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(BirthDataDimens.headerIconSize * 0.47f),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.create_birth_chart),
            fontFamily = CanelaFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = BirthDataDimens.headerTitleSize,
            color = CreamText,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.enter_details_desc),
            fontSize = BirthDataDimens.headerSubtitleSize,
            color = CreamDim,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
        )
    }
}

// ── Premium input field (name) ──────────────────────────────────────────────────

@Composable
private fun PremiumInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = CreamDim) },
        placeholder = { Text(placeholder, color = TextTertiary) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Gold.copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp),
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.contentDescription = contentDescription },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Gold,
            unfocusedBorderColor = Gold.copy(alpha = 0.25f),
            focusedTextColor = CreamText,
            unfocusedTextColor = CreamText,
            cursorColor = Gold,
            focusedLabelColor = Gold,
            unfocusedContainerColor = NavyInput,
            focusedContainerColor = NavyInput,
        ),
    )
}

// ── Premium field button (date / time / location / gender) ─────────────────────

@Composable
private fun PremiumFieldButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    isPlaceholder: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) NavyInput else NavyInput.copy(alpha = 0.5f))
            .border(
                1.dp,
                Gold.copy(alpha = if (enabled) 0.3f else 0.1f),
                RoundedCornerShape(12.dp),
            )
            .then(
                if (enabled) Modifier.clickable(onClick = onClick)
                else Modifier
            )
            .padding(horizontal = 12.dp)
            .semantics { this.contentDescription = contentDescription },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) Gold.copy(alpha = 0.8f) else TextTertiary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            color = if (isPlaceholder || !enabled) TextTertiary else CreamText,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    }
}

// ── Gender bottom sheet ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenderSelectionSheet(
    currentGender: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        "male" to stringResource(R.string.gender_male),
        "female" to stringResource(R.string.gender_female),
        "non-binary" to stringResource(R.string.gender_non_binary),
        "prefer_not_to_say" to stringResource(R.string.gender_prefer_not_say),
    )

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
            GoldGradientText(
                text = stringResource(R.string.gender_identity),
                fontSize = 20.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            options.forEach { (value, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (currentGender == value) NavyVariant else Color.Transparent
                        )
                        .border(
                            1.dp,
                            if (currentGender == value) Gold.copy(alpha = 0.5f)
                            else Gold.copy(alpha = 0.15f),
                            RoundedCornerShape(10.dp),
                        )
                        .clickable { onSelect(value) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        fontSize = 16.sp,
                        fontWeight = if (currentGender == value) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (currentGender == value) Gold else CreamText,
                        modifier = Modifier.weight(1f),
                    )
                    if (currentGender == value) {
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
    }
}

// ── Location search sheet ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationSearchSheet(
    results: List<com.destinyai.astrology.data.remote.LocationResult>,
    isSearching: Boolean,
    errorRes: Int?,
    onQueryChange: (String) -> Unit,
    onSelect: (city: String, lat: Double, lng: Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }

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
            GoldGradientText(
                text = stringResource(R.string.place_of_birth),
                fontSize = 20.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it; onQueryChange(it) },
                placeholder = { Text(stringResource(R.string.select_birth_city), color = TextTertiary) },
                leadingIcon = {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Gold.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Gold,
                    unfocusedBorderColor = Gold.copy(alpha = 0.25f),
                    focusedTextColor = CreamText,
                    unfocusedTextColor = CreamText,
                    cursorColor = Gold,
                    unfocusedContainerColor = NavyInput,
                    focusedContainerColor = NavyInput,
                ),
                singleLine = true,
            )

            Spacer(Modifier.height(8.dp))

            when {
                isSearching -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = Gold,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
                // GAP-2: surface backend/network failures so the user does not see
                // a silently empty list when the request hit a 401/404/timeout.
                errorRes != null -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(errorRes),
                            fontSize = 14.sp,
                            color = TextTertiary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                // Genuine "no matches" state — distinct from a backend failure.
                query.length >= 2 && results.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.location_search_no_results),
                            fontSize = 14.sp,
                            color = TextTertiary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        results.forEach { result ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelect(result.displayName, result.latitude, result.longitude)
                                    }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = Gold.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(text = result.displayName, fontSize = 15.sp, color = CreamText)
                            }
                            HorizontalDivider(color = Gold.copy(alpha = 0.08f))
                        }
                    }
                }
            }
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────

private fun isUnder13(dob: String): Boolean {
    if (dob.isBlank()) return false
    return try {
        val parts = dob.split("-")
        val dobYear = parts[0].toInt()
        val dobMonth = parts[1].toInt()
        val dobDay = parts[2].toInt()
        val today = Calendar.getInstance()
        var age = today.get(Calendar.YEAR) - dobYear
        val monthDiff = today.get(Calendar.MONTH) + 1 - dobMonth
        if (monthDiff < 0 || (monthDiff == 0 && today.get(Calendar.DAY_OF_MONTH) < dobDay)) age--
        age < 13
    } catch (_: Exception) {
        false
    }
}
