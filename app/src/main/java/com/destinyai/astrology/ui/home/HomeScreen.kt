package com.destinyai.astrology.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.destinyai.astrology.R
import com.destinyai.astrology.services.HapticManager
import com.destinyai.astrology.services.SoundManager
import com.destinyai.astrology.ui.profile.ProfileSwitcherViewModel
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.GoldLight
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.NavyVariant
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onNavigateToCharts: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onAskDestiny: (String) -> Unit = {},
    // Scroll-to-top tick — incremented by the host (MainScreen) when the Home tab
    // is re-tapped. iOS UITabBar gives this for free; Compose NavigationBar does not.
    scrollToTopTick: Int = 0,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = remember { HapticManager(context) }
    val coroutineScope = rememberCoroutineScope()

    // Hilt entry point for SoundManager (parity with iOS HomeView header speaker button).
    val soundManager = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            HomeSoundEntryPoint::class.java,
        ).soundManager()
    }
    val isSoundEnabled by produceState(initialValue = false, key1 = soundManager) {
        soundManager.isSoundEnabledFlow().collect { v -> value = v }
    }

    // Profile switcher bottom sheet (parity with iOS HomeView .sheet ProfileSwitcherSheet).
    // Tapping the gold avatar opens this sheet INSTEAD of switching to the Profile tab.
    var showProfileSwitcher by remember { mutableStateOf(false) }
    if (showProfileSwitcher) {
        ProfileSwitcherSheet(
            onDismiss = { showProfileSwitcher = false },
            onOpenFullProfile = {
                showProfileSwitcher = false
                onNavigateToProfile()
            },
        )
    }

    LaunchedEffect(Unit) { viewModel.loadHomeData() }

    // Forward one-shot askDestiny events to the host (MainScreen routes them to the
    // Chat tab with the prompt prefilled — mirrors iOS pendingQuestion handoff).
    LaunchedEffect(viewModel) {
        viewModel.askDestinyEvents.collect { prompt -> onAskDestiny(prompt) }
    }

    // Parity with iOS .onChange(of: scenePhase) { if .active ... }: observe app-level
    // foreground events and let the ViewModel decide whether to refresh (day rollover etc).
    DisposableEffect(Unit) {
        val owner = ProcessLifecycleOwner.get()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onAppForeground()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    // Request POST_NOTIFICATIONS at runtime on Android 13+ (parity with iOS requestPermission)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* result handled implicitly; FCM token registration occurs separately */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val greeting = timeBasedGreeting()

    // Life area questions bottom sheet
    state.selectedLifeArea?.let { lifeArea ->
        LifeAreaQuestionsSheet(
            lifeArea = lifeArea,
            onQuestionTap = { question ->
                haptic.light()
                viewModel.onLifeAreaQuestionTapped(lifeArea, question)
            },
            onDismiss = { viewModel.dismissLifeArea() },
        )
    }

    // R2-H28: brief popup shown before the full questions sheet
    state.briefLifeArea?.let { lifeArea ->
        LifeAreaBriefPopup(
            lifeArea = lifeArea,
            onConfirm = {
                haptic.light()
                viewModel.confirmLifeAreaBrief()
            },
            onDismiss = { viewModel.dismissLifeAreaBrief() },
        )
    }

    // Yoga detail popup (parity with iOS YogaDetailPopup)
    state.selectedYoga?.let { yoga ->
        YogaDetailPopup(
            yoga = yoga,
            onAskMore = {
                haptic.light()
                viewModel.onYogaAskMore(yoga)
            },
            onDismiss = { viewModel.dismissYoga() },
        )
    }

    CosmicBackground(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            HomeHeader(
                displayName = state.displayName,
                greeting = greeting,
                unreadCount = state.unreadCount,
                isSoundEnabled = isSoundEnabled,
                onSoundToggle = {
                    haptic.light()
                    coroutineScope.launch { soundManager.toggleSound() }
                },
                onHistoryTap = {
                    haptic.light()
                    onNavigateToHistory()
                },
                onNotificationsTap = {
                    haptic.light()
                    onNavigateToNotifications()
                },
                onProfileTap = {
                    haptic.light()
                    // Parity with iOS HomeView avatar tap → presents ProfileView() in .sheet.
                    // Tap opens the full ProfileScreen; long-press opens the bottom-sheet
                    // switcher (Android-only affordance — see HomeHeader).
                    onNavigateToProfile()
                },
                onProfileLongPress = {
                    haptic.light()
                    showProfileSwitcher = true
                },
            )

            // OfflineBanner — parity with iOS OfflineBanner() above content.
            // Renders only when network is unreachable; ties into NetworkMonitor.isOnline.
            if (!isOnline) {
                OfflineBanner()
            }

            // Pull-to-refresh wrapper (parity with iOS .refreshable on ScrollView).
            // Scroll-to-top: a hoisted LazyListState lets the host trigger a smooth
            // scrollToItem(0) when the Home tab is re-tapped (parity with iOS UITabBar).
            val listState = rememberLazyListState()
            LaunchedEffect(scrollToTopTick) {
                if (scrollToTopTick > 0) {
                    listState.animateScrollToItem(0)
                }
            }
            PullToRefreshBox(
                isRefreshing = state.isLoading || state.isRichDataLoading,
                onRefresh = { viewModel.loadHomeData() },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("home_scroll_view"),
                    contentPadding = PaddingValues(bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                // Error banner with retry (parity with iOS errorMessage banner)
                state.errorMessage?.let { msg ->
                    item {
                        ErrorBanner(
                            message = msg,
                            onRetry = { viewModel.loadHomeData() },
                            onDismiss = { viewModel.dismissError() },
                        )
                    }
                }

                // Life area orbs — parity with iOS storyOrbsSection (HomeView.swift:744-794):
                // greeting + name on top, ascendant subtitle, then horizontal orbs, then
                // "tap to explore" guide text. The header strip above intentionally
                // contains only the logo + side buttons, not the greeting.
                item {
                    Spacer(Modifier.height(4.dp))
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = stringResource(
                                R.string.home_greeting_with_name,
                                greeting,
                                state.displayName.ifBlank { stringResource(R.string.home_welcome) },
                            ),
                            style = MaterialTheme.typography.headlineSmall,
                            fontFamily = CanelaFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = Gold,
                        )
                        if (state.ascendantSign.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = stringResource(
                                    R.string.home_ascendant_subtitle,
                                    localizedSignName(state.ascendantSign),
                                ),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = CreamDim,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    LifeAreaOrbs(
                        lifeAreas = state.lifeAreas,
                        onAreaTap = { area ->
                            haptic.light()
                            viewModel.selectLifeArea(area)
                        },
                        onNavigateToCharts = {
                            haptic.light()
                            onNavigateToCharts()
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.home_tap_to_explore_day),
                        fontSize = 11.sp,
                        fontStyle = FontStyle.Italic,
                        color = CreamDim.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // What's in my mind — parity with iOS whatsInMyMindSection
                // (HomeView.swift:826-862): 2×2 golden-bordered card grid. Renders
                // unconditionally with a 4-question fallback when the API list is
                // empty so the section never disappears (iOS HomeView.swift:834-836
                // parity).
                item {
                    val fallback = listOf(
                        stringResource(R.string.home_default_q_career),
                        stringResource(R.string.home_default_q_love),
                        stringResource(R.string.home_default_q_finance),
                        stringResource(R.string.home_default_q_health),
                    )
                    val questions = state.suggestedQuestions.take(4).ifEmpty { fallback }
                    WhatsInMyMindGrid(
                        questions = questions,
                        onQuestionTap = { q ->
                            haptic.light()
                            viewModel.onSuggestedQuestionTapped(q)
                        },
                    )
                }

                // Daily Insight card — DISABLED for iOS parity. iOS HomeView.swift:532-623
                // defines insightHeroSection but does NOT include it in the body VStack
                // (the section is present-but-disconnected). Hiding here so Android matches
                // the live iOS surface.
                // if (state.dailyInsight != null) {
                //     item {
                //         InsightCard(insight = state.dailyInsight.orEmpty())
                //     }
                // }

                // Dasha insight card
                if (state.dashaInfo != null) {
                    item {
                        state.dashaInfo?.let {
                            DashaInsightCard(
                                dashaInfo = it,
                                onClick = {
                                    haptic.light()
                                    viewModel.onDashaTapped()
                                },
                            )
                        }
                    }
                }

                // Transit alerts (horizontal scroll)
                if (state.transits.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.home_current_transits),
                            style = MaterialTheme.typography.labelLarge,
                            color = Gold.copy(alpha = 0.7f),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        TransitAlertsRow(
                            transits = state.transits,
                            onTransitTap = { transit ->
                                haptic.light()
                                viewModel.onTransitTapped(transit)
                            },
                        )
                    }
                }

                // Yoga highlights
                if (state.yogas.isNotEmpty()) {
                    item {
                        YogaHighlightRow(
                            yogas = state.yogas,
                            selectedFilter = state.yogaFilter,
                            onFilterSelected = { filter ->
                                haptic.light()
                                viewModel.setYogaFilter(filter)
                            },
                            onYogaClick = { yoga ->
                                haptic.light()
                                viewModel.selectYoga(yoga)
                            },
                        )
                    }
                }

                // Dosha status chips — DISABLED for iOS parity. iOS HomeView.swift:218
                // explicitly comments out `doshaStatusSection // Removed per user request`.
                // Hiding here so Android matches the live iOS surface.
                // if (state.doshas.hasMangalDosha || state.doshas.hasKalasarpa) {
                //     item {
                //         DoshaStatusRow(doshas = state.doshas)
                //     }
                // }

                if (state.isLoading || state.isRichDataLoading) {
                    item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Gold, modifier = Modifier.size(24.dp))
                        }
                    }
                }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HomeHeader(
    displayName: String,
    greeting: String,
    unreadCount: Int,
    isSoundEnabled: Boolean,
    onSoundToggle: () -> Unit,
    onHistoryTap: () -> Unit,
    onNotificationsTap: () -> Unit,
    onProfileTap: () -> Unit,
    onProfileLongPress: () -> Unit,
) {
    // Parity with iOS HomeView.headerSection (HomeView.swift:414-528): a ZStack/Box with
    // the centered destiny_home logo, history button absolute-left, notifications +
    // gold-circle profile avatar absolute-right. The greeting + name moves to the
    // story-orbs section below to match iOS storyOrbsSection (HomeView.swift:746-758).
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // Centered logo
        Image(
            painter = painterResource(id = R.drawable.destiny_home),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .height(32.dp)
                .align(Alignment.Center),
        )
        // Left/right rows
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // History button (left)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .border(1.dp, Gold.copy(alpha = 0.3f), CircleShape)
                    .clickable(onClick = onHistoryTap),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = stringResource(R.string.home_history_cd),
                    tint = Gold,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.weight(1f))

            // Bell + profile (right)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Sound toggle button — parity with iOS HomeView showSoundToggle.
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .border(1.dp, Gold.copy(alpha = 0.3f), CircleShape)
                        .clickable(onClick = onSoundToggle)
                        .testTag("home_sound_toggle"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isSoundEnabled) {
                            Icons.AutoMirrored.Filled.VolumeUp
                        } else {
                            Icons.AutoMirrored.Filled.VolumeOff
                        },
                        contentDescription = stringResource(
                            if (isSoundEnabled) R.string.home_sound_on_cd else R.string.home_sound_off_cd,
                        ),
                        tint = Gold,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Box(contentAlignment = Alignment.TopEnd) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .border(1.dp, Gold.copy(alpha = 0.3f), CircleShape)
                            .clickable(onClick = onNotificationsTap),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Notifications,
                            contentDescription = stringResource(R.string.home_notifications_cd),
                            tint = Gold,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    if (unreadCount > 0) {
                        val badgeText = if (unreadCount > 99) {
                            stringResource(R.string.home_unread_badge_overflow)
                        } else {
                            unreadCount.toString()
                        }
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp, end = 0.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE53E3E))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = badgeText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }
                }

                // Profile avatar — solid gold circle with initials (iOS parity).
                // Tap opens the full ProfileScreen (parity with iOS .sheet ProfileView).
                // Long-press opens the bottom-sheet ProfileSwitcher (Android-only affordance).
                val initials = displayName.split(" ")
                    .filter { it.isNotEmpty() }
                    .take(2)
                    .joinToString("") { it.first().uppercase() }
                    .ifEmpty { "?" }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Gold)
                        .combinedClickable(
                            onClick = onProfileTap,
                            onLongClick = onProfileLongPress,
                        )
                        .testTag("home_profile_avatar"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = initials,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(red = 0.15f, green = 0.15f, blue = 0.2f),
                    )
                }
            }
        }
    }
}

