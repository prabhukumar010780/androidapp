package com.destinyai.astrology.ui.compatibility

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import com.destinyai.astrology.R
import com.destinyai.astrology.domain.model.AnalysisStep
import com.destinyai.astrology.domain.model.PartnerData
import com.destinyai.astrology.ui.charts.ChartComparisonSheet
import com.destinyai.astrology.ui.subscription.SubscriptionScreen
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.NavyVariant
import com.destinyai.astrology.ui.theme.SuccessGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun CompatibilityScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onNavigateToPartners: () -> Unit,
    onNavigateToHistory: (() -> Unit)? = null,
    onNavigateToSettings: (() -> Unit)? = null,
    // iOS parity (CompatibilityView.swift signOutAndReauth + QuotaExhaustedView.onSignIn):
    // when a guest hits the quota wall on Compatibility, the Sign In CTA must route to
    // AuthScreen — not just dismiss the dialog (which strands them on the Match tab).
    onNavigateToAuth: () -> Unit = {},
    onShowResultChange: ((Boolean) -> Unit)? = null,
    initialMatchItem: com.destinyai.astrology.domain.model.CompatibilityHistoryItem? = null,
    initialMatchGroup: com.destinyai.astrology.domain.model.ComparisonGroup? = null,
    viewModel: CompatibilityViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val partners by viewModel.partners.collectAsStateWithLifecycle()
    val activePartnerIndex by viewModel.activePartnerIndex.collectAsStateWithLifecycle()
    val hasFailedPartners by viewModel.hasFailedPartners.collectAsStateWithLifecycle()
    val comparisonResults by viewModel.comparisonResults.collectAsStateWithLifecycle()
    val currentStep by viewModel.currentStep.collectAsStateWithLifecycle()
    val historyLoadedToast by viewModel.historyLoadedToast.collectAsStateWithLifecycle()
    // Mirrors iOS CompatibilityView resultView swap: when a CompatibilityResult lands on the
    // VM, we replace the form entirely with CompatibilityResultScreen.
    val resultObj by viewModel.compatibilityResult.collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current

    // Auto-dismiss the "Loaded from history" toast after 2s. Mirrors iOS animation
    // behavior driven by viewModel.historyLoadedToast (CompatibilityView.swift:190).
    LaunchedEffect(historyLoadedToast) {
        if (historyLoadedToast) {
            delay(2000)
            viewModel.dismissHistoryLoadedToast()
        }
    }

    // History sheet — mirrors iOS CompatibilityView .sheet(isPresented: $showHistorySheet)
    var showHistorySheet by remember { mutableStateOf(false) }
    // Chart comparison sheet — mirrors iOS .sheet(isPresented: $showChartsSheet)
    var showChartsSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadUserData() }

    // Mirrors iOS CompatibilityView(initialMatchItem:, initialMatchGroup:) — when the
    // host hands us a saved match (deep-link from Home match-history), hydrate the
    // form / overview without re-running an LLM analysis.
    LaunchedEffect(initialMatchItem, initialMatchGroup) {
        if (initialMatchGroup != null) {
            viewModel.loadFromGroup(initialMatchGroup)
        } else if (initialMatchItem != null) {
            viewModel.loadFromHistory(initialMatchItem)
        }
    }

    // Mirrors iOS MainTabView showMatchResult — propagate result/overview/streaming
    // visibility to the parent so the tab bar can hide while a result is showing.
    val isResultShowing = state.showComparisonOverview ||
        state.showStreamingView ||
        resultObj != null ||
        state.result.isNotEmpty()
    LaunchedEffect(isResultShowing) {
        onShowResultChange?.invoke(isResultShowing)
    }
    DisposableEffect(Unit) {
        onDispose { onShowResultChange?.invoke(false) }
    }

    val today = remember {
        val c = Calendar.getInstance()
        "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    // Date picker
    val calendar = Calendar.getInstance()
    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, day ->
                viewModel.setPartnerDob("%04d-%02d-%02d".format(year, month + 1, day))
                viewModel.setShowDatePicker(false)
            },
            calendar.get(Calendar.YEAR) - 25,
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
        ).apply { datePicker.maxDate = System.currentTimeMillis() }
    }

    // Time picker
    val timePickerDialog = remember {
        TimePickerDialog(
            context,
            { _, hour, minute ->
                viewModel.setPartnerTime("%02d:%02d".format(hour, minute))
                viewModel.setShowTimePicker(false)
            },
            12, 0, true,
        )
    }

    LaunchedEffect(state.showDatePicker) { if (state.showDatePicker) datePickerDialog.show() }
    LaunchedEffect(state.showTimePicker) { if (state.showTimePicker) timePickerDialog.show() }

    // Partner-field edit watcher — mirrors iOS .onChange(of: viewModel.girl*) blocks.
    // Each field gets its own guarded onChange so a programmatic multi-field write
    // from selectSavedPartner() / loadFromHistory() does not race-fire the edit
    // hook before the VM has flipped partnerFromSaved=true. Guard: skip while the
    // VM is in the middle of loading from a saved entry.
    LaunchedEffect(state.partnerName) {
        if (!state.isLoadingFromSaved && state.partnerFromSaved) viewModel.markPartnerEdited()
    }
    LaunchedEffect(state.partnerDob) {
        if (!state.isLoadingFromSaved && state.partnerFromSaved) viewModel.markPartnerEdited()
    }
    LaunchedEffect(state.partnerTime) {
        if (!state.isLoadingFromSaved && state.partnerFromSaved) viewModel.markPartnerEdited()
    }
    LaunchedEffect(state.partnerCity) {
        if (!state.isLoadingFromSaved && state.partnerFromSaved) viewModel.markPartnerEdited()
    }
    LaunchedEffect(state.partnerGender) {
        if (!state.isLoadingFromSaved && state.partnerFromSaved) viewModel.markPartnerEdited()
    }
    LaunchedEffect(state.partnerTimeUnknown) {
        if (!state.isLoadingFromSaved && state.partnerFromSaved) viewModel.markPartnerEdited()
    }

    // Gender bottom sheet
    var showGenderSheet by remember { mutableStateOf(false) }

    // Streaming overlay — show over form while analyzing
    if (state.showStreamingView) {
        if (partners.size > 1) {
            MultiPartnerStreamingView(
                isVisible = true,
                partners = partners,
                completedResults = comparisonResults,
                currentPartnerIndex = activePartnerIndex,
                currentStep = currentStep,
                totalPartners = partners.count { it.isComplete },
            )
            return
        } else {
            CompatibilityStreamingView(
                isVisible = true,
                currentStep = currentStep,
                streamingText = "",
            )
            return
        }
    }

    // Comparison overview after multi-partner analysis
    if (state.showComparisonOverview) {
        val youLabel = stringResource(R.string.compat_you_label)
        ComparisonOverviewView(
            results = comparisonResults,
            userName = state.personAName.ifEmpty { youLabel },
            onSelectPartner = { idx ->
                viewModel.selectComparisonResult(idx)
                viewModel.setShowComparisonOverview(false)
            },
            onBack = { viewModel.setShowComparisonOverview(false) },
            onNewMatch = {
                viewModel.clearResult()
                viewModel.setShowComparisonOverview(false)
            },
        )
        return
    }

    // Single Partner result swap — mirrors iOS CompatibilityView else-if branch:
    // when the VM has a CompatibilityResult, render the full result screen instead
    // of the input form. Without this gate, users only saw the inline summary card.
    val currentResult = resultObj
    if (currentResult != null) {
        CompatibilityResultScreen(
            result = currentResult,
            onBack = { viewModel.clearResult() },
            onNewAnalysis = {
                viewModel.clearResult()
                viewModel.resetPartnerForm()
            },
            isFromComparison = comparisonResults.size > 1,
            onCharts = { showChartsSheet = true },
            onOpenSettings = onNavigateToSettings,
            modifier = modifier,
        )
        if (showChartsSheet) {
            ChartComparisonSheet(
                boyName = currentResult.boyName,
                girlName = currentResult.girlName,
                boyChartData = currentResult.boyChartData,
                girlChartData = currentResult.girlChartData,
                boyAscendant = currentResult.boyAscendant,
                girlAscendant = currentResult.girlAscendant,
                onDismiss = { showChartsSheet = false },
            )
        }
        return
    }

    CosmicBackground(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .semantics(mergeDescendants = true) { contentDescription = "compat_screen" }) {
            CompatibilityHeader(
                onNavigateToPartners = onNavigateToPartners,
                onHistory = {
                    viewModel.loadHistory()
                    showHistorySheet = true
                },
                onReset = { viewModel.resetPartnerForm() },
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(Modifier.height(8.dp))

                // Hero header — mirrors iOS CompatibilityView VStack(spacing: 12) with
                // PulsingGlowView + 64x64 stroked Circle + match_icon + Ashtakoot Analysis title.
                HeroPulsingMatchIcon()

                if (!state.personALoaded) {
                    BirthDataWarningCard()
                } else {
                    YouCard(
                        name = state.personAName,
                        summary = formattedUserSummary(
                            name = state.personAName,
                            gender = state.personAGender,
                            dob = state.personADob,
                            time = state.personATime,
                            city = state.personACity,
                            timeUnknown = state.personATimeUnknown,
                        ),
                    )

                    // Age block banner — shown prominently between user card and form
                    val bannerAgeMessage = ageBlockMessage(
                        userDob = state.personADob,
                        partnerDob = state.partnerDob,
                        today = today,
                    )
                    if (ageBlockBannerVisible(bannerAgeMessage)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFED8936).copy(alpha = 0.15f))
                                .border(1.dp, Color(0xFFED8936).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("⚠️", fontSize = 18.sp)
                            Text(
                                text = stringResource(R.string.compat_age_block_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFED8936),
                                lineHeight = 18.sp,
                            )
                        }
                    }

                    if (partners.size >= 1) {
                        val activePartner = partners.getOrNull(activePartnerIndex)
                        val activeIsComplete = activePartner?.isComplete == true ||
                            (state.partnerName.isNotBlank() &&
                                state.partnerDob.isNotBlank() &&
                                state.partnerCity.isNotBlank() &&
                                (state.partnerTime.isNotBlank() || state.partnerTimeUnknown))
                        PartnerTabStrip(
                            partners = partners,
                            activeIndex = activePartnerIndex,
                            isPlus = state.isPlus,
                            activeIsComplete = activeIsComplete,
                            onSelectPartner = viewModel::selectPartner,
                            onAddPartner = {
                                if (!state.isPlus) {
                                    viewModel.showPaywallSheet()
                                } else {
                                    viewModel.addPartner()
                                }
                            },
                            onRemovePartner = viewModel::removePartner,
                        )
                    }

                    SectionHeader(title = stringResource(R.string.compat_partner_details_lc))

                    // Name + Gender row (with saved partner picker icon)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CosmicTextField(
                            value = state.partnerName,
                            onValueChange = viewModel::setPartnerName,
                            label = stringResource(com.destinyai.astrology.R.string.compat_name),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { viewModel.showPartnerPicker() },
                            modifier = Modifier
                                .size(48.dp)
                                .border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                        ) {
                            Icon(
                                Icons.Filled.PersonAdd,
                                contentDescription = stringResource(com.destinyai.astrology.R.string.compat_load_saved_partner),
                                tint = Gold,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        // Gender picker button
                        PickerField(
                            icon = Icons.Filled.Person,
                            label = if (state.partnerGender.isEmpty()) stringResource(com.destinyai.astrology.R.string.compat_gender) else state.partnerGender.replaceFirstChar { it.uppercase() },
                            isPlaceholder = state.partnerGender.isEmpty(),
                            modifier = Modifier.width(130.dp),
                            onClick = { showGenderSheet = true },
                        )
                    }

                    // Date + Time row
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PickerField(
                            icon = Icons.Filled.CalendarMonth,
                            label = if (state.partnerDob.isEmpty()) stringResource(com.destinyai.astrology.R.string.compat_dob) else state.partnerDob,
                            isPlaceholder = state.partnerDob.isEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .semantics { contentDescription = "compat_person2_dob" },
                            onClick = { viewModel.setShowDatePicker(true) },
                        )
                        PickerField(
                            icon = Icons.Filled.Schedule,
                            label = when {
                                state.partnerTimeUnknown -> stringResource(R.string.compat_unknown_label)
                                state.partnerTime.isEmpty() -> stringResource(R.string.compat_time_of_birth)
                                else -> state.partnerTime
                            },
                            isPlaceholder = state.partnerTime.isEmpty() && !state.partnerTimeUnknown,
                            modifier = Modifier.width(130.dp),
                            onClick = {
                                if (!state.partnerTimeUnknown) viewModel.setShowTimePicker(true)
                            },
                            enabled = !state.partnerTimeUnknown,
                        )
                    }

                    // City / Location picker
                    PickerField(
                        icon = Icons.Filled.LocationOn,
                        label = if (state.partnerCity.isEmpty()) stringResource(com.destinyai.astrology.R.string.compat_city_of_birth) else state.partnerCity,
                        isPlaceholder = state.partnerCity.isEmpty(),
                        onClick = { viewModel.setShowLocationSearch(true) },
                    )

                    // Inline location search dialog
                    if (state.showLocationSearch) {
                        LocationSearchDialog(
                            onSearch = { query -> viewModel.searchLocation(query) },
                            onLocationSelected = { city, lat, lon ->
                                viewModel.setPartnerLocation(city, lat, lon)
                                viewModel.setShowLocationSearch(false)
                            },
                            onDismiss = { viewModel.setShowLocationSearch(false) },
                        )
                    }

                    // Time unknown + save row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                viewModel.setPartnerTimeUnknown(!state.partnerTimeUnknown)
                            },
                        ) {
                            Checkbox(
                                checked = state.partnerTimeUnknown,
                                onCheckedChange = { viewModel.setPartnerTimeUnknown(it) },
                                colors = CheckboxDefaults.colors(checkedColor = Gold),
                            )
                            Text(
                                text = stringResource(com.destinyai.astrology.R.string.compat_time_unknown),
                                style = MaterialTheme.typography.labelSmall,
                                color = CreamDim,
                            )
                        }
                    }

                    if (state.partnerTimeUnknown) {
                        Text(
                            text = stringResource(com.destinyai.astrology.R.string.compat_time_unknown_note),
                            style = MaterialTheme.typography.labelSmall,
                            fontStyle = FontStyle.Italic,
                            color = CreamDim,
                        )
                    }
                }

                if (state.error != null) {
                    Text(
                        text = state.error ?: "",
                        color = Color(0xFFFF8A80),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (hasFailedPartners) {
                    OutlinedButton(
                        onClick = viewModel::retryFailedPartners,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(26.dp),
                        border = BorderStroke(1.dp, Gold.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Gold),
                    ) {
                        Text(stringResource(com.destinyai.astrology.R.string.compat_retry_failed), fontWeight = FontWeight.SemiBold)
                    }
                }

                // R2-CM5: Save partner to birth charts checkbox
                // iOS parity (CompatibilityView.swift:919-938): show the checkbox in a
                // disabled (greyed-out) state when partner originated from a saved
                // entry, instead of hiding it entirely. Hidden only for free-plan users.
                if (state.isPlus) {
                    val savePartnerEnabled = !state.partnerFromSaved
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .semantics { contentDescription = "compat_save_partner_checkbox" }
                            .let { m ->
                                if (savePartnerEnabled) {
                                    m.clickable {
                                        viewModel.setSavePartnerToBirthCharts(!state.savePartnerToBirthCharts)
                                    }
                                } else {
                                    m
                                }
                            },
                    ) {
                        Checkbox(
                            checked = state.savePartnerToBirthCharts,
                            onCheckedChange = if (savePartnerEnabled) {
                                { viewModel.setSavePartnerToBirthCharts(it) }
                            } else null,
                            enabled = savePartnerEnabled,
                            colors = CheckboxDefaults.colors(checkedColor = Gold),
                        )
                        Text(
                            text = stringResource(com.destinyai.astrology.R.string.compat_save_partner_to_birth_charts),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (savePartnerEnabled) CreamDim else CreamDim.copy(alpha = 0.5f),
                        )
                    }
                }

                // R2-CM6: Duplicate alert dialog
                if (state.showDuplicateAlert) {
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissDuplicateAlert() },
                        containerColor = NavySurface,
                        title = {
                            Text(
                                stringResource(com.destinyai.astrology.R.string.compat_partner_already_saved),
                                style = MaterialTheme.typography.titleMedium,
                                color = CreamText,
                            )
                        },
                        text = {
                            Text(
                                stringResource(com.destinyai.astrology.R.string.compat_partner_already_saved_msg),
                                style = MaterialTheme.typography.bodyMedium,
                                color = CreamDim,
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { viewModel.dismissDuplicateAlert(); viewModel.analyze() }) {
                                Text(stringResource(com.destinyai.astrology.R.string.compat_save_anyway), color = Gold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { viewModel.dismissDuplicateAlert() }) {
                                Text(stringResource(com.destinyai.astrology.R.string.compat_use_saved), color = CreamDim)
                            }
                        },
                    )
                }

                // R2-CM7: ShimmerButton replaces plain Button
                val completedCount = partners.count { it.isComplete }
                val ageWarning = ageBlockMessage(
                    userDob = state.personADob,
                    partnerDob = state.partnerDob,
                    today = today,
                )
                if (ageWarning != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = stringResource(R.string.compat_age_block_warning),
                            style = MaterialTheme.typography.labelSmall,
                            color = CreamDim,
                        )
                    }
                }
                if (state.isAnalyzing) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Gold,
                            strokeWidth = 2.dp,
                        )
                    }
                } else {
                    ShimmerButton(
                        text = analyzeButtonTitleComposable(completedCount, isAnalyzing = false),
                        onClick = {
                            if (state.canAnalyze && ageWarning == null) {
                                val dupId = viewModel.checkForDuplicate()
                                if (dupId != null) {
                                    viewModel.showDuplicateAlert(dupId)
                                } else if (completedCount > 1) {
                                    viewModel.analyzeAllPartners()
                                } else {
                                    viewModel.analyze()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = analyzeButtonContentDescription() },
                        enabled = state.canAnalyze && ageWarning == null,
                    )
                }

                Spacer(Modifier.height(32.dp))
            }
        }

            // iOS parity (CompatibilityView.swift:190): animated "Loaded from history" toast
            // shown when a saved match is hydrated without an API call. Auto-dismisses after 2s.
            AnimatedVisibility(
                visible = historyLoadedToast,
                enter = fadeIn(animationSpec = tween(300)) +
                    slideInVertically(animationSpec = tween(300)) { -it },
                exit = fadeOut(animationSpec = tween(300)) +
                    slideOutVertically(animationSpec = tween(300)) { -it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp)
                    .semantics { contentDescription = "compat_history_loaded_toast" },
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(NavySurface)
                        .border(1.dp, SuccessGreen.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(com.destinyai.astrology.R.string.loaded_from_history),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = CreamText,
                    )
                }
            }
        }
    }

    // Gender selection sheet
    if (showGenderSheet) {
        GenderSelectionSheet(
            selected = state.partnerGender,
            onSelect = { gender ->
                viewModel.setPartnerGender(gender)
                showGenderSheet = false
            },
            onDismiss = { showGenderSheet = false },
        )
    }

    // Saved partner picker sheet
    if (state.showPartnerPicker) {
        // iOS parity (CompatibilityView.swift:218-227): exclude already-selected
        // partners and the active user profile, and only show forCompatibility-flagged
        // saved partners.
        val activeProfileIdState by viewModel.activeProfileId.collectAsStateWithLifecycle(initialValue = null)
        val excluded = remember(partners, activeProfileIdState) {
            buildSet<String> {
                addAll(partners.mapNotNull { it.savedProfileId }.filter { it.isNotBlank() })
                activeProfileIdState?.let { add(it) }
                // 'self' sentinel mirrors iOS shouldExcludeSelf gating
                add("self")
            }
        }
        PartnerPickerSheet(
            viewModel = viewModel,
            excludeIds = excluded,
            forCompatibilityOnly = true,
            onDismiss = { viewModel.dismissPartnerPicker() },
        )
    }

    // Quota-exhausted intermediate dialog — mirrors iOS .sheet(isPresented: $showQuotaExhausted)
    // QuotaExhaustedView. Shown for guest/free users when canAccessFeature returns false, with
    // distinct Sign In vs Upgrade CTAs. The downstream SubscriptionScreen still renders for
    // the underlying paywall flow.
    val quotaMarker = state.error
    val isQuotaMarker = quotaMarker == "FREE_LIMIT_GUEST" ||
        quotaMarker == "FREE_LIMIT_REGISTERED" ||
        quotaMarker == "FEATURE_UPGRADE_REQUIRED"
    if (isQuotaMarker && !state.showPaywall) {
        QuotaExhaustedDialog(
            isGuest = quotaMarker == "FREE_LIMIT_GUEST",
            customMessage = null,
            onSignIn = {
                // iOS parity (CompatibilityView.swift signOutAndReauth + QuotaExhaustedView.onSignIn):
                // dismiss the dialog and route to AuthScreen via the host. Without onNavigateToAuth
                // the user was previously stranded on the Compat tab with the dialog dismissed.
                viewModel.dismissError()
                viewModel.requestSignInFromQuota()
            },
            onUpgrade = {
                viewModel.dismissError()
                viewModel.showPaywallSheet()
            },
            onDismiss = { viewModel.dismissError() },
        )
    }

    // iOS parity (ChatScreen.kt:412-418 / ChatView.swift onSignIn): when the VM signals
    // navigateToAuth, invoke the host callback exactly once so the user lands on AuthScreen.
    androidx.compose.runtime.LaunchedEffect(state.navigateToAuth) {
        if (state.navigateToAuth) {
            onNavigateToAuth()
            viewModel.consumeNavigateToAuth()
        }
    }

    // iOS parity (CompatibilityView.swift:191-199): .sheet(isPresented: $showHistorySheet)
    // presents CompatibilityHistorySheet. Box(fillMaxSize) ensures the overlay sits on top
    // of CosmicBackground and blocks all touch events from the underlying input form.
    if (showHistorySheet) {
        Box(modifier = Modifier.fillMaxSize()) {
            CompatibilityHistoryScreen(
                viewModel = viewModel,
                onBack = { showHistorySheet = false },
                onItemSelect = { item ->
                    viewModel.loadFromHistory(item)
                    showHistorySheet = false
                },
                onGroupSelect = { group ->
                    viewModel.loadFromGroup(group)
                    showHistorySheet = false
                },
                onOpenSettings = onNavigateToSettings,
            )
        }
    }

    if (state.showPaywall) {
        SubscriptionScreen(
            onBack = { viewModel.dismissPaywall() },
        )
    }
}

