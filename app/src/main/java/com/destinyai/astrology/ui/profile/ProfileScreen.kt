package com.destinyai.astrology.ui.profile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
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
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.BuildConfig
import com.destinyai.astrology.services.HapticManager
import com.destinyai.astrology.R
import com.destinyai.astrology.ui.auth.AuthViewModel
import com.destinyai.astrology.ui.components.ShimmerButton
import com.destinyai.astrology.ui.settings.ChartStylePickerSheet
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.NavyVariant
import kotlinx.coroutines.launch

// Gold→GoldDeep→Gold 3-stop avatar gradient
private val GoldDeep = Color(0xFFA8862A)
private val AvatarGradient = Brush.linearGradient(listOf(Gold, GoldDeep, Gold))
private val AvatarInitialsColor = Color(0xFF0D0D1A)

/**
 * Which Plus-only feature triggered the guest sign-in prompt.
 * Mirrors iOS three flags: showGuestSignInForSwitch, showGuestSignInForAlerts, showGuestSignInSheet.
 * Title/message resolved via stringResource at compose time.
 */
private enum class GuestSignInFeature(
    val titleRes: Int,
    val messageRes: Int,
) {
    SWITCH(
        titleRes = R.string.profile_guest_signin_switch_title,
        messageRes = R.string.profile_guest_signin_switch_message,
    ),
    ALERTS(
        titleRes = R.string.profile_guest_signin_alerts_title,
        messageRes = R.string.profile_guest_signin_alerts_message,
    ),
    SUBSCRIPTION(
        titleRes = R.string.profile_guest_signin_subscription_title,
        messageRes = R.string.profile_guest_signin_subscription_message,
    ),
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSubscription: () -> Unit,
    onDeletedAccount: () -> Unit,
    onNavigateToLanguage: () -> Unit = {},
    onNavigateToResponseStyle: () -> Unit = {},
    onNavigateToNotificationPrefs: () -> Unit = {},
    onNavigateToCharts: () -> Unit = {},
    onNavigateToPartners: () -> Unit = {},
    onNavigateToFaq: () -> Unit = {},
    onNavigateToBirthDetails: () -> Unit = {},
    onNavigateToAstrologySettings: () -> Unit = {},
    onLaunchEmbeddedAuth: () -> Unit = {},
    onSignedOut: () -> Unit = onDeletedAccount,
    viewModel: ProfileViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    // Turn-off-history confirmation (mirrors iOS .alert("Turn off history?")
    // ProfileView.swift:210-222). Wrapping the off-direction in a dialog
    // prevents accidental data loss; the on-direction still flips immediately.
    var showTurnOffHistoryDialog by remember { mutableStateOf(false) }
    // Chart Style picker sheet (mirrors iOS .sheet(isPresented: $showChartStylePicker)).
    var showChartStyleSheet by remember { mutableStateOf(false) }
    // Language picker sheet — iOS parity (ProfileView.swift:142-144 opens
    // LanguageSettingsSheet inline, NOT the onboarding flow). Hosting it here
    // keeps the user on the Profile screen and reuses the same 13-language
    // sheet that SettingsScreen already shows.
    var showLanguageSheet by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    // Guest sign-in prompt sheets (parity with iOS ProfileView.swift:33-34)
    // iOS uses three separate flags; Android tracks which feature triggered the prompt.
    var guestSignInFeature by remember { mutableStateOf<GuestSignInFeature?>(null) }

    // Mirrors iOS UNUserNotificationCenter.requestAuthorization (ProfileView.swift:886-909).
    // On Android 13+ POST_NOTIFICATIONS is a runtime permission; pre-13 it was granted at install.
    // Tracks UI state so the badge flips immediately after the system dialog resolves.
    var notifPermissionGranted by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notifPermissionGranted = granted ||
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        // After grant, re-fetch + register the FCM token so the backend can deliver pushes.
        // FirebaseMessagingService.onNewToken will fire on next app launch — best-effort here.
        if (granted) {
            viewModel.refreshAll()
        }
    }

    // Re-check OS-level notification permission on resume so the inline toggle
    // reflects changes the user made in system Settings (parity with
    // NotificationPreferencesScreen.kt:152-161). Without this, after the user
    // taps the row → Settings opens → enables notifications → comes back, the
    // toggle stays Off because notifPermissionGranted is only written from the
    // permission-launcher callback, which never fires for Settings deep-links.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                notifPermissionGranted =
                    NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            viewModel.refreshAll()
        },
    )

    LaunchedEffect(Unit) { viewModel.loadProfile() }
    LaunchedEffect(state.isDeleted) { if (state.isDeleted) onDeletedAccount() }
    LaunchedEffect(state.isSignedOut) { if (state.isSignedOut) onSignedOut() }
    LaunchedEffect(state.isLoading) { if (!state.isLoading) isRefreshing = false }
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }
    // "Now viewing as <Name>" snackbar after a successful Switch Profile.
    // ProfileViewModel collects ProfileChangeBus and exposes the resolved
    // display name as a one-shot event; consume after rendering.
    val switchedToName by viewModel.profileSwitchedToName.collectAsStateWithLifecycle()
    val nowViewingTemplate = stringResource(R.string.now_viewing_as)
    LaunchedEffect(switchedToName) {
        switchedToName?.let { name ->
            snackbarHostState.showSnackbar(nowViewingTemplate.format(name))
            viewModel.consumeProfileSwitchedEvent()
        }
    }

    // ── Dialogs & sheets ─────────────────────────────────────────────────────
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text(stringResource(R.string.profile_clear_chat_history), color = CreamText, fontFamily = CanelaFontFamily) },
            text = { Text(stringResource(R.string.profile_clear_chat_history_message), color = CreamDim) },
            confirmButton = {
                TextButton(onClick = {
                    showClearHistoryDialog = false
                    viewModel.clearChatHistory()
                }) { Text(stringResource(R.string.clear_action), color = Color(0xFFFF5252), fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) { Text(stringResource(R.string.cancel), color = CreamDim) }
            },
            containerColor = NavySurface,
        )
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text(stringResource(R.string.profile_sign_out_question), color = CreamText, fontFamily = CanelaFontFamily) },
            text = { Text(stringResource(R.string.profile_sign_out_message_short), color = CreamDim) },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutDialog = false
                    viewModel.signOut()
                }) { Text(stringResource(R.string.sign_out), color = Color(0xFFFF5252), fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) { Text(stringResource(R.string.cancel), color = CreamDim) }
            },
            containerColor = NavySurface,
        )
    }

    // Turn-off-history confirmation — destructive action mirrors iOS
    // ProfileView.swift:210-222. Cancel keeps the toggle ON because the
    // toggle was never actually flipped — only the dialog opens.
    if (showTurnOffHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showTurnOffHistoryDialog = false },
            title = { Text(stringResource(R.string.profile_history_turn_off_title), color = CreamText, fontFamily = CanelaFontFamily) },
            text = { Text(stringResource(R.string.history_off_warning), color = CreamDim) },
            confirmButton = {
                TextButton(onClick = {
                    showTurnOffHistoryDialog = false
                    viewModel.toggleHistory(false)
                }) { Text(stringResource(R.string.turn_off_action), color = Color(0xFFFF5252), fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showTurnOffHistoryDialog = false }) { Text(stringResource(R.string.cancel), color = CreamDim) }
            },
            containerColor = NavySurface,
        )
    }

    // History-cleared success alert with thread count — mirrors iOS
    // .alert("history_cleared_title", isPresented: $showClearSuccessAlert)
    // (ProfileView.swift:239-243). Plurals select singular vs plural string.
    state.clearedThreadCount?.let { count ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissClearedThreadAlert() },
            title = { Text(stringResource(R.string.history_cleared_title), color = CreamText, fontFamily = CanelaFontFamily) },
            text = {
                val msgRes = if (count == 1) R.string.deleted_conversation_singular else R.string.deleted_conversation_plural
                Text(stringResource(msgRes, count), color = CreamDim)
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissClearedThreadAlert() }) {
                    Text(stringResource(R.string.ok_action), color = Gold, fontWeight = FontWeight.SemiBold)
                }
            },
            containerColor = NavySurface,
        )
    }

    // Chart Style picker sheet — mirrors iOS ChartStylePickerSheet at
    // ProfileView.swift:151-153. Selection persists via prefs.setChartStyle.
    if (showChartStyleSheet) {
        ChartStylePickerSheet(
            currentStyle = state.chartStyle,
            onSelect = { viewModel.setChartStyle(it) },
            onDismiss = { showChartStyleSheet = false },
        )
    }

    // Language picker sheet — iOS parity (ProfileView.swift:142-144). Tapping
    // the Language row opens the 13-language picker inline; selection persists
    // via SettingsViewModel.setLanguageWithLocale which writes DataStore +
    // applies the per-app locale via AppCompatDelegate.
    if (showLanguageSheet) {
        com.destinyai.astrology.ui.settings.LanguageSettingsSheet(
            onDismiss = { showLanguageSheet = false },
        )
    }

    if (state.showDeleteSheet) {
        DeleteAccountSheet(
            hasActiveSubscription = state.hasActiveSubscription,
            onDismiss = { viewModel.dismissDeleteConfirmation() },
            onConfirmDelete = { viewModel.confirmDeleteAccount() },
            isDeleting = state.isDeletingAccount,
            errorMessage = state.deleteErrorMessage,
        )
    }

    // ── Main scaffold ─────────────────────────────────────────────────────────
    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        CosmicBackground {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .pullRefresh(pullRefreshState),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header — iOS parity (ProfileView.swift:129-138):
                    // navigationTitle + topBarTrailing "Done" button (sheet style).
                    // No back arrow; Profile is presented as a modal sheet on iOS,
                    // so on Android we drop the leading arrow and use a trailing
                    // "Done" text button that calls the same onBack lambda
                    // (MainScreen.kt resets showProfile, AppNav.kt pops back stack).
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = stringResource(R.string.profile_title),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = CanelaFontFamily,
                            color = Gold,
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            TextButton(
                                onClick = onBack,
                                modifier = Modifier.align(Alignment.CenterEnd),
                            ) {
                                Text(
                                    stringResource(R.string.done),
                                    color = Gold,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    if (state.isLoading && !isRefreshing) {
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

                            // ── R2-P1 User card with gradient avatar ──────────────────
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Brush.linearGradient(listOf(NavySurface, NavyVariant)))
                                    .border(0.5.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                    .padding(20.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Gradient avatar circle
                                    val initials = state.userName.split(" ")
                                        .filter { it.isNotEmpty() }
                                        .take(2)
                                        .joinToString("") { it.first().uppercase() }
                                        .ifEmpty { "?" }
                                    Box(
                                        modifier = Modifier
                                            .size(70.dp)
                                            .clip(CircleShape)
                                            .background(AvatarGradient)
                                            .border(1.dp, Gold.copy(alpha = 0.5f), CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = initials,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AvatarInitialsColor,
                                        )
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            text = state.userName.ifEmpty { stringResource(R.string.profile_guest_label) },
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = CanelaFontFamily,
                                            color = CreamText,
                                        )
                                        if (state.email.isNotEmpty()) {
                                            // Mask auto-generated guest email (parity with iOS ProfileView.swift:269)
                                            val emailDisplay = if (state.isGuestUser) {
                                                stringResource(R.string.profile_guest_user_email)
                                            } else {
                                                state.email
                                            }
                                            Text(text = emailDisplay, fontSize = 13.sp, color = CreamDim)
                                        }

                                        // R2-P3 Plan badge
                                        Spacer(Modifier.height(6.dp))
                                        val isPaid = state.planId.isNotEmpty() && state.planId != "free_registered" && state.planId != "free_guest"
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isPaid) Gold.copy(alpha = 0.2f) else NavyVariant)
                                                .border(
                                                    0.5.dp,
                                                    if (isPaid) Gold else CreamDim.copy(alpha = 0.3f),
                                                    RoundedCornerShape(8.dp),
                                                )
                                                .padding(horizontal = 10.dp, vertical = 3.dp),
                                        ) {
                                            Text(
                                                text = if (isPaid) stringResource(R.string.profile_plan_plus) else stringResource(R.string.profile_plan_free),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isPaid) Gold else CreamDim,
                                            )
                                        }
                                    }
                                }
                            }

                            // ── R2-P2 Active chart row ────────────────────────────────
                            // iOS parity (ProfileView.swift:277-287): the "viewing as
                            // <activeProfileName>" row is a non-interactive display
                            // line embedded in the account section — no chevron, no
                            // tap target. Removing the click navigation here keeps
                            // platform parity; the Switch Profile / Manage Charts
                            // entries below provide the actionable flows.
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NavySurface)
                                    .border(0.5.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AccountCircle,
                                    contentDescription = null,
                                    tint = Gold.copy(alpha = 0.8f),
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = if (state.userName.isNotEmpty()) {
                                        stringResource(R.string.profile_viewing_birth_chart, state.userName)
                                    } else {
                                        stringResource(R.string.profile_viewing_birth_chart_default)
                                    },
                                    fontSize = 13.sp,
                                    color = CreamDim,
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            // ── R2-P5 Pending upgrade card ────────────────────────────
                            if (state.pendingUpgradePlanId != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Gold.copy(alpha = 0.08f))
                                        .border(0.5.dp, Gold.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                        .padding(14.dp),
                                ) {
                                    Text(
                                        text = state.pendingUpgradeDate?.let {
                                            stringResource(R.string.profile_pending_upgrade_with_date_format, state.pendingUpgradePlanId ?: "", it)
                                        } ?: stringResource(R.string.profile_pending_upgrade_format, state.pendingUpgradePlanId ?: ""),
                                        fontSize = 13.sp,
                                        color = Gold,
                                    )
                                }
                            }

                            // Daily Questions card removed for iOS parity — iOS
                            // ProfileView.swift does not surface daily quota on
                            // the Profile screen. Quota is shown in-line on the
                            // Home/Chat screens; duplicating it here drifts from
                            // canonical placement.

                            // ── Subscription banner ───────────────────────────────────
                            // iOS parity (ProfileView.swift:82-96): subscription banner
                            // appears IMMEDIATELY after the account card, BEFORE Profile
                            // / History / Preferences sections — so users see plan state
                            // at a glance without scrolling through preferences.
                            //
                            // Free / guest users see the upgrade hero; paid users see the
                            // crown card + Manage Subscription + View All Plans.
                            if (!state.isPremium) {
                                FreeUpgradeHeroCard(
                                    isGuest = state.isGuestUser,
                                    onClick = {
                                        // GUEST RULE: Guests must sign in first to view plans
                                        if (state.isGuestUser) {
                                            guestSignInFeature = GuestSignInFeature.SUBSCRIPTION
                                        } else {
                                            onNavigateToSubscription()
                                        }
                                    },
                                )
                            }

                            if (state.isPremium && state.planId.isNotEmpty()) {
                                PaidSubscriptionHeroCard(
                                    planDisplayName = if (state.planId == "plus") {
                                        stringResource(R.string.profile_plan_plus)
                                    } else {
                                        state.planId.replaceFirstChar { it.uppercase() }
                                    },
                                    expiryDisplayText = state.subscriptionExpiryDisplayText,
                                    pendingUpgradeText = state.pendingUpgradePlanId?.let { id ->
                                        state.pendingUpgradeDate?.let { d ->
                                            stringResource(R.string.profile_pending_upgrade_with_date_format, id, d)
                                        } ?: stringResource(R.string.profile_pending_upgrade_format, id)
                                    },
                                )
                                OutlinedButton(
                                    onClick = {
                                        // iOS opens itms-apps://apps.apple.com/account/subscriptions.
                                        // Mirror that by opening Google Play's general subs list.
                                        val url = "https://play.google.com/store/account/subscriptions"
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    },
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(width = 1.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Gold,
                                        containerColor = NavySurface,
                                    ),
                                ) {
                                    Text(stringResource(R.string.profile_manage_subscription), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                }
                                TextButton(
                                    onClick = onNavigateToSubscription,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = stringResource(R.string.profile_view_all_plans),
                                        color = CreamDim,
                                        fontSize = 14.sp,
                                    )
                                }
                            }

                            // ── Profile section header ────────────────────────────────
                            // iOS parity (ProfileView.swift:314-320 profileSection):
                            // gold "Profile" header followed by Birth Details, Manage
                            // Birth Charts, Switch Profile (in that order — Manage
                            // BEFORE Switch).
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.profile_title),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = CanelaFontFamily,
                                color = Gold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp),
                            )
                            Spacer(Modifier.height(4.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(NavySurface)
                                    .border(0.5.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(14.dp)),
                            ) {
                                Column {
                                    // Birth Details — mirrors iOS PremiumListItem('birth_details').
                                    PreferenceArrowRow(
                                        label = stringResource(R.string.profile_birth_details),
                                        subtitle = stringResource(R.string.birth_details_subtitle),
                                        leadingIcon = Icons.Filled.CalendarMonth,
                                        onClick = onNavigateToBirthDetails,
                                    )
                                    HorizontalDivider(color = Gold.copy(alpha = 0.08f))

                                    // PREMIUM GATING (iOS ProfileView.swift:332-369)
                                    val planId = state.planId
                                    val isFreePlan = planId.isEmpty() || planId == "free_guest" || planId == "free_registered"
                                    val hasMaintainProfile = !isFreePlan // Core or Plus
                                    val hasSwitchProfile = planId == "plus" // Plus only

                                    // Manage Birth Charts FIRST — iOS ProfileView.swift:332-349
                                    PreferenceArrowRow(
                                        label = stringResource(R.string.profile_manage_birth_charts),
                                        subtitle = stringResource(R.string.manage_birth_charts_subtitle),
                                        leadingIcon = Icons.Filled.People,
                                        premiumBadgeText = when {
                                            state.isGuestUser -> stringResource(R.string.profile_premium_badge_sign_up)
                                            else -> stringResource(R.string.profile_premium_badge_core)
                                        },
                                        // iOS parity (ProfileView.swift:338): green when
                                        // .maintainProfile is entitled, gold otherwise.
                                        premiumBadgeEntitled = !state.isGuestUser && hasMaintainProfile,
                                        onClick = {
                                            when {
                                                state.isGuestUser -> guestSignInFeature = GuestSignInFeature.SWITCH
                                                hasMaintainProfile -> onNavigateToPartners()
                                                else -> onNavigateToSubscription()
                                            }
                                        },
                                    )
                                    HorizontalDivider(color = Gold.copy(alpha = 0.08f))

                                    // Switch Profile SECOND — iOS ProfileView.swift:351-369
                                    PreferenceArrowRow(
                                        label = stringResource(R.string.profile_switch_profile),
                                        subtitle = if (state.userName.isNotEmpty()) {
                                            stringResource(R.string.viewing_as_label, state.userName)
                                        } else {
                                            null
                                        },
                                        leadingIcon = Icons.Filled.SwapHoriz,
                                        premiumBadgeText = when {
                                            state.isGuestUser -> stringResource(R.string.profile_premium_badge_sign_up)
                                            else -> stringResource(R.string.profile_premium_badge_plus)
                                        },
                                        // iOS parity (ProfileView.swift:359): green when
                                        // .switchProfile is entitled, gold otherwise.
                                        premiumBadgeEntitled = !state.isGuestUser && hasSwitchProfile,
                                        onClick = {
                                            when {
                                                state.isGuestUser -> guestSignInFeature = GuestSignInFeature.SWITCH
                                                hasSwitchProfile -> viewModel.showProfileSwitcher()
                                                else -> onNavigateToSubscription()
                                            }
                                        },
                                    )
                                }
                            }

                            // ── History section ───────────────────────────────────────
                            // iOS parity (ProfileView.swift:483-578 historySection):
                            // gold "History" header, save-toggle row, clear-history row.
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.history_section),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = CanelaFontFamily,
                                color = Gold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp),
                            )
                            Spacer(Modifier.height(4.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(NavySurface)
                                    .border(0.5.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(14.dp)),
                            ) {
                                Column {
                                    PreferenceToggleRow(
                                        label = stringResource(R.string.save_conversation_history),
                                        subtitle = stringResource(
                                            if (state.historyEnabled) R.string.history_chats_saved else R.string.history_turned_off,
                                        ),
                                        checked = state.historyEnabled,
                                        leadingIcon = Icons.Filled.History,
                                        onCheckedChange = {
                                            // Off-direction asks for confirmation (iOS ProfileView.swift:517-523).
                                            if (state.historyEnabled) {
                                                showTurnOffHistoryDialog = true
                                            } else {
                                                viewModel.toggleHistory(true)
                                            }
                                        },
                                    )
                                    HorizontalDivider(color = Gold.copy(alpha = 0.08f))

                                    PreferenceArrowRow(
                                        label = stringResource(R.string.clear_history),
                                        subtitle = stringResource(R.string.remove_all_saved),
                                        leadingIcon = Icons.Filled.Delete,
                                        destructive = true,
                                        onClick = { showClearHistoryDialog = true },
                                    )
                                }
                            }

                            // ── Preferences section (Astrology Settings on iOS) ───────
                            // iOS parity (ProfileView.swift:375-480 astrologySection):
                            // Language → Response Style → Astrology Settings → Chart
                            // Style → Notifications (inline toggle) → Personalized
                            // Alerts → Share Analytics.
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.profile_preferences_section),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = CanelaFontFamily,
                                color = Gold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp),
                            )
                            Spacer(Modifier.height(4.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(NavySurface)
                                    .border(0.5.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(14.dp)),
                            ) {
                                Column {
                                    PreferenceArrowRow(
                                        label = stringResource(R.string.language),
                                        subtitle = languageDisplayName(state.languageCode),
                                        leadingIcon = Icons.Filled.Language,
                                        // iOS parity (ProfileView.swift:142-144 +
                                        // LanguageSettingsSheet.swift): open the
                                        // 13-language picker inline, not the
                                        // onboarding flow. The onboarding route
                                        // remains for first-run only.
                                        onClick = { showLanguageSheet = true },
                                    )
                                    HorizontalDivider(color = Gold.copy(alpha = 0.08f))

                                    PreferenceArrowRow(
                                        label = stringResource(R.string.profile_response_style),
                                        // iOS parity (ProfileView.swift:393): subtitle
                                        // is ContentStyleManager.currentStyle.label
                                        // (Essentials / Complete Chart Details), NOT
                                        // the response-length label (Concise/Detailed).
                                        subtitle = contentStyleDisplayLabel(state.responseStyle),
                                        leadingIcon = Icons.Filled.AutoAwesome,
                                        onClick = onNavigateToResponseStyle,
                                    )
                                    HorizontalDivider(color = Gold.copy(alpha = 0.08f))

                                    // Astrology Settings — iOS parity: gated by
                                    // AppTheme.Features.showAstrologySettings = false
                                    // (AppTheme.swift:59), so the row is NOT rendered
                                    // in shipping builds. Onboarding still uses it.
                                    // PreferenceArrowRow for astrology settings is
                                    // intentionally omitted here to match iOS
                                    // ProfileView.swift:399-407.

                                    PreferenceArrowRow(
                                        label = stringResource(R.string.profile_chart_style),
                                        subtitle = stringResource(
                                            if (state.chartStyle == "south") R.string.south_indian else R.string.north_indian,
                                        ),
                                        leadingIcon = Icons.Filled.GridView,
                                        onClick = { showChartStyleSheet = true },
                                    )
                                    HorizontalDivider(color = Gold.copy(alpha = 0.08f))

                                    // Notifications inline toggle — iOS parity
                                    // (ProfileView.swift:418-436 PremiumListItem with
                                    // showChevron:false + Toggle). Tap permission row
                                    // requests/opens system settings.
                                    val notifEnabled = notifPermissionGranted ||
                                        NotificationManagerCompat.from(context).areNotificationsEnabled()
                                    val notifHaptic = remember { HapticManager(context) }
                                    NotificationPermissionRow(
                                        enabled = notifEnabled,
                                        onClick = onClick@{
                                            notifHaptic.light()
                                            if (notifEnabled) {
                                                context.startActivity(
                                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                                                )
                                                return@onClick
                                            }
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                val state = androidx.core.content.ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.POST_NOTIFICATIONS,
                                                )
                                                if (state != PackageManager.PERMISSION_GRANTED) {
                                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                    return@onClick
                                                }
                                            }
                                            context.startActivity(
                                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                                            )
                                        },
                                    )
                                    HorizontalDivider(color = Gold.copy(alpha = 0.08f))

                                    // Personalized alerts (Plus) — iOS ProfileView.swift:440-457.
                                    // iOS title key: "personalized_alerts_title" = "Personalized alerts"
                                    // (lowercase 'a'). Android matches via R.string.personalized_alerts_title.
                                    PreferenceArrowRow(
                                        label = stringResource(R.string.personalized_alerts_title),
                                        subtitle = stringResource(R.string.personalized_alerts_subtitle),
                                        leadingIcon = Icons.Filled.NotificationsActive,
                                        premiumBadgeText = when {
                                            state.isGuestUser -> stringResource(R.string.profile_premium_badge_sign_up)
                                            else -> stringResource(R.string.profile_premium_badge_plus)
                                        },
                                        // iOS parity: green when Plus-entitled.
                                        premiumBadgeEntitled = !state.isGuestUser && state.planId == "plus",
                                        onClick = {
                                            when {
                                                state.isGuestUser -> guestSignInFeature = GuestSignInFeature.ALERTS
                                                state.planId == "plus" -> onNavigateToNotificationPrefs()
                                                else -> onNavigateToSubscription()
                                            }
                                        },
                                    )
                                    HorizontalDivider(color = Gold.copy(alpha = 0.08f))

                                    // Share Usage Analytics — iOS ProfileView.swift:460-477.
                                    // iOS keys: "share_analytics_title" = "Share Usage Analytics",
                                    // "share_analytics_subtitle" = "Help improve the app by sharing
                                    // usage analytics", icon "chart.bar.fill".
                                    PreferenceToggleRow(
                                        label = stringResource(R.string.share_analytics_title),
                                        subtitle = stringResource(R.string.share_analytics_subtitle),
                                        leadingIcon = Icons.Filled.BarChart,
                                        checked = state.analyticsConsent,
                                        onCheckedChange = { viewModel.toggleAnalytics(!state.analyticsConsent) },
                                    )
                                }
                            }

                            Spacer(Modifier.weight(1f))

                            if (state.error != null) {
                                Text(text = state.error ?: "", color = Color(0xFFFF8A80), fontSize = 13.sp)
                            }

                            // R2-P11 Support links — iOS parity (ProfileView.swift:721-772
                            // supportSection): gold "Support" heading above the card,
                            // matches the Preferences heading style.
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.support_menu),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = CanelaFontFamily,
                                color = Gold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp),
                            )
                            Spacer(Modifier.height(4.dp))
                            SupportLinksSection(
                                context = context,
                                onNavigateToFaq = onNavigateToFaq,
                            )

                            // Sign Out (destructive shimmer CTA, opens confirm dialog)
                            ShimmerButton(
                                text = stringResource(R.string.sign_out),
                                onClick = { showSignOutDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                destructive = true,
                            )

                            TextButton(
                                onClick = { viewModel.showDeleteConfirmation() },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.profile_delete_account), color = Color(0xFFFF5252), fontWeight = FontWeight.SemiBold)
                            }

                            // App info footer — parity with iOS appInfoSection (ProfileView.swift:775-790):
                            // brand name, version+build, copyright. Renders three Text rows.
                            val appVersionText = remember {
                                val base = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                                val type = BuildConfig.BUILD_TYPE.lowercase()
                                if (type == "release") base else "$base [${BuildConfig.BUILD_TYPE.uppercase()}]"
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.destiny_ai_brand_name),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = CanelaFontFamily,
                                color = CreamText,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = appVersionText,
                                fontSize = 12.sp,
                                color = CreamDim.copy(alpha = 0.7f),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.copyright_full),
                                fontSize = 11.sp,
                                color = CreamDim.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )

                            Spacer(Modifier.height(32.dp))
                        }
                    }
                }

                // Pull-to-refresh indicator
                PullRefreshIndicator(
                    refreshing = isRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter),
                    backgroundColor = NavySurface,
                    contentColor = Gold,
                )
            }
        }
    }

    // Profile switcher sheet
    if (state.showProfileSwitcher) {
        ProfileSwitcherSheet(
            onDismiss = { viewModel.dismissProfileSwitcher() },
            onNavigateToPartners = onNavigateToPartners,
            onNavigateToSubscription = onNavigateToSubscription,
        )
    }

    // Guest sign-in prompt sheet (parity with iOS ProfileView.swift showGuestSignIn* sheets)
    // iOS embeds GuestSignInPromptView with the same authViewModel — the user
    // signs in directly inside the sheet without first being signed out.
    // Android mirrors this by routing onSignIn to the embedded auth flow
    // (AuthScreen) — NOT by calling viewModel.signOut() which forced a sign-out
    // bounce that iOS never does. Dismissing the sheet leaves guest state intact.
    guestSignInFeature?.let { feature ->
        GuestSignInPromptSheet(
            feature = feature,
            onDismiss = { guestSignInFeature = null },
            onSignIn = {
                guestSignInFeature = null
                onLaunchEmbeddedAuth()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuestSignInPromptSheet(
    feature: GuestSignInFeature,
    onDismiss: () -> Unit,
    onSignIn: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NavySurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Gold.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(feature.titleRes),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CanelaFontFamily,
                color = CreamText,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(feature.messageRes),
                fontSize = 14.sp,
                color = CreamDim,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Gold,
                    contentColor = Color(0xFF0D0D1A),
                ),
            ) {
                Text(stringResource(R.string.profile_sign_in_or_sign_up), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.profile_maybe_later), color = CreamDim)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun NotificationPermissionRow(enabled: Boolean, onClick: () -> Unit) {
    // Mirrors iOS PremiumListItem('notifications_title') with embedded Toggle
    // (ProfileView.swift:418-436): bell icon + title + subtitle + Switch only.
    // No "Allowed"/"Denied" status capsule — iOS shows just the toggle state.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_notifications_row")
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 36×36 gold-tinted circular icon container — matches iOS
        // PremiumListItem.swift:48-63 for visual parity with sibling rows.
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(NavyVariant)
                .border(1.dp, Gold.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = null,
                tint = Gold,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(R.string.notifications_title), fontSize = 15.sp, color = CreamText)
            Text(
                text = stringResource(R.string.notifications_setting_subtitle),
                fontSize = 12.sp,
                color = CreamDim.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Switch(
            checked = enabled,
            // Tapping the switch routes through the row's onClick so the
            // permission launcher / settings deep-link logic stays in one place.
            onCheckedChange = { onClick() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF0D0D1A),
                checkedTrackColor = Gold,
                uncheckedTrackColor = NavyVariant,
            ),
        )
    }
}

@Composable
private fun SupportLinksSection(context: android.content.Context, onNavigateToFaq: () -> Unit = {}) {
    val haptic = remember { HapticManager(context) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NavySurface)
            .border(0.5.dp, Gold.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
    ) {
        // FAQ & Help — mirrors iOS supportSection (ProfileView.swift:730-739) which
        // groups FAQ + Contact + Privacy + Terms together as four support entries.
        // Leading icons mirror iOS SF Symbols:
        //  - questionmark.circle.fill → AutoMirrored HelpOutline
        //  - envelope.fill            → Email
        //  - hand.raised.fill         → PrivacyTip
        //  - doc.text.fill            → AutoMirrored Article
        SupportLinkRow(
            label = stringResource(R.string.profile_help_faq),
            leadingIcon = Icons.AutoMirrored.Filled.HelpOutline,
            testTag = "profile_help_faq",
            onClick = {
                haptic.light()
                onNavigateToFaq()
            },
        )
        HorizontalDivider(color = Gold.copy(alpha = 0.08f))
        SupportLinkRow(
            label = stringResource(R.string.profile_contact_us),
            leadingIcon = Icons.Filled.Email,
            testTag = "profile_contact_us",
            onClick = {
                haptic.light()
                // iOS parity: ProfileView.swift:744-748 — mailto:support@destinyaiastrology.com
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@destinyaiastrology.com?subject=Support"))
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.profile_contact_us)))
            },
        )
        HorizontalDivider(color = Gold.copy(alpha = 0.08f))
        SupportLinkRow(
            label = stringResource(R.string.profile_privacy_policy),
            leadingIcon = Icons.Filled.PrivacyTip,
            testTag = "profile_privacy_policy",
            onClick = {
                haptic.light()
                // iOS parity: ProfileView.swift:754-758
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.destinyaiastrology.com/privacy-policy/")))
            },
        )
        HorizontalDivider(color = Gold.copy(alpha = 0.08f))
        SupportLinkRow(
            label = stringResource(R.string.profile_terms_of_service),
            leadingIcon = Icons.AutoMirrored.Filled.Article,
            testTag = "profile_terms_of_service",
            onClick = {
                haptic.light()
                // iOS parity: ProfileView.swift:764-768
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.destinyaiastrology.com/terms-of-service/")))
            },
        )
    }
}

