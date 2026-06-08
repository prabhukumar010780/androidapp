package com.destinyai.astrology.ui.main

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
import androidx.compose.ui.unit.sp
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
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.DarkNavyContrast
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.GoldChampagne
import com.destinyai.astrology.ui.theme.NavyDeep
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
                        showHistory = false
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
                        showHistory = false
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

        // Tab bar — hidden on chat tab (index 1), when soft keyboard is visible
        // (mirrors iOS MainTabView.swift:116 isKeyboardVisible check), when a
        // match result is showing, or when an overlay screen is pushed.
        val isImeVisible = WindowInsets.isImeVisible
        val tabBarVisible = selectedTab != 1 &&
            !isImeVisible &&
            !showMatchResult &&
            !showHistory &&
            !showProfile
        if (tabBarVisible) {
            DestinyTabBar(
                selectedTab = selectedTab,
                onTabSelected = { newTab ->
                    // Re-tapping the active Home tab triggers a scroll-to-top in
                    // HomeScreen (parity with iOS UITabBar double-tap behavior).
                    if (newTab == 0 && selectedTab == 0) {
                        homeScrollTopTick += 1
                    }
                    selectedTab = newTab
                },
                // 20dp horizontal / 10dp vertical outer padding mirrors iOS
                // CustomTabBar's outer chrome (Issue P2-7).
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
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
    // filled with mainBackground, with a gold gradient line at the top edge and a
    // FAB contained INSIDE the bar (no overhang). The full 76dp height is opaque
    // so content scrolling underneath doesn't bleed through gaps.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(NavyDeep)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(76.dp),
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

        // Row of 3 tab slots — fills the full 76dp height, anchored center.
        // Center slot is a transparent Spacer placeholder so layout/weights stay
        // consistent; the visible FAB is rendered separately above this Row.
        Row(
            modifier = Modifier
                .fillMaxSize()
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
                    .testTag("tab_home"),
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
                    .testTag("tab_match"),
            )
        }

        // Center FAB — fully INSIDE the bar (no negative offset). zIndex keeps
        // it visually layered above the row but every pixel of the bar remains
        // opaque navy so content underneath never bleeds through.
        AskFabButton(
            isSelected = selectedTab == 1,
            label = stringResource(R.string.ask),
            onClick = { onTabSelected(1) },
            modifier = Modifier
                .align(Alignment.Center)
                .zIndex(1f)
                .testTag("tab_chat"),
        )
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
            .padding(vertical = 6.dp),
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
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
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
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (isSelected) CreamText else Gold,
            modifier = Modifier.offset(y = (-10).dp),
        )
    }
}