@Composable
private fun CompatibilityHeader(
    onNavigateToPartners: () -> Unit,
    onHistory: () -> Unit,
    onReset: () -> Unit,
) {
    // iOS parity (Components/AppHeader.swift MatchInputHeader): circle-bordered history
    // button left, centered Destiny logo, circle-bordered reset button right.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .border(1.dp, Gold.copy(alpha = 0.3f), CircleShape)
                .clickable(onClick = onHistory)
                .semantics { contentDescription = "compat_history_button" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.History,
                contentDescription = null,
                tint = Gold,
                modifier = Modifier.size(20.dp),
            )
        }
        Image(
            painter = painterResource(id = R.drawable.destiny_home),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            modifier = Modifier
                .weight(1f)
                .height(32.dp)
                .padding(horizontal = 12.dp),
        )
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .border(1.dp, Gold.copy(alpha = 0.3f), CircleShape)
                .clickable(onClick = onReset)
                .semantics { contentDescription = "compat_reset_button" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = stringResource(R.string.compat_reset_form_a11y),
                tint = Gold,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun BirthDataWarningCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NavySurface)
            .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.compat_birth_data_warning),
            style = MaterialTheme.typography.bodyMedium,
            color = CreamDim,
        )
    }
}

@Composable
private fun YouCard(name: String, summary: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(NavySurface, NavyVariant)))
            .border(0.5.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "👤", fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.compat_you_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF718096),
                )
                val meFallback = stringResource(R.string.compat_me_fallback)
                Text(
                    text = name.ifEmpty { meFallback },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = CreamText,
                )
                if (summary.isNotEmpty()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = CreamDim,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = Gold.copy(alpha = 0.7f),
    )
}