@Composable
private fun SupportLinkRow(
    label: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    testTag: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (testTag != null) it.testTag(testTag) else it }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 36dp gold-tinted circular icon container — matches PreferenceArrowRow
        // (ProfileScreen.kt earlier) and iOS PremiumListItem.swift:48-63.
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(NavyVariant)
                .border(1.dp, Gold.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = Gold,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(text = label, fontSize = 15.sp, color = CreamText, modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = CreamDim.copy(alpha = 0.5f),
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun PaidSubscriptionHeroCard(
    planDisplayName: String,
    expiryDisplayText: String?,
    pendingUpgradeText: String?,
) {
    // Mirrors iOS paidSubscriptionCard hero (ProfileView.swift:639-693): gradient
    // hero box with crown icon, plan name, expiry/pending text and Active capsule.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(NavySurface, Gold.copy(alpha = 0.18f))))
            .border(0.5.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = planDisplayName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CanelaFontFamily,
                    color = CreamText,
                )
                if (!pendingUpgradeText.isNullOrEmpty()) {
                    Text(
                        text = pendingUpgradeText,
                        fontSize = 12.sp,
                        color = Color(0xFFFFA94D),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                } else if (!expiryDisplayText.isNullOrEmpty()) {
                    Text(
                        text = expiryDisplayText,
                        fontSize = 13.sp,
                        color = CreamDim,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            // Active capsule
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.9f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.profile_paid_active),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0D0D1A),
                )
            }
        }
    }
}