@Composable
private fun LifeAreaOrbs(
    lifeAreas: List<HomeLifeArea>,
    onAreaTap: (HomeLifeArea) -> Unit,
    onNavigateToCharts: () -> Unit,
) {
    // Fall back to static list if state is still empty on first render
    val areas = lifeAreas.ifEmpty {
        listOf(
            HomeLifeArea("Wealth", "💰", emptyList()),
            HomeLifeArea("Love", "❤️", emptyList()),
            HomeLifeArea("Career", "💼", emptyList()),
            HomeLifeArea("Health", "🏥", emptyList()),
            HomeLifeArea("Family", "👨‍👩‍👧", emptyList()),
            HomeLifeArea("Education", "🎓", emptyList()),
            HomeLifeArea("Spiritual", "🕉️", emptyList()),
            HomeLifeArea("Destiny", "✦", emptyList()),
        )
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(areas) { area ->
            // Parity with iOS StoryOrbView: ring color and a status dot are driven by
            // life-area status. Without this, every orb looks identical regardless of
            // whether the area is Good (Positive), Steady (Neutral) or Caution today.
            val (ringColor, dotColor) = when (area.status) {
                LifeAreaStatus.Positive -> Color(0xFF48BB78) to Color(0xFF48BB78) // green
                LifeAreaStatus.Caution -> Color(0xFFED8936) to Color(0xFFED8936)  // amber
                LifeAreaStatus.Neutral -> Gold.copy(alpha = 0.45f) to Color.Transparent
            }
            // Map life-area name → custom yoga drawable (iOS parity). Falls back to
            // emoji glyph when no asset is available for that area.
            val isActive = area.status != LifeAreaStatus.Neutral
            val drawableRes: Int? = when (area.name.lowercase()) {
                "career" -> if (isActive) R.drawable.ic_yoga_career_active else R.drawable.ic_yoga_career_inactive
                "love", "relationship" -> if (isActive) R.drawable.ic_yoga_love_active else R.drawable.ic_yoga_love_inactive
                "wealth", "finance", "investment" -> if (isActive) R.drawable.ic_yoga_wealth_active else R.drawable.ic_yoga_wealth_inactive
                "health" -> if (isActive) R.drawable.ic_yoga_health_active else R.drawable.ic_yoga_health_inactive
                "family" -> if (isActive) R.drawable.ic_yoga_family_active else R.drawable.ic_yoga_family_inactive
                "spiritual", "education", "wisdom" -> if (isActive) R.drawable.ic_yoga_wisdom_active else R.drawable.ic_yoga_wisdom_inactive
                "destiny", "challenges", "sudden events", "sudden_events", "sudden" ->
                    if (isActive) R.drawable.ic_yoga_challenges_active else R.drawable.ic_yoga_challenges_inactive
                else -> null
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onAreaTap(area) }
                    .testTag("home_life_area_orb_${area.name.lowercase()}"),
            ) {
                Box(contentAlignment = Alignment.TopEnd) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(NavySurface, NavyVariant),
                                )
                            )
                            .border(1.5.dp, ringColor, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (drawableRes != null) {
                            Icon(
                                painter = painterResource(drawableRes),
                                contentDescription = area.name,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(34.dp),
                            )
                        } else {
                            Text(text = area.emoji, fontSize = 26.sp)
                        }
                    }
                    if (dotColor != Color.Transparent) {
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp, end = 2.dp)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                                .border(1.dp, Color(0xFF0D0826), CircleShape),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = area.name,
                    fontSize = 11.sp,
                    color = CreamDim,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun DashaInsightCard(dashaInfo: HomeDashaInfo, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(Color(0xFF1A1040), Color(0xFF0D0826)))
                )
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_dasha_period),
                        style = MaterialTheme.typography.labelLarge,
                        color = Gold,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DashaChip(label = stringResource(R.string.home_dasha_maha), value = dashaInfo.mahadasha)
                        DashaChip(label = stringResource(R.string.home_dasha_antar), value = dashaInfo.antardasha)
                    }
                    if (dashaInfo.endsAt.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.home_dasha_period_ends, dashaInfo.endsAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = CreamDim,
                        )
                    }
                    // Theme row — parity with iOS DashaInsightCard.theatermasks.fill row.
                    if (!dashaInfo.theme.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.TheaterComedy,
                                contentDescription = null,
                                tint = Gold.copy(alpha = 0.8f),
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.home_dasha_theme_label) + ": " + dashaInfo.theme,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = GoldLight,
                            )
                        }
                    }
                    // Meaning paragraph — parity with iOS optional meaning text.
                    if (!dashaInfo.meaning.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = dashaInfo.meaning!!,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            lineHeight = 17.sp,
                        )
                    }
                }
                // Quality badge top-right + progress ring stacked.
                Column(horizontalAlignment = Alignment.End) {
                    if (!dashaInfo.quality.isNullOrBlank()) {
                        val qColor = when (dashaInfo.quality!!.lowercase()) {
                            "good" -> Color(0xFF48BB78)
                            "caution" -> Color(0xFFED8936)
                            "steady" -> Gold
                            else -> Gold
                        }
                        val qLabel = when (dashaInfo.quality!!.lowercase()) {
                            "good" -> stringResource(R.string.status_good)
                            "caution" -> stringResource(R.string.status_caution)
                            "steady" -> stringResource(R.string.status_steady)
                            else -> dashaInfo.quality!!
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(qColor)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = qLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    // Parity with iOS DashaProgressWidget — circular ring shows how far the
                    // current dasha period has elapsed (R2-H13/14). Only renders when both
                    // start and end are present in the model.
                    if (!dashaInfo.periodStartIso.isNullOrBlank() && !dashaInfo.periodEndIso.isNullOrBlank()) {
                        val progress = computeDashaProgress(
                            dashaInfo.periodStartIso,
                            dashaInfo.periodEndIso,
                        )
                        Box(contentAlignment = Alignment.Center) {
                            DashaProgressRing(progress = progress, sizeDp = 44.dp)
                            Text(
                                text = "${(progress * 100f).toInt()}%",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Gold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashaChip(label: String, value: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Gold.copy(alpha = 0.12f))
            .border(0.5.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, fontSize = 10.sp, color = CreamDim)
            Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Gold)
        }
    }
}

@Composable
private fun TransitAlertsRow(
    transits: List<HomeTransit>,
    onTransitTap: (HomeTransit) -> Unit = {},
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(transits) { transit ->
            TransitAlertCard(transit = transit, onClick = { onTransitTap(transit) })
        }
    }
}

@Composable
private fun TransitAlertCard(transit: HomeTransit, onClick: () -> Unit = {}) {
    val chipColor = if (transit.isFavorable) Color(0xFF48BB78) else Color(0xFFFC8181)
    // Status-coloured ring colour (parity with iOS TransitOrbView.borderColor switch).
    val ringColor = when (transit.badgeType.lowercase()) {
        "positive" -> Color(0xFF48BB78)
        "caution", "warning" -> Color(0xFFFC8181)
        "neutral" -> Color(0xFFED8936)
        else -> Gold
    }
    val planetDrawable = planetDrawableFor(transit.planet)
    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
            .testTag("transit_card_${transit.planet.lowercase()}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(0.5.dp, Gold.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Planet orb (parity with iOS TransitOrbView): 64dp circle, status ring,
            // planet image inside, planet-name capsule overlay.
            Box(
                modifier = Modifier.size(68.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (planetDrawable != null) {
                    Image(
                        painter = painterResource(planetDrawable),
                        contentDescription = transit.planet,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(NavyVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = transit.planet.take(2),
                            color = Gold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                    }
                }
                // Status-coloured stroke ring around the planet.
                Canvas(modifier = Modifier.size(64.dp)) {
                    drawArc(
                        color = ringColor.copy(alpha = 0.55f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
                // Planet-name capsule overlay (centered on the orb).
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF14141C).copy(alpha = 0.85f))
                        .border(0.5.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = transit.planet,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Gold,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.home_transit_in_sign, transit.sign),
                fontSize = 12.sp,
                color = CreamDim,
            )
            if (transit.house > 0) {
                Text(
                    text = stringResource(R.string.home_transit_house_label, transit.house),
                    fontSize = 10.sp,
                    color = CreamDim.copy(alpha = 0.8f),
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(chipColor.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(
                    text = transit.influence,
                    fontSize = 10.sp,
                    color = chipColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (transit.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = transit.description,
                    fontSize = 10.sp,
                    color = CreamDim.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Maps a planet name (Sun/Moon/Mars/...) to its planet_<name>.png drawable.
 * Returns null when the asset is missing so the caller can fall back to a textual
 * placeholder. Mirrors iOS TransitOrbView.planetImageName.
 */
private fun planetDrawableFor(planet: String): Int? {
    return when (planet.trim().lowercase()) {
        "sun" -> R.drawable.planet_sun
        "moon" -> R.drawable.planet_moon
        "mars" -> R.drawable.planet_mars
        "mercury" -> R.drawable.planet_mercury
        "jupiter" -> R.drawable.planet_jupiter
        "venus" -> R.drawable.planet_venus
        "saturn" -> R.drawable.planet_saturn
        "rahu" -> R.drawable.planet_rahu
        "ketu" -> R.drawable.planet_ketu
        else -> null
    }
}

@Composable
private fun YogaHighlightRow(
    yogas: List<HomeYoga>,
    selectedFilter: YogaFilter,
    onFilterSelected: (YogaFilter) -> Unit,
    onYogaClick: (HomeYoga) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(R.string.home_active_yogas),
            style = MaterialTheme.typography.labelLarge,
            color = Gold.copy(alpha = 0.7f),
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        // Filter chips row (parity with iOS YogaHighlightCard.swift:62-87 filterButton row).
        // 11 categories matching iOS FilterType enum.
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.testTag("home_yoga_filter_row"),
        ) {
            items(YogaFilter.values().toList()) { filter ->
                YogaFilterChip(
                    filter = filter,
                    selected = filter == selectedFilter,
                    onClick = { onFilterSelected(filter) },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        val filteredYogas = if (selectedFilter == YogaFilter.All) {
            yogas
        } else {
            yogas.filter { it.matchesFilter(selectedFilter) }
        }
        if (filteredYogas.isEmpty()) {
            Text(
                text = stringResource(R.string.home_yoga_no_results),
                fontSize = 12.sp,
                fontStyle = FontStyle.Italic,
                color = CreamDim.copy(alpha = 0.7f),
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filteredYogas.size) { index ->
                    val yoga = filteredYogas[index]
                    PremiumYogaCard(
                        yoga = yoga,
                        index = index,
                        onClick = { onYogaClick(yoga) },
                    )
                }
            }
        }
    }
}

@Composable
private fun YogaFilterChip(
    filter: YogaFilter,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val labelRes = when (filter) {
        YogaFilter.All -> R.string.home_yoga_filter_all
        YogaFilter.Wealth -> R.string.home_yoga_filter_wealth
        YogaFilter.Career -> R.string.home_yoga_filter_career
        YogaFilter.Love -> R.string.home_yoga_filter_love
        YogaFilter.Health -> R.string.home_yoga_filter_health
        YogaFilter.Family -> R.string.home_yoga_filter_family
        YogaFilter.Education -> R.string.home_yoga_filter_education
        YogaFilter.Spiritual -> R.string.home_yoga_filter_spiritual
        YogaFilter.Foundation -> R.string.home_yoga_filter_foundation
        YogaFilter.Personality -> R.string.home_yoga_filter_personality
        YogaFilter.Special -> R.string.home_yoga_filter_special
        else -> R.string.home_yoga_filter_all // legacy Raja/Dhana fallback
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) Gold.copy(alpha = 0.22f) else NavySurface,
            )
            .border(
                0.5.dp,
                if (selected) Gold else Gold.copy(alpha = 0.25f),
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .testTag("home_yoga_filter_${filter.name.lowercase()}")
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = stringResource(labelRes),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) Gold else CreamDim,
        )
    }
}

/**
 * Parity with iOS PremiumYogaCard (YogaHighlightCard.swift) — 170dp card with status
 * badge, dosha icon, planets, houses, divider, gold border, arrow CTA.
 */
@Composable
private fun PremiumYogaCard(
    yoga: HomeYoga,
    index: Int,
    onClick: () -> Unit,
) {
    val statusLabel = when (yoga.status.lowercase()) {
        "active", "a" -> stringResource(R.string.yoga_status_active)
        "reduced", "r" -> stringResource(R.string.yoga_status_reduced)
        "cancelled", "canceled", "c" -> stringResource(R.string.yoga_status_cancelled)
        else -> if (yoga.isActive) stringResource(R.string.yoga_status_active) else stringResource(R.string.yoga_status_inactive)
    }
    val statusColor = when (yoga.status.lowercase()) {
        "active", "a" -> Color(0xFF48BB78)
        "reduced", "r" -> Color(0xFFED8936)
        "cancelled", "canceled", "c" -> Color(0xFFFC8181)
        else -> Gold.copy(alpha = 0.7f)
    }
    Box(
        modifier = Modifier
            .width(170.dp)
            .heightIn(min = 170.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(NavySurface)
            .border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("yoga_card_$index")
            .padding(12.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (yoga.isDosha) Icons.Filled.Warning else Icons.Filled.Star,
                    contentDescription = null,
                    tint = if (yoga.isDosha) Color(0xFFFC8181) else Gold,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = statusLabel,
                        fontSize = 9.sp,
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = yoga.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = CreamText,
                fontFamily = CanelaFontFamily,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (yoga.planets.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.home_yoga_planets_label, yoga.planets),
                    fontSize = 10.sp,
                    color = CreamDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (yoga.houses.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.home_yoga_houses_label, yoga.houses),
                    fontSize = 10.sp,
                    color = CreamDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(Gold.copy(alpha = 0.2f)),
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (yoga.strength > 0) {
                    Text(
                        text = stringResource(R.string.home_yoga_strength_label, yoga.strength),
                        fontSize = 10.sp,
                        color = Gold,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "→",
                    color = Gold,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * Maps a HomeYoga's category to the YogaFilter enum. Mirrors iOS FilterType
 * matching by category string. Categories used by the server include
 * Wealth, Career, Love, Health, Family, Education, Spiritual, Foundation,
 * Personality, Special, Other, Dosha.
 */
private fun HomeYoga.matchesFilter(filter: YogaFilter): Boolean {
    val cat = category.lowercase()
    return when (filter) {
        YogaFilter.All -> true
        YogaFilter.Wealth -> cat.contains("wealth")
        YogaFilter.Career -> cat.contains("career")
        YogaFilter.Love -> cat.contains("love") || cat.contains("relationship")
        YogaFilter.Health -> cat.contains("health")
        YogaFilter.Family -> cat.contains("family")
        YogaFilter.Education -> cat.contains("education")
        YogaFilter.Spiritual -> cat.contains("spiritual")
        YogaFilter.Foundation -> cat.contains("foundation") || cat.contains("basic")
        YogaFilter.Personality -> cat.contains("personality")
        YogaFilter.Special -> cat.contains("special")
        else -> true // legacy Raja/Dhana = treat as no-filter
    }
}

@Composable
private fun DoshaStatusRow(doshas: HomeDoshaStatus) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(R.string.home_dosha_alerts),
            style = MaterialTheme.typography.labelLarge,
            color = Gold.copy(alpha = 0.7f),
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (doshas.hasMangalDosha) {
                DoshaChip(
                    label = stringResource(R.string.home_mangal_dosha),
                    severity = doshas.mangalSeverity,
                    color = Color(0xFFED8936),
                )
            }
            if (doshas.hasKalasarpa) {
                DoshaChip(
                    label = stringResource(R.string.home_kala_sarpa),
                    severity = doshas.kalasarpaType,
                    color = Color(0xFFFC8181),
                )
            }
        }
    }
}

@Composable
private fun DoshaChip(label: String, severity: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .border(0.5.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Column {
            Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
            if (severity.isNotEmpty()) {
                Text(text = severity, fontSize = 10.sp, color = color.copy(alpha = 0.8f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LifeAreaQuestionsSheet(
    lifeArea: HomeLifeArea,
    onQuestionTap: (String) -> Unit = {},
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0D0826),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(36.dp, 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Gold.copy(alpha = 0.3f)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = lifeArea.emoji, fontSize = 28.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = lifeArea.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CanelaFontFamily,
                    color = CreamText,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.home_tap_a_question),
                fontSize = 13.sp,
                color = CreamDim,
            )
            Spacer(Modifier.height(16.dp))
            lifeArea.questions.forEach { question ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(NavySurface)
                        .border(0.5.dp, Gold.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .clickable { onQuestionTap(question) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "→", color = Gold, fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = question,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CreamText,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightCard(insight: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(NavySurface, NavyVariant)
                    )
                )
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = stringResource(R.string.home_todays_insight),
                    style = MaterialTheme.typography.labelLarge,
                    color = Gold,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = insight,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CreamText,
                    lineHeight = 22.sp,
                )
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    emoji: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = emoji, fontSize = 28.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = CreamDim,
            )
        }
    }
}

@Composable
private fun SuggestedQuestion(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(NavySurface)
            .border(0.5.dp, Gold.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "→", color = Gold, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = CreamText,
            )
        }
    }
}

@Composable
private fun timeBasedGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    // Parity with iOS HomeView.timeBasedGreeting: 0..<12 morning, 12..<18 afternoon,
    // default evening (no separate "good night" bucket).
    return when {
        hour < 12 -> stringResource(R.string.good_morning)
        hour < 18 -> stringResource(R.string.good_afternoon)
        else -> stringResource(R.string.good_evening)
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3D1F1F)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(0.5.dp, Color(0xFFFC8181).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(14.dp),
        ) {
            Text(
                text = stringResource(R.string.home_error_title),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFC8181),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                fontSize = 13.sp,
                color = CreamText,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Gold.copy(alpha = 0.18f))
                        .border(0.5.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable(onClick = onRetry)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.home_error_retry),
                        color = Gold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(android.R.string.cancel), color = CreamDim, fontSize = 12.sp)
                }
            }
        }
    }
}

// R2-H28: brief popup shown before the full life-area questions sheet
@Composable
private fun LifeAreaBriefPopup(
    lifeArea: HomeLifeArea,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0D0826))
                .border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .padding(20.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = lifeArea.emoji, fontSize = 28.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = lifeArea.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = CanelaFontFamily,
                            color = CreamText,
                        )
                        // Status text — parity with iOS LifeAreaBriefPopup.swift:35-45
                        // (Good/Steady/Caution colored bold under the name).
                        val (statusText, statusColor) = when (lifeArea.status) {
                            LifeAreaStatus.Positive -> stringResource(R.string.status_good) to Color(0xFF48BB78)
                            LifeAreaStatus.Caution -> stringResource(R.string.status_caution) to Color(0xFFED8936)
                            LifeAreaStatus.Neutral -> stringResource(R.string.status_steady) to Gold
                        }
                        Text(
                            text = statusText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            modifier = Modifier.testTag("home_brief_status_text"),
                        )
                    }
                    // X close button (parity with iOS LifeAreaBriefPopup.swift:48-55)
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("home_brief_close_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.home_close_cd),
                            tint = CreamDim,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                if (lifeArea.briefDescription.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = lifeArea.briefDescription,
                        fontSize = 13.sp,
                        color = CreamDim,
                        lineHeight = 18.sp,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.home_close), color = CreamDim, fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Gold.copy(alpha = 0.18f))
                            .border(0.5.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .clickable(onClick = onConfirm)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.home_ask_more),
                            color = Gold,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

// Yoga detail popup — parity with iOS YogaDetailPopup. Shows yoga name, category, status,
// strength and description so users can drill into a chip instead of seeing read-only text.
@Composable
private fun YogaDetailPopup(
    yoga: HomeYoga,
    onAskMore: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val statusLabel = when (yoga.status.lowercase()) {
        "active", "a" -> stringResource(R.string.yoga_status_active)
        "reduced", "r" -> stringResource(R.string.yoga_status_reduced)
        "cancelled", "canceled", "c" -> stringResource(R.string.yoga_status_cancelled)
        else -> yoga.status.ifBlank { if (yoga.isActive) stringResource(R.string.yoga_status_active) else stringResource(R.string.yoga_status_inactive) }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0D0826))
                .border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .padding(20.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = yoga.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CanelaFontFamily,
                        color = CreamText,
                        modifier = Modifier.weight(1f),
                    )
                    // X close button (parity with iOS YogaDetailPopup.swift:80-87)
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("home_yoga_close_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.home_close_cd),
                            tint = CreamDim,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Gold.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = yoga.category,
                            fontSize = 11.sp,
                            color = Gold,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CreamDim.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = statusLabel,
                            fontSize = 11.sp,
                            color = CreamDim,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                if (yoga.description.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = yoga.description,
                        fontSize = 13.sp,
                        color = CreamText,
                        lineHeight = 19.sp,
                    )
                }
                // Extra detail rows — parity with iOS YogaDetailPopup
                // (planets, houses, formation, strength, outcome, reduction reason).
                if (yoga.planets.isNotBlank()) {
                    YogaDetailRow(
                        label = stringResource(R.string.planets_label),
                        value = yoga.planets,
                    )
                }
                if (yoga.houses.isNotBlank()) {
                    YogaDetailRow(
                        label = stringResource(R.string.houses_label),
                        value = yoga.houses.split(",").joinToString(", ") { "H" + it.trim() },
                    )
                }
                if (yoga.formation.isNotBlank()) {
                    YogaDetailRow(
                        label = stringResource(R.string.home_yoga_formation),
                        value = yoga.formation,
                    )
                }
                if (yoga.strength > 0) {
                    YogaDetailRow(
                        label = stringResource(R.string.home_yoga_strength),
                        value = "${yoga.strength}%",
                    )
                }
                if (yoga.outcome.isNotBlank()) {
                    YogaDetailRow(
                        label = stringResource(R.string.home_yoga_outcome),
                        value = yoga.outcome,
                    )
                }
                if (yoga.reductionReason.isNotBlank()) {
                    YogaDetailRow(
                        label = stringResource(R.string.home_yoga_reduction_reason),
                        value = yoga.reductionReason,
                    )
                }
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.home_close), color = CreamDim, fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Gold.copy(alpha = 0.18f))
                            .border(0.5.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .clickable(onClick = onAskMore)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.home_ask_more),
                            color = Gold,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// What's-in-my-mind 2×2 grid (parity with iOS HomeView whatsInMyMindSection)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 2×2 grid of QuickQuestionCard. iOS HomeView.swift:838-861 chunks the question
 * list into pairs and renders each pair in an HStack with weight=1f. Tapping a
 * card emits the raw question text via [onQuestionTap], which maps to
 * onQuestionSelected on iOS.
 */
@Composable
private fun WhatsInMyMindGrid(
    questions: List<String>,
    onQuestionTap: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(R.string.home_what_in_my_mind),
            style = MaterialTheme.typography.titleMedium,
            fontFamily = CanelaFontFamily,
            fontWeight = FontWeight.SemiBold,
            color = Gold,
        )
        Spacer(Modifier.height(8.dp))
        questions.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { question ->
                    QuickQuestionCard(
                        question = question,
                        onClick = { onQuestionTap(question) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) {
                    // Keep grid balanced when an odd number of questions arrives.
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

/**
 * Mirrors iOS QuickQuestionCard (HomeView.swift:1083-1127): gold-bordered card
 * with the question text and a forward arrow CTA. Tap-friendly via Modifier.clickable.
 */
@Composable
private fun QuickQuestionCard(
    question: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .heightIn(min = 70.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(NavySurface)
            .border(2.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = question,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = CreamText,
                lineHeight = 18.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(GoldLight, Gold))
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "→",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(red = 0.15f, green = 0.15f, blue = 0.2f),
                )
            }
        }
    }
}

/**
 * Maps a sign name (e.g. "Aries", "Aquarius") to the localized string-resource
 * value when available, otherwise returns the input unchanged. Mirrors the
 * iOS HomeView.localizedAscendant lookup.
 */
@Composable
private fun localizedSignName(sign: String): String {
    val resId = when (sign.lowercase()) {
        "aries" -> R.string.sign_ar
        "taurus" -> R.string.sign_ta
        "gemini" -> R.string.sign_ge
        "cancer" -> R.string.sign_ca
        "leo" -> R.string.sign_le
        "virgo" -> R.string.sign_vi
        "libra" -> R.string.sign_li
        "scorpio" -> R.string.sign_sc
        "sagittarius" -> R.string.sign_sg
        "capricorn" -> R.string.sign_cp
        "aquarius" -> R.string.sign_aq
        "pisces" -> R.string.sign_pi
        else -> null
    }
    return if (resId != null) stringResource(resId) else sign
}

/**
 * Circular progress indicator for the Dasha card. Computes a 0..1 fraction from
 * periodStartIso → periodEndIso → today, falling back to 0f if either is
 * missing or unparseable. Mirrors iOS DashaProgressWidget.
 */
@Composable
internal fun DashaProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    sizeDp: androidx.compose.ui.unit.Dp = 36.dp,
) {
    val safe = progress.coerceIn(0f, 1f)
    Canvas(modifier = modifier.size(sizeDp)) {
        val stroke = 4.dp.toPx()
        // Background ring
        drawArc(
            color = Color(0xFF2D1F5E),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = stroke),
        )
        // Foreground arc — gold gradient via solid gold (Compose drawArc has no gradient param)
        drawArc(
            color = Color(0xFFD4AF37),
            startAngle = -90f,
            sweepAngle = 360f * safe,
            useCenter = false,
            style = Stroke(width = stroke),
        )
    }
}