@Composable
private fun CosmicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color(0xFF718096)) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
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

@Composable
private fun PickerField(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isPlaceholder: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .then(if (modifier == Modifier) Modifier.fillMaxWidth() else modifier)
            .clip(RoundedCornerShape(12.dp))
            .background(NavySurface.copy(alpha = if (enabled) 1f else 0.5f))
            .border(1.dp, Gold.copy(alpha = if (enabled) 0.25f else 0.1f), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) Gold.copy(alpha = 0.7f) else Gold.copy(alpha = 0.3f),
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isPlaceholder || !enabled) Color(0xFF718096) else CreamText,
                maxLines = 1,
            )
        }
    }
}

internal fun analyzeButtonContentDescription(): String = "compat_analyze_button"

@Composable
internal fun partnerTabLabelComposable(index: Int): String =
    stringResource(R.string.partner_index_label, index + 1)

internal fun partnerTabLabel(index: Int): String = "Partner ${index + 1}"

@Composable
internal fun analyzeButtonTitleComposable(completedCount: Int, isAnalyzing: Boolean): String = when {
    isAnalyzing -> stringResource(R.string.compat_analyzing)
    completedCount > 1 -> stringResource(R.string.compat_compare_all_format, completedCount)
    else -> stringResource(R.string.compat_check_compatibility)
}