@Composable
private fun PreferenceArrowRow(
    label: String,
    onClick: () -> Unit,
    premiumBadgeText: String? = null,
    // iOS parity (PremiumListItem.swift:84-98): green when the feature is
    // entitled (unlocked), gold when locked. Defaults to gold.
    premiumBadgeEntitled: Boolean = false,
    subtitle: String? = null,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    // iOS parity (ProfileView.swift:537-569): destructive rows (Clear history)
    // tint the icon container, icon, and label in error red.
    destructive: Boolean = false,
) {
    val errorRed = Color(0xFFFF5252)
    val iconContainerColor = if (destructive) errorRed.copy(alpha = 0.15f) else NavyVariant
    val iconBorderColor = if (destructive) errorRed.copy(alpha = 0.3f) else Gold.copy(alpha = 0.15f)
    val iconTint = if (destructive) errorRed else Gold
    val labelColor = if (destructive) errorRed else CreamText
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // iOS parity (PremiumListItem.swift:48-63): 36×36 circular gold-tinted
        // icon container at the start of every Profile row. Skipped silently
        // when the row doesn't pass an icon, keeping List parity intact.
        if (leadingIcon != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconContainerColor)
                    .border(1.dp, iconBorderColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontSize = 15.sp, color = labelColor)
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = CreamDim.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        // Premium badge — iOS PremiumListItem.swift:84-98:
        //  - Crown icon + label, capsule-shaped
        //  - Color = green when entitled, gold when locked
        //  - Background = same color at 15% opacity
        if (!premiumBadgeText.isNullOrEmpty()) {
            val badgeColor = if (premiumBadgeEntitled) Color(0xFF34C759) else Gold
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(badgeColor.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.WorkspacePremium,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(11.dp),
                )
                Text(
                    text = premiumBadgeText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = badgeColor,
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = CreamDim.copy(alpha = 0.5f),
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun PreferenceToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: () -> Unit,
    subtitle: String? = null,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // iOS parity (ProfileView.swift:493-501): 36×36 gold-tinted leading
        // icon container, matches PreferenceArrowRow visuals so toggle and
        // arrow rows align across a section.
        if (leadingIcon != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(NavyVariant)
                    .border(1.dp, Gold.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontSize = 15.sp, color = CreamText)
            if (!subtitle.isNullOrEmpty()) {
                // Mirrors iOS save_conversation_history live status subtitle
                // (history_chats_saved / history_turned_off) at ProfileView.swift:508.
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = CreamDim.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = { onCheckedChange() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF0D0D1A),
                checkedTrackColor = Gold,
                uncheckedTrackColor = NavyVariant,
            ),
        )
    }
}

/**
 * Free-user upgrade hero card — mirrors iOS freeUpgradeCard
 * (ProfileView.swift:594-635). Gradient background, white circle holding a
 * star/crown icon, two-line headline + subtitle, trailing chevron.
 */
@Composable
private fun FreeUpgradeHeroCard(isGuest: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Gold, GoldDeep)))
            .border(0.5.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                // Closest Material match for SF Symbol "crown.fill".
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isGuest) {
                        stringResource(R.string.profile_sign_up_cta)
                    } else {
                        stringResource(R.string.profile_upgrade_premium)
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CanelaFontFamily,
                    color = Color.White,
                )
                Text(
                    text = if (isGuest) {
                        stringResource(R.string.save_birth_chart_unlock_insights)
                    } else {
                        stringResource(R.string.unlock_unlimited_insights)
                    },
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Maps a language code (e.g. "en", "hi") to its native display name.
 * Mirrors iOS currentLanguageDisplay at ProfileView.swift:991-998 — same 13
 * codes as the picker (LanguageSettingsSheet.kt:171-185), same native-script
 * names, and the same "English" fallback when the code is unrecognized.
 */
private fun languageDisplayName(code: String): String = when (code) {
    "en" -> "English"
    "hi" -> "हिन्दी"
    "ta" -> "தமிழ்"
    "te" -> "తెలుగు"
    "kn" -> "ಕನ್ನಡ"
    "ml" -> "മലയാളം"
    "es" -> "Español"
    "pt" -> "Português"
    "de" -> "Deutsch"
    "fr" -> "Français"
    "zh-Hans" -> "中文"
    "ja" -> "日本語"
    "ru" -> "Русский"
    else -> "English"
}

/**
 * Maps a response-style key (e.g. "guidance") to its localized label.
 * Mirrors iOS ContentStyleManager.shared.currentStyle.label at ProfileView.swift:393.
 */
@Composable
private fun responseStyleDisplayLabel(style: String): String = when (style) {
    "concise" -> stringResource(R.string.response_style_concise)
    "detailed" -> stringResource(R.string.response_style_detailed)
    "guidance" -> stringResource(R.string.response_style_concise)
    "prediction" -> stringResource(R.string.response_style_detailed)
    else -> style.replaceFirstChar { it.uppercase() }
}

/**
 * iOS parity (ResponseStyleManager.swift:55-67 + ProfileView.swift:393):
 * the Profile "Response Style" subtitle shows the *content style* label
 * (Essentials / Complete Chart Details), not the response-length label.
 *  - "guidance" raw value  → Essentials
 *  - "astrology" raw value → Complete Chart Details
 * Falls back to "Essentials" when unrecognised, matching iOS default.
 */
@Composable
private fun contentStyleDisplayLabel(style: String): String = when (style) {
    "guidance", "essentials" -> stringResource(R.string.content_style_essentials)
    "astrology", "complete", "completeChart" -> stringResource(R.string.content_style_complete)
    else -> stringResource(R.string.content_style_essentials)
}
