package com.destinyai.astrology.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.destinyai.astrology.R
import com.destinyai.astrology.domain.model.CompatibilityHistoryItem
import com.destinyai.astrology.domain.model.ComparisonGroup
import com.destinyai.astrology.services.NotificationDeepLink
import com.destinyai.astrology.services.NotificationRouter
import com.destinyai.astrology.ui.auth.GuestSignInPromptScreen
import com.destinyai.astrology.ui.chat.ChatScreen
import com.destinyai.astrology.ui.compatibility.CompatibilityScreen
import com.destinyai.astrology.ui.history.HistoryScreen
import com.destinyai.astrology.ui.home.HomeScreen
import com.destinyai.astrology.ui.profile.ProfileScreen
import com.destinyai.astrology.ui.theme.AppType
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.DarkNavyContrast
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.GoldChampagne
import com.destinyai.astrology.ui.theme.NavyDeep
import com.destinyai.astrology.ui.theme.Spacing
import com.destinyai.astrology.ui.theme.WidthClass
import com.destinyai.astrology.ui.theme.currentWidthClass
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Mirrors iOS MainTabView. Three primary tabs (Home / Ask FAB / Match) match
 * iOS CustomTabBar exactly. History and Profile are pushed as full screens
 * via callbacks from HomeScreen rather than living as tabs.
 *
 * Tabs are kept co-resident in a Box (mirrors iOS MainTabView's ZStack with
 * .opacity + .allowsHitTesting) so Chat scroll/draft text and Match form state
 * survive tab switches. Chat and Match render lazily on first visit
 * (hasVisitedChat / hasVisitedMatch — parity with iOS lazy `if` gates).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToCharts: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToPartners: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSubscription: () -> Unit,
    onDeletedAccount: () -> Unit,
    onNavigateToLanguage: () -> Unit = {},
    onNavigateToResponseStyle: () -> Unit = {},
    onNavigateToNotificationPrefs: () -> Unit = {},
    onNavigateToFaq: () -> Unit = {},
    onNavigateToAstrologySettings: () -> Unit = {},
    // iOS parity (ProfileView.swift:139-141 .sheet showBirthDetails →
    // BirthDetailsView): the Profile screen needs a route to Birth Details.
    // Without this, tapping the Birth Details row on the in-overlay Profile
    // (showProfile = true on this MainScreen) is a no-op because the default
    // lambda in ProfileScreen swallows the tap.
    onNavigateToBirthDetails: () -> Unit = {},
    onNavigateToAuth: () -> Unit = {},
    viewModel: MainScreenViewModel = hiltViewModel(),
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    // Scroll-to-top tick — incremented when the user re-taps the Home tab while
    // already on Home (parity with iOS UITabBar double-tap behavior).
    var homeScrollTopTick by remember { mutableIntStateOf(0) }
    var pendingQuestion by remember { mutableStateOf<String?>(null) }
    var pendingThreadId by remember { mutableStateOf<String?>(null) }
    // Mirrors iOS MainTabView pendingMatchItem / pendingMatchGroup — set by
    // HomeScreen's match-history tap callbacks, consumed by CompatibilityScreen.
    var pendingMatchItem by remember { mutableStateOf<CompatibilityHistoryItem?>(null) }
    var pendingMatchGroup by remember { mutableStateOf<ComparisonGroup?>(null) }

    // Mirrors iOS hasVisitedChat / hasVisitedMatch — keeps inactive tabs alive
    // after first visit so scroll position, draft text, and form state survive
    // tab switches without re-creating composables.
    var hasVisitedChat by remember { mutableStateOf(false) }
    var hasVisitedMatch by remember { mutableStateOf(false) }

    // Top-level overlays — History/Profile aren't tabs; they push over content.
    var showHistory by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    // Mirrors iOS @State showMatchResult — toggled by CompatibilityScreen.
    var showMatchResult by remember { mutableStateOf(false) }

    // System back gesture/button dismisses History/Profile overlays (Cat 3 dead-end fix).
    // Mirrors BirthDataScreen.kt:106 BackHandler pattern. Enabled only when the overlay
    // is showing so the back event falls through to the OS when neither is active.
    BackHandler(enabled = showHistory) { showHistory = false }
    BackHandler(enabled = showProfile) { showProfile = false }

    // Mirrors iOS @AppStorage("isGuest") — observe guest user state.
    val isGuestUser by viewModel.isGuestUser.collectAsState()

    // Mirrors iOS .id(ProfileContextManager.shared.activeProfileId) on the Match
    // tab. When the user switches profiles the CompatibilityScreen must be
    // re-keyed and any pending match state cleared.
    val activeProfileId by viewModel.activeProfileId.collectAsState()
    LaunchedEffect(activeProfileId) {
        pendingMatchItem = null
        pendingMatchGroup = null
        showMatchResult = false
    }

    // Mirrors iOS .onChange(of: selectedTab) — dismiss the soft keyboard on every
    // tab switch and clear pending match state when leaving the Match tab.
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var previousTab by remember { mutableIntStateOf(0) }
    LaunchedEffect(selectedTab) {
        keyboardController?.hide()
        focusManager.clearFocus()
        if (selectedTab == 1) hasVisitedChat = true
        if (selectedTab == 2) hasVisitedMatch = true
        if (previousTab == 2 && selectedTab != 2) {
            pendingMatchItem = null
            pendingMatchGroup = null
            showMatchResult = false
        }
        previousTab = selectedTab
    }

    // Mirrors iOS .onChange(of: notificationRouter.pendingDeepLink). MainScreen
    // only consumes deep links targeting an intra-MAIN tab (Home / Chat / Match).
    // Settings is handled by AppNav (it pushes NotificationPrefs onto the back
    // stack). Skip Settings here so we don't double-consume.
    val pendingDeepLink by NotificationRouter.pendingDeepLink.collectAsState()
    LaunchedEffect(pendingDeepLink) {
        val deepLink = pendingDeepLink ?: return@LaunchedEffect
        when (deepLink) {
            is NotificationDeepLink.Home -> {
                showHistory = false
                showProfile = false
                selectedTab = 0
                NotificationRouter.consume()
            }
            is NotificationDeepLink.Chat -> {
                if (deepLink.newThread) {
                    pendingQuestion = null
                    pendingThreadId = null
                }
                showHistory = false
                showProfile = false
                selectedTab = 1
                if (deepLink.prefill.isNotEmpty()) {
                    pendingQuestion = deepLink.prefill
                }
                NotificationRouter.consume()
            }
            is NotificationDeepLink.Match -> {
                showHistory = false
                showProfile = false
                selectedTab = 2
                NotificationRouter.consume()
            }
            // Settings is consumed by AppNav.kt (pushes NotificationPrefs route).
            // Mirrors iOS MainTabView.swift:174-176 — when the user returns from
            // the pushed NotificationPrefs route they should land on Home, so
            // reset selectedTab to 0 here. AppNav still owns the route push and
            // the NotificationRouter.consume() call.
            is NotificationDeepLink.Settings -> {
                showHistory = false
                showProfile = false
                selectedTab = 0
            }
        }
    }

    // Mirrors iOS .alert(item: $quotaManager.externalPlanChangeAlert)
    val planChange by viewModel.externalPlanChangeAlert.collectAsState()
    planChange?.let { change ->
        AlertDialog(
            onDismissRequest = { viewModel.clearExternalPlanChangeAlert() },
            title = { Text(stringResource(R.string.subscription_activated_title)) },
            text = { Text(externalPlanChangeMessage(change)) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearExternalPlanChangeAlert() }) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }

    // Mirrors iOS PushNotificationService.requestPermission() timing — ask AFTER the
    // user has successfully signed in and landed on the main app, not on cold onCreate.
    // Approach: one-time rationale dialog ("Get daily guidance & reminders") shown on
    // the FIRST ever MainScreen composition, gated by a SharedPreferences boolean
    // (has_requested_notif_permission). The rationale dialog fires first; the user's
    // "Enable" tap then triggers the OS system prompt. If they already granted or are
    // below API 33, we skip silently. This matches iOS UNUserNotificationCenter timing
    // where the system dialog appears after the user has completed onboarding/auth.
    val notifContext = LocalContext.current
    val notifPrefsFile = "destiny_notif_prefs"
    val notifRequestedKey = "has_requested_notif_permission"
    // Compose-idiomatic launcher — wraps the system dialog; result only logged because
    // MainActivity.notificationPermissionLauncher remains the primary registered launcher.
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d("MainScreen", if (granted) "POST_NOTIFICATIONS granted" else "POST_NOTIFICATIONS denied")
    }
    // State: show rationale dialog before the OS system prompt (parity with iOS
    // UNUserNotificationCenter requestAuthorization pre-prompt UX).
    var showNotifRationale by remember { mutableStateOf(false) }
    // One-shot: check on first composition whether we should ask.
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        val alreadyGranted = ContextCompat.checkSelfPermission(
            notifContext, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) return@LaunchedEffect
        // Only ask once per install — gate on the SharedPreferences flag.
        val sp = notifContext.getSharedPreferences(notifPrefsFile, android.content.Context.MODE_PRIVATE)
        val alreadyRequested = sp.getBoolean(notifRequestedKey, false)
        if (!alreadyRequested) {
            showNotifRationale = true
        }
    }
    // Rationale dialog — shown before the OS system prompt so the user understands
    // why push notifications are useful. Matches Android best-practice for
    // in-context permission requests with a pre-prompt explanation.
    if (showNotifRationale) {
        AlertDialog(
            onDismissRequest = {
                showNotifRationale = false
                // Mark as requested even on dismiss — don't nag again.
                notifContext.getSharedPreferences(notifPrefsFile, android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean(notifRequestedKey, true).apply()
            },
            title = { Text(stringResource(R.string.notif_rationale_title)) },
            text = { Text(stringResource(R.string.notif_rationale_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showNotifRationale = false
                    notifContext.getSharedPreferences(notifPrefsFile, android.content.Context.MODE_PRIVATE)
                        .edit().putBoolean(notifRequestedKey, true).apply()
                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) {
                    Text(stringResource(R.string.notif_rationale_enable))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showNotifRationale = false
                    notifContext.getSharedPreferences(notifPrefsFile, android.content.Context.MODE_PRIVATE)
                        .edit().putBoolean(notifRequestedKey, true).apply()
                }) {
                    Text(stringResource(R.string.notif_rationale_not_now))
                }
            },
        )
    }

    // Mirrors iOS .alert(item: $subscriptionManager.subscriptionConflict)
    val conflict by viewModel.subscriptionConflict.collectAsState()
    conflict?.let {
        AlertDialog(
            onDismissRequest = { viewModel.clearSubscriptionConflict() },
            title = { Text(stringResource(R.string.subscription_conflict_title)) },
            text = { Text(stringResource(R.string.subscription_conflict_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearSubscriptionConflict() }) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(NavyDeep)) {
        // Navigation visibility (shared by bottom bar AND rail) — hidden on chat tab
        // (index 1), when the soft keyboard is visible (iOS MainTabView.swift:116),
        // when a match result is showing, or when an overlay screen is pushed.
        val isImeVisible = WindowInsets.isImeVisible
        val navVisible = selectedTab != 1 &&
            !isImeVisible &&
            !showMatchResult &&
            !showHistory &&
            !showProfile
        // On Expanded-width screens (tablets/large foldables) use a left NavigationRail
        // instead of the bottom bar, per Material 3 large-screen guidance. Compact and
        // Medium widths keep the iOS-parity bottom bar.
        val useRail = currentWidthClass() == WidthClass.Expanded
        val railVisible = useRail && navVisible
        // Inset co-resident tab content by the rail so it doesn't sit under it.
        val contentStartInset = if (railVisible) NavRailWidth else 0.dp

        // Re-tapping the active Home tab triggers a scroll-to-top in HomeScreen
        // (parity with iOS UITabBar double-tap). Shared by bar and rail.
        val onTabSelected: (Int) -> Unit = { newTab ->
            if (newTab == 0 && selectedTab == 0) {
                homeScrollTopTick += 1
            }
            selectedTab = newTab
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = contentStartInset),
        ) {
            when {
            showHistory -> HistoryScreen(
                onBack = { showHistory = false },
                // Mirrors iOS HomeView.onChatHistorySelected — dismiss History,
                // hand the threadId to MainScreen, switch to the Chat tab.
                onChatSelected = { threadId ->
                    pendingThreadId = threadId
                    showHistory = false
                    selectedTab = 1
                },
                // Mirrors iOS HomeView.onMatchHistorySelected — guest gate then
                // hand the matchItem to the Match tab via pendingMatchItem.
                onMatchSelected = { sessionId ->
                    if (isGuestUser) {
                        // iOS parity (MainTabView.swift:47-48): a guest tapping a match must be
                        // routed to the sign-in prompt, not silently dismissed. The Match tab
                        // already renders GuestSignInPromptScreen for guests, so switch to it (F2).
                        showHistory = false
                        selectedTab = 2
                    } else {
                        viewModel.findMatchHistoryItem(sessionId)?.let {
                            pendingMatchItem = it
                            pendingMatchGroup = null
                            showHistory = false
                            selectedTab = 2
                        }
                    }
                },
                onMatchGroupSelected = { groupId ->
                    if (isGuestUser) {
                        // iOS parity (MainTabView.swift:56-57): guest → sign-in prompt (F2).
                        showHistory = false
                        selectedTab = 2
                    } else {
                        viewModel.findMatchHistoryGroup(groupId)?.let {
                            pendingMatchGroup = it
                            pendingMatchItem = null
                            showHistory = false
                            selectedTab = 2
                        }
                    }
                },
                // Mirrors iOS HistoryView.swift:89-93 — when history is disabled
                // the "Open Settings" CTA must deep-link to Profile Settings
                // (NotificationCenter `.openProfileSettings`). On Android we
                // dismiss the History overlay then surface the Profile screen.
                onOpenProfileSettings = {
                    showHistory = false
                    showProfile = true
                },
            )
            showProfile -> ProfileScreen(
                onBack = { showProfile = false },
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToSubscription = onNavigateToSubscription,
                onDeletedAccount = onDeletedAccount,
                onNavigateToLanguage = onNavigateToLanguage,
                onNavigateToResponseStyle = onNavigateToResponseStyle,
                onNavigateToNotificationPrefs = onNavigateToNotificationPrefs,
                onNavigateToCharts = onNavigateToCharts,
                onNavigateToPartners = onNavigateToPartners,
                onNavigateToFaq = onNavigateToFaq,
                onNavigateToAstrologySettings = onNavigateToAstrologySettings,
                onNavigateToBirthDetails = onNavigateToBirthDetails,
                // iOS parity (ProfileView.swift showGuestSignInSheet/showGuestSignInForSwitch/
                // showGuestSignInForAlerts → GuestSignInPromptView): all guest gates inside Profile
                // (Switch Profile, Manage Charts, Alerts, Subscription) must route to AuthScreen.
                onLaunchEmbeddedAuth = onNavigateToAuth,
            )
            else -> {
                // Co-resident tabs. Mirrors iOS MainTabView's ZStack with
                // .opacity + .allowsHitTesting so each tab keeps its scroll
                // position and form/draft state across switches.
                Box(modifier = Modifier.fillMaxSize()) {
                    // HOME tab — always loaded
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(if (selectedTab == 0) 1f else 0f),
                    ) {
                        HomeScreen(
                            modifier = Modifier.fillMaxSize(),
                            onNavigateToCharts = onNavigateToCharts,
                            onNavigateToHistory = { showHistory = true },
                            onNavigateToNotifications = onNavigateToNotifications,
                            onNavigateToProfile = { showProfile = true },
                            onAskDestiny = { prompt ->
                                pendingQuestion = prompt
                                selectedTab = 1
                            },
                            scrollToTopTick = homeScrollTopTick,
                        )
                    }
                    // Block hit-testing on inactive Home overlay
                    // (mirrors iOS .allowsHitTesting(selectedTab == 0))
                    if (selectedTab != 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(0f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { /* swallow taps from underlying Home */ },
                        )
                    }

                    // CHAT tab — lazily loaded on first visit
                    if (hasVisitedChat || selectedTab == 1) {
                        val initialQ = pendingQuestion
                        val initialT = pendingThreadId
                        LaunchedEffect(selectedTab, initialQ, initialT) {
                            if (selectedTab == 1) {
                                if (initialQ != null) pendingQuestion = null
                                if (initialT != null) pendingThreadId = null
                            }
                        }
                        // iOS parity (.allowsHitTesting(selectedTab == 1)): when Chat is the
                        // INACTIVE tab we render the cached UI but with size=0 so it cannot
                        // intercept Home's scroll gestures. Earlier we used a pointerInput
                        // consumer here, but that swallowed scroll/tap events meant for the
                        // active Home layer underneath, breaking Home scroll after the user
                        // returned from Chat via the back button.
                        Box(
                            modifier = if (selectedTab == 1) {
                                Modifier.fillMaxSize()
                            } else {
                                Modifier.size(0.dp)
                            },
                        ) {
                            ChatScreen(
                                modifier = Modifier.fillMaxSize(),
                                // Back chevron returns to the Home tab (iOS parity:
                                // chat tab hides the bottom bar, so back is the
                                // primary affordance to return to Home).
                                onBack = { selectedTab = 0 },
                                onNavigateToAuth = onNavigateToAuth,
                                onNavigateToSettings = onNavigateToSettings,
                                initialQuestion = initialQ,
                                initialThreadId = initialT,
                            )
                        }
                    }

                    // MATCH tab — lazily loaded on first visit. Re-keyed by
                    // active profile id so a profile switch fully recreates the
                    // VM-backed UI (parity with iOS .id(activeProfileId)).
                    if (hasVisitedMatch || selectedTab == 2) {
                        // iOS parity (.allowsHitTesting(selectedTab == 2)): inactive Match
                        // tab is rendered with size=0 so it cannot intercept Home/Chat
                        // gestures (same fix as Chat tab — see comment above).
                        Box(
                            modifier = if (selectedTab == 2) {
                                Modifier.fillMaxSize()
                            } else {
                                Modifier.size(0.dp)
                            },
                        ) {
                            if (isGuestUser) {
                                GuestSignInPromptScreen(
                                    message = stringResource(R.string.sign_in_to_check_compatibility),
                                    // iOS parity (MainTabView.swift GuestSignInPromptView): the Sign In CTA
                                    // must route to AuthScreen, not just reset to Home. Previous bug:
                                    // onSignIn = { selectedTab = 0 } stranded the user on Home with no
                                    // path to actually authenticate.
                                    onSignIn = onNavigateToAuth,
                                    onBack = { selectedTab = 0 },
                                )
                            } else {
                                key(activeProfileId) {
                                    CompatibilityScreen(
                                        modifier = Modifier.fillMaxSize(),
                                        onBack = {},
                                        onNavigateToPartners = onNavigateToPartners,
                                        onNavigateToSettings = onNavigateToSettings,
                                        // iOS parity (CompatibilityView.swift signOutAndReauth):
                                        // wire host's auth navigator so QuotaExhaustedDialog Sign In
                                        // routes to AuthScreen instead of stranding on the Match tab.
                                        onNavigateToAuth = onNavigateToAuth,
                                        onShowResultChange = { showMatchResult = it },
                                        initialMatchItem = pendingMatchItem,
                                        initialMatchGroup = pendingMatchGroup,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        } // end tab-content wrapper Box (rail inset)

        // Bottom bar — Compact/Medium widths only. Docked flush to the bottom edge.
        if (navVisible && !useRail) {
            DestinyTabBar(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
                // iOS parity (MainTabView:117-119: "No horizontal padding for full
                // width" + .padding(.bottom, 0) docked to bottom; the bar itself is a
                // full-width Rectangle with .frame(maxWidth: .infinity)). The bar's own
                // inner Row already has .padding(horizontal = 30.dp) matching iOS's inner
                // 30pt touch-target inset. Previously an outer 20dp/10dp padding made the
                // bar a floating inset pill with edge gaps — remove it so it spans the full
                // width and docks flush to the bottom like iOS.
                modifier = Modifier
                    .align(Alignment.BottomCenter),
            )
        }

        // Navigation rail — Expanded width only. Anchored to the leading edge.
        if (railVisible) {
            DestinyNavigationRail(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
    }
}

/** Mirrors iOS MainTabView.externalPlanChangeMessage — formats the alert body. */
@Composable
private fun externalPlanChangeMessage(change: com.destinyai.astrology.services.ExternalPlanChange): String {
    val ctx = LocalContext.current
    val parts = mutableListOf<String>()
    val previous = change.previousPlanId
    val firstLine = if (previous != null && previous.isNotEmpty() && previous != change.newPlanId) {
        ctx.getString(R.string.subscription_plan_updated, change.newPlanDisplayName)
    } else {
        ctx.getString(R.string.subscription_now_active, change.newPlanDisplayName)
    }
    parts.add(firstLine)
    val expires = change.expiresAt
    if (!expires.isNullOrEmpty()) {
        val formatted = formatExpiryDate(expires)
        val secondLine = when (change.willAutoRenew) {
            true -> ctx.getString(R.string.subscription_auto_renews, formatted)
            false -> ctx.getString(R.string.subscription_ends_on, formatted)
            null -> ctx.getString(R.string.subscription_active_until, formatted)
        }
        parts.add(secondLine)
    }
    return parts.joinToString("\n\n")
}

private fun formatExpiryDate(iso: String): String {
    return try {
        val parsed = OffsetDateTime.parse(iso, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val display = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        display.format(java.util.Date.from(parsed.toInstant()))
    } catch (e: Exception) {
        iso
    }
}

/** Width of the left NavigationRail shown on Expanded (tablet/large) screens. */
private val NavRailWidth = 88.dp

/**
 * Left navigation rail for Expanded-width screens (tablets, large foldables). Mirrors
 * the three primary destinations of [DestinyTabBar] (Home / Ask / Match) but laid out
 * vertically on the leading edge, per Material 3 large-screen guidance. The bottom bar
 * is hidden when this is shown; both are gated by the same visibility rules so a tablet
 * never shows two navigation surfaces at once.
 */
@Composable
private fun DestinyNavigationRail(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(NavRailWidth)
            .background(NavyDeep)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        // Gold trailing border (1dp vertical gradient) — mirrors the bar's top border.
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .align(Alignment.CenterEnd)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Gold.copy(alpha = 0.5f), Color.Transparent),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .align(Alignment.Center)
                .padding(vertical = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xl, Alignment.CenterVertically),
        ) {
            TabBarItem(
                vectorIcon = Icons.Filled.Home,
                label = stringResource(R.string.home),
                selected = selectedTab == 0,
                onClick = { onTabSelected(0) },
                modifier = Modifier.testTag("rail_home"),
            )
            AskFabButton(
                isSelected = selectedTab == 1,
                label = stringResource(R.string.ask),
                onClick = { onTabSelected(1) },
                modifier = Modifier.testTag("rail_chat"),
            )
            TabBarItem(
                vectorIcon = Icons.Filled.Favorite,
                label = stringResource(R.string.match),
                selected = selectedTab == 2,
                onClick = { onTabSelected(2) },
                modifier = Modifier.testTag("rail_match"),
            )
        }
    }
}

/**
 * Three-tab bar mirroring iOS CustomTabBar (Home / Ask FAB / Match).
 * - 48dp FAB with 56dp glow ring when selected, offset y = -12dp
 * - Champagne→gold linear gradient, gold drop shadow
 * - Gold top border, navy background extending behind nav bar via insets
 */
@Composable
private fun DestinyTabBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // iOS parity (MainTabView CustomTabBar:305-330): tab bar is a SOLID rectangle
    // filled with mainBackground that IGNORES the safe area (navy paints all the way
    // to the physical bottom edge, behind the gesture-nav indicator), with a gold
    // gradient line at the top edge and a FAB contained INSIDE the bar. Only the tab
    // CONTENT respects the navigation-bar inset — the background does not — so there's
    // no unpainted gap below the bar.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(NavyDeep),
    ) {
        // Gold top border (1dp gradient) — anchored to the very top of the bar.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Gold.copy(alpha = 0.5f), Color.Transparent),
                    ),
                ),
        )

        // Row of 3 tab slots — fixed 76dp content height ABOVE the nav-bar inset, so the
        // navy background below (the inset region) stays painted like iOS's ignoresSafeArea.
        // Center slot is a transparent Spacer placeholder so layout/weights stay
        // consistent; the visible FAB is rendered separately above this Row.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(76.dp)
                .padding(horizontal = 30.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabBarItem(
                vectorIcon = Icons.Filled.Home,
                label = stringResource(R.string.home),
                selected = selectedTab == 0,
                onClick = { onTabSelected(0) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("tab_home")
                    .semantics { contentDescription = "tab_home" },
            )

            // Transparent placeholder — preserves layout for the center FAB.
            Spacer(modifier = Modifier.weight(1f))

            TabBarItem(
                vectorIcon = Icons.Filled.Favorite,
                label = stringResource(R.string.match),
                selected = selectedTab == 2,
                onClick = { onTabSelected(2) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("tab_match")
                    .semantics { contentDescription = "tab_match" },
            )
        }

        // Center FAB — occupies the same 76dp content region as the tab Row (above the
        // nav-bar inset) and is centered within it, matching iOS where the Ask button is an
        // inline center item. Its own internal -12dp icon offset provides the slight raise.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(76.dp),
            contentAlignment = Alignment.Center,
        ) {
            AskFabButton(
                isSelected = selectedTab == 1,
                label = stringResource(R.string.ask),
                onClick = { onTabSelected(1) },
                modifier = Modifier
                    .zIndex(1f)
                    .testTag("tab_chat")
                    .semantics { contentDescription = "tab_chat" },
            )
        }
    }
}

@Composable
private fun TabBarItem(
    vectorIcon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "tab_press_scale",
    )

    // iOS .symbolEffect(.bounce) substitute: brief scale bump on selection.
    var bounceScale by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            bounceScale = 1.15f
            kotlinx.coroutines.delay(120)
            bounceScale = 1f
        }
    }
    val animatedBounce by animateFloatAsState(
        targetValue = bounceScale,
        animationSpec = tween(durationMillis = 200),
        label = "tab_bounce_scale",
    )

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            )
            .semantics { contentDescription = label }
            .padding(vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = vectorIcon,
            contentDescription = null,
            tint = if (selected) CreamText else Gold,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = animatedBounce
                    scaleY = animatedBounce
                },
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = label,
            fontSize = AppType.caption,
            lineHeight = AppType.captionLh,
            fontWeight = FontWeight.Normal,
            color = if (selected) CreamText else Gold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AskFabButton(
    isSelected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "ask_press_scale",
    )

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                clip = false
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            )
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .offset(y = (-12).dp),
            contentAlignment = Alignment.Center,
        ) {
            // Outer glow when selected (iOS Circle 56pt @ 0.3 gold opacity)
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Gold.copy(alpha = 0.3f)),
                )
            }
            // FAB body (48dp) — gold gradient + drop shadow
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .shadow(
                        elevation = if (isSelected) 10.dp else 6.dp,
                        shape = CircleShape,
                        ambientColor = Gold.copy(alpha = 0.5f),
                        spotColor = Gold.copy(alpha = 0.5f),
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(GoldChampagne, Gold),
                            start = Offset(0f, 0f),
                            end = Offset(48f, 48f),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_chat_bubbles),
                    contentDescription = null,
                    tint = DarkNavyContrast,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        // Pull label up since the FAB is offset upward.
        Text(
            text = label,
            fontSize = AppType.caption,
            lineHeight = AppType.captionLh,
            fontWeight = FontWeight.Normal,
            color = if (isSelected) CreamText else Gold,
            modifier = Modifier.offset(y = (-10).dp),
        )
    }
}