internal fun analyzeButtonTitle(completedCount: Int, isAnalyzing: Boolean): String = when {
    isAnalyzing -> "Analyzing…"
    completedCount > 1 -> "Compare All ($completedCount)"
    else -> "Check Compatibility"
}

internal fun formattedUserSummary(
    name: String,
    gender: String,
    dob: String,
    time: String,
    city: String,
    timeUnknown: Boolean,
): String {
    val parts = buildList {
        if (name.isNotEmpty()) add(name)
        if (gender.isNotEmpty()) add(gender.replaceFirstChar { it.uppercase() })
        if (dob.isNotEmpty()) add(dob)
        if (!timeUnknown && time.isNotEmpty()) add(time)
        if (city.isNotEmpty()) add(city)
    }
    return parts.joinToString(" · ")
}

internal fun ageBlockMessage(userDob: String, partnerDob: String, today: String): String? {
    if (userDob.isEmpty() || partnerDob.isEmpty()) return null
    fun isMinor(dob: String): Boolean {
        return try {
            val parts = dob.split("-")
            val todayParts = today.split("-")
            val birthYear = parts[0].toInt()
            val birthMonth = parts[1].toInt()
            val birthDay = parts[2].toInt()
            val todayYear = todayParts[0].toInt()
            val todayMonth = todayParts[1].toInt()
            val todayDay = todayParts[2].toInt()
            val age = todayYear - birthYear -
                if (todayMonth < birthMonth || (todayMonth == birthMonth && todayDay < birthDay)) 1 else 0
            age <= 18
        } catch (_: Exception) { false }
    }
    return if (isMinor(userDob) || isMinor(partnerDob))
        "Destiny matching requires both individuals to be 18 or older"
        // ^ Hard-coded only as a sentinel for tests / non-Composable callers; UI sites read
        // the localized string from R.string.compat_age_block_warning instead (see usage).
    else null
}