/** Computes 0..1 elapsed fraction between an ISO start/end date pair. */
internal fun computeDashaProgress(startIso: String?, endIso: String?): Float {
    if (startIso.isNullOrBlank() || endIso.isNullOrBlank()) return 0f
    return try {
        val start = OffsetDateTime.parse(startIso, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val end = OffsetDateTime.parse(endIso, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val now = OffsetDateTime.now()
        val total = java.time.Duration.between(start, end).toMillis().coerceAtLeast(1L)
        val elapsed = java.time.Duration.between(start, now).toMillis().coerceAtLeast(0L)
        (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    } catch (e: Exception) {
        0f
    }
}

// ─── OfflineBanner ─────────────────────────────────────────────────────────
// Parity with iOS HomeView OfflineBanner() (NetworkMonitor.swift:53-71). A subtle
// orange capsule with a wifi-slash icon shown above Home content when offline.
@Composable
private fun OfflineBanner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFED8936).copy(alpha = 0.9f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.WifiOff,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = stringResource(R.string.home_offline_message),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ─── ProfileSwitcherSheet ──────────────────────────────────────────────────
// Parity with iOS HomeView .sheet(isPresented: $showProfileSwitcher) {
// ProfileSwitcherSheet() }. Tapping the gold avatar in the header opens this
// bottom-sheet picker instead of switching to the Profile tab. Reuses the
// existing ProfileSwitcher composable (ui/compatibility/ProfileSwitcher.kt) and
// the ProfileSwitcherViewModel that already powers the Compatibility flow.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSwitcherSheet(
    onDismiss: () -> Unit,
    onOpenFullProfile: () -> Unit,
    viewModel: ProfileSwitcherViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val haptic = remember { HapticManager(context) }
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val activeProfileId by viewModel.activeProfileId.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0D0826),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(36.dp, 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Gold.copy(alpha = 0.3f)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.switch_birth_chart),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CanelaFontFamily,
                    color = CreamText,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .clickable {
                            haptic.light()
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.home_profile_switcher_close_cd),
                        tint = CreamDim,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            if (isLoading && profiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Gold, modifier = Modifier.size(28.dp))
                }
            } else {
                profiles.forEach { entry ->
                    val isActive = entry.id == activeProfileId
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isActive) Gold.copy(alpha = 0.15f) else NavySurface
                            )
                            .border(
                                0.5.dp,
                                if (isActive) Gold.copy(alpha = 0.5f) else Gold.copy(alpha = 0.15f),
                                RoundedCornerShape(12.dp),
                            )
                            .clickable {
                                haptic.light()
                                if (!isActive) viewModel.switchProfile(entry.id)
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (entry.isSelf) "★" else "•",
                                color = Gold,
                                fontSize = 14.sp,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = entry.name,
                                color = CreamText,
                                fontSize = 14.sp,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f),
                            )
                            if (isActive) {
                                Text(text = "✓", color = Gold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            // "Manage profiles" jump-off — opens the full ProfileScreen for editing.
            TextButton(
                onClick = {
                    haptic.light()
                    onOpenFullProfile()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.profile_switch_profile),
                    color = Gold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// Hilt EntryPoint that exposes the application-scoped SoundManager to non-Hilt
// composables. Lets HomeScreen wire the header sound toggle without forcing
// SoundManager into HomeViewModel's constructor.
@EntryPoint
@InstallIn(SingletonComponent::class)
interface HomeSoundEntryPoint {
    fun soundManager(): SoundManager
}

/**
 * Labelled key/value row used inside YogaDetailPopup to display planets, houses,
 * formation, strength, outcome and reduction reason. Mirrors iOS YogaDetailPopup
 * row layout (label in muted small caps, value in body cream).
 */
@Composable
private fun YogaDetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = label.uppercase(),
            fontSize = 10.sp,
            color = Gold.copy(alpha = 0.7f),
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = CreamText,
            lineHeight = 18.sp,
        )
    }
}