@Composable
private fun PartnerTabStrip(
    partners: List<PartnerData>,
    activeIndex: Int,
    isPlus: Boolean,
    activeIsComplete: Boolean,
    onSelectPartner: (Int) -> Unit,
    onAddPartner: () -> Unit,
    onRemovePartner: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        partners.forEachIndexed { index, partner ->
            val isActive = index == activeIndex
            FilterChip(
                selected = isActive,
                onClick = { onSelectPartner(index) },
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = partnerTabLabelComposable(index),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        if (partner.isComplete) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(10.dp))
                        }
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Gold,
                    selectedLabelColor = Color(0xFF0D0D1A),
                    containerColor = Color.Transparent,
                    labelColor = Gold,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isActive,
                    borderColor = Gold.copy(alpha = 0.5f),
                    selectedBorderColor = Gold,
                ),
            )
        }

        // iOS parity (CompatibilityView.swift:677-715): Add gated by Plus + active partner complete
        if (partners.size < 3) {
            val canAddMore = isPlus && activeIsComplete
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .border(
                        1.dp,
                        Gold.copy(alpha = if (canAddMore) 0.4f else 0.2f),
                        RoundedCornerShape(8.dp),
                    )
                    .semantics { contentDescription = "compat_add_partner" },
                contentAlignment = Alignment.Center,
            ) {
                IconButton(
                    onClick = onAddPartner,
                    enabled = isPlus || !isPlus, // always clickable to surface paywall when not Plus
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Add partner",
                        tint = Gold.copy(alpha = if (canAddMore) 1f else 0.5f),
                        modifier = Modifier.size(16.dp),
                    )
                }
                if (!isPlus) {
                    // Crown badge mirrors iOS Plus indicator overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 6.dp, y = (-6).dp)
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Gold),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("👑", fontSize = 8.sp)
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        if (partners.size > 1) {
            IconButton(
                onClick = { onRemovePartner(activeIndex) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove partner", tint = Color(0xFFFC8181).copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun CompatibilityResultCard(score: Int?, result: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(NavySurface, NavyVariant)))
            .border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(20.dp),
    ) {
        Column {
            if (score != null) {
                Text(
                    text = compatibilityScoreLabel(score, 36),
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    color = Gold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Compatibility Score",
                    style = MaterialTheme.typography.labelMedium,
                    color = CreamDim,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
            }
            Text(
                text = result,
                style = MaterialTheme.typography.bodyMedium,
                color = CreamText,
                lineHeight = 22.sp,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenderSelectionSheet(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf("male" to "Male", "female" to "Female", "non-binary" to "Non-Binary", "prefer_not_to_say" to "Prefer not to say")
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = NavySurface) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Gender Identity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = CreamText)
            Spacer(Modifier.height(8.dp))
            options.forEach { (key, display) ->
                val isSelected = selected == key
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) Gold.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable { onSelect(key) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(display, style = MaterialTheme.typography.bodyMedium, color = if (isSelected) Gold else CreamText, modifier = Modifier.weight(1f))
                    if (isSelected) Icon(Icons.Filled.Check, contentDescription = null, tint = Gold, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// Simple city-name entry dialog — mirrors iOS LocationSearchView sheet
@Composable
private fun LocationSearchDialog(
    onSearch: suspend (String) -> Triple<String, Double, Double>?,
    onLocationSelected: (city: String, lat: Double, lon: Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var cityInput by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavySurface,
        title = {
            Text("City of Birth", style = MaterialTheme.typography.titleMedium, color = CreamText)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = cityInput,
                    onValueChange = {
                        cityInput = it
                        errorMessage = null
                    },
                    label = { Text("Enter city name", color = Color(0xFF718096)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !isSearching,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold,
                        unfocusedBorderColor = Gold.copy(alpha = 0.25f),
                        focusedTextColor = CreamText,
                        unfocusedTextColor = CreamText,
                        cursorColor = Gold,
                        unfocusedContainerColor = NavySurface,
                        focusedContainerColor = NavySurface,
                    ),
                )
                if (errorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFE57373),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val q = cityInput.trim()
                    if (q.isBlank() || isSearching) return@TextButton
                    isSearching = true
                    errorMessage = null
                    scope.launch {
                        val result = onSearch(q)
                        isSearching = false
                        if (result == null) {
                            errorMessage = "Location not found"
                            return@launch
                        }
                        val (name, lat, lon) = result
                        if (lat == 0.0 && lon == 0.0) {
                            errorMessage = "Location not found"
                            return@launch
                        }
                        onLocationSelected(name, lat, lon)
                    }
                },
                enabled = cityInput.isNotBlank() && !isSearching,
            ) {
                Text(if (isSearching) "Searching…" else "Select", color = Gold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSearching) {
                Text("Cancel", color = CreamDim)
            }
        },
    )
}

internal fun ageBlockBannerVisible(ageMessage: String?): Boolean =
    !ageMessage.isNullOrBlank()

/**
 * Pulsing gold ring + match-icon hero block. Mirrors iOS CompatibilityView VStack(spacing: 12)
 * with PulsingGlowView (gold opacity 0.3 size 80 blur 25) + 64dp Circle stroke + match_icon
 * 30dp + "Ashtakoot Analysis" title + description.
 */
@Composable
private fun HeroPulsingMatchIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "hero_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(animation = tween(1400)),
        label = "hero_pulse_scale",
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.36f,
        animationSpec = infiniteRepeatable(animation = tween(1400)),
        label = "hero_pulse_alpha",
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(80.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Outer pulsing gold halo (parity with iOS PulsingGlowView)
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = glowAlpha
                    }
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Gold.copy(alpha = 0.55f), Color.Transparent),
                        ),
                    ),
            )
            // Inner gold-stroked circle
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(NavySurface)
                    .border(1.dp, Gold.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.match_icon),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(com.destinyai.astrology.R.string.ashtakoot_analysis_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                color = CreamText,
            )
            Text(
                text = stringResource(com.destinyai.astrology.R.string.enter_details_desc_compatibility),
                fontSize = 13.sp,
                color = CreamDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

/**
 * Quota-exhausted dialog — mirrors iOS QuotaExhaustedView (Components/QuotaExhaustedView.swift).
 * Branches on isGuest:
 *   - GUEST  → "Sign up to continue!" + Sign-in CTA (no upgrade button — backend doesn't allow paid plans for guests)
 *   - PAID/FREE_REGISTERED → "You've reached your limit" + Upgrade CTA (Sign In hidden — they're already signed in)
 * Both branches get a "Maybe later" tertiary action.
 */
@Composable
fun QuotaExhaustedDialog(
    isGuest: Boolean,
    customMessage: String?,
    onSignIn: () -> Unit,
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit,
) {
    val titleRes = if (isGuest) {
        com.destinyai.astrology.R.string.sign_up_to_continue
    } else {
        com.destinyai.astrology.R.string.quota_exhausted_title
    }
    val bodyRes = if (isGuest) {
        com.destinyai.astrology.R.string.create_account_to_continue
    } else {
        com.destinyai.astrology.R.string.upgrade_to_keep_going
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavySurface,
        title = {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
                color = Gold,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text(
                text = customMessage?.takeIf { it.isNotBlank() }
                    ?: stringResource(bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                color = CreamDim,
            )
        },
        confirmButton = {
            // Primary CTA: guest → Sign In, account → Upgrade
            TextButton(onClick = if (isGuest) onSignIn else onUpgrade) {
                Text(
                    text = stringResource(
                        if (isGuest) {
                            com.destinyai.astrology.R.string.sign_in_button
                        } else {
                            com.destinyai.astrology.R.string.upgrade_action
                        },
                    ),
                    color = Gold,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            // Tertiary: "Maybe later" — mirrors iOS not_now CTA.
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(com.destinyai.astrology.R.string.not_now),
                    color = CreamDim,
                )
            }
        },
    )
}
