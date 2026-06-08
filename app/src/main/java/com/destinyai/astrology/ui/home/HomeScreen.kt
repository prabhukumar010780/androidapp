package com.destinyai.astrology.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import com.destinyai.astrology.ui.components.GoldGradientText
import com.destinyai.astrology.ui.profile.ProfileSwitcherViewModel
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.Features
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

    // Parity with iOS HomeView.onAppear → UIApplication.registerForRemoteNotifications().
    // MainActivity.onCreate already registers once at process start, but this defensive
    // re-register on Home first composition catches the case where the user reinstalled
    // a fresh build without killing the process, or where FCM rotated the token while
    // the app was backgrounded (DestinyFirebaseMessagingService.onNewToken does fire,
    // but the backend POST in FcmTokenManager can fail offline — Home is a good retry
    // point because it's the most-visited surface). Idempotent on the backend side.
    val fcmRegisterScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        try {
            val tokenManager = EntryPointAccessors
                .fromApplication(
                    context.applicationContext,
                    com.destinyai.astrology.ui.notifications.FcmTokenManagerEntryPoint::class.java,
                )
                .fcmTokenManager()
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    if (!token.isNullOrBlank()) {
                        fcmRegisterScope.launch {
                            tokenManager.registerToken(token, com.destinyai.astrology.BuildConfig.VERSION_NAME)
                        }
                    }
                }
        } catch (_: Exception) {
            // FCM may be unavailable on devices without Google Play Services — fail silently.
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
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
            // iOS parity: HomeView uses a plain ScrollView with auto-refresh driven by
            // app-resume + day-rollover (HomeViewModel.refresh()). Material3's
            // PullToRefreshBox over-aggressively interprets every top-of-list downward
            // drag as a refresh pull, which causes a rubber-band stretching feel on
            // every scroll. Replacing it with a plain LazyColumn matches iOS exactly —
            // refresh continues to fire from scenePhase / day-rollover paths. We also
            // disable the system overscroll glow + stretch animation (Android 12+
            // default) so the bounce-back at edges feels iOS-tight instead of
            // rubber-band stretchy.
            @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
            CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("home_scroll_view"),
                    contentPadding = PaddingValues(bottom = 120.dp),
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.TouchApp,
                            contentDescription = null,
                            tint = Gold.copy(alpha = 0.6f),
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = stringResource(R.string.home_tap_to_explore_day),
                            fontSize = 11.sp,
                            fontStyle = FontStyle.Italic,
                            color = CreamDim.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                // What's in my mind — parity with iOS whatsInMyMindSection
                // (HomeView.swift:826-862): 2×2 golden-bordered card grid. Renders
                // unconditionally with a 4-question fallback when the API list is
                // empty so the section never disappears (iOS HomeView.swift:834-836
                // parity).
                item {
                    Spacer(Modifier.height(20.dp))
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
                        Spacer(Modifier.height(20.dp))
                        GoldGradientText(
                            text = stringResource(R.string.home_current_dasha),
                            modifier = Modifier.padding(horizontal = 16.dp),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = CanelaFontFamily,
                        )
                        Spacer(Modifier.height(8.dp))
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
                        Spacer(Modifier.height(20.dp))
                        GoldGradientText(
                            text = stringResource(R.string.home_current_transits),
                            modifier = Modifier.padding(horizontal = 16.dp),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = CanelaFontFamily,
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
                        Spacer(Modifier.height(20.dp))
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
                // iOS gates on AppTheme.Features.showSoundToggle (currently false), so
                // Android renders nothing when the flag is off.
                if (Features.showSoundToggle) {
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

// ─── LifeAreaOrbs (Stories-style) ─────────────────────────────────────────
// Compose port of iOS StoryOrbView (Views/Home/Components/StoryOrbView.swift):
// outer ambient ring → soft glow aura → status-coloured sweep ring → glass
// inner sphere → top-left specular highlight → gold definition ring → filled
// gold material icon → status dot (top-right) → uppercase gold label below.
// Press effect mirrors iOS StoryOrbButtonStyle (scale 0.88, snappy spring).

private val LifeAreaPositive = Color(0xFF26D973) // status green
private val LifeAreaCaution = Color(0xFFEB3830)  // status red
private val OrbInnerNavyDeep = Color(0xFF0F141E) // matches iOS rgb(0.06,0.08,0.12)

@Composable
private fun LifeAreaOrbs(
    lifeAreas: List<HomeLifeArea>,
    onAreaTap: (HomeLifeArea) -> Unit,
    onNavigateToCharts: () -> Unit,
) {
    // Fall back to a fixed 8-area set when state is still empty on first render.
    // Names match the icon switch in [storyOrbIconFor] below.
    val areas = lifeAreas.ifEmpty {
        listOf(
            HomeLifeArea("Career", "💼", emptyList()),
            HomeLifeArea("Relationship", "❤️", emptyList()),
            HomeLifeArea("Finance", "💰", emptyList()),
            HomeLifeArea("Health", "🏥", emptyList()),
            HomeLifeArea("Family", "👨‍👩‍👧", emptyList()),
            HomeLifeArea("Education", "🎓", emptyList()),
            HomeLifeArea("Investment", "📈", emptyList()),
            HomeLifeArea("Sudden Events", "✦", emptyList()),
        )
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(areas) { area ->
            StoryOrb(
                area = area,
                onTap = { onAreaTap(area) },
            )
        }
    }
}

@Composable
private fun StoryOrb(
    area: HomeLifeArea,
    onTap: () -> Unit,
) {
    val orbSize = 72.dp
    val statusColor = when (area.status) {
        LifeAreaStatus.Positive -> LifeAreaPositive
        LifeAreaStatus.Caution -> LifeAreaCaution
        LifeAreaStatus.Neutral -> Gold
    }
    val statusLabel = when (area.status) {
        LifeAreaStatus.Positive -> "Good"
        LifeAreaStatus.Caution -> "Caution"
        LifeAreaStatus.Neutral -> "Steady"
    }
    val icon = storyOrbIconFor(area.name)

    // Press scale (0.88 with snappy spring) — parity with iOS StoryOrbButtonStyle.
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "story_orb_press_scale",
    )
    val haptics = LocalHapticFeedback.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onTap()
                },
            )
            .testTag("home_life_area_orb_${area.name.lowercase().replace(' ', '_')}"),
    ) {
        Box(
            modifier = Modifier.size(orbSize + 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Layers 1–6: drawn together on a single canvas matching the orb
            // bounding box. The status dot + icon ride on top as composables so
            // they get proper antialiasing and Material vector rendering.
            Canvas(modifier = Modifier.size(orbSize + 16.dp)) {
                val totalSize = size.minDimension
                val cx = size.width / 2f
                val cy = size.height / 2f
                val orbPx = orbSize.toPx()
                val ringWidthPx = 2.5.dp.toPx()
                val gapPx = 3.dp.toPx()
                val innerPx = orbPx - (ringWidthPx + gapPx) * 2f

                // 1. Outer ambient ring (status × 0.25 alpha, 2dp stroke, size+8)
                drawCircle(
                    color = statusColor.copy(alpha = 0.25f),
                    radius = (orbPx + 8.dp.toPx()) / 2f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2.dp.toPx()),
                )

                // 2. Glow aura (radial gradient, status colour, size × 1.4)
                //    Compose has no blur in drawScope; we approximate with a
                //    wider radial gradient that fades to transparent.
                val auraRadius = (orbPx * 1.4f) / 2f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            statusColor.copy(alpha = 0.25f),
                            statusColor.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                        center = Offset(cx, cy),
                        radius = auraRadius,
                    ),
                    radius = auraRadius,
                    center = Offset(cx, cy),
                )

                // 3. Status-coloured ring (sweep gradient, 2.5dp stroke)
                val ringColors = when (area.status) {
                    LifeAreaStatus.Positive -> listOf(
                        Color(0xFF26D973),
                        Color(0xFF1AB38C),
                        Color(0xFF33E680),
                        Color(0xFF1ABF66),
                        Color(0xFF26D973),
                    )
                    LifeAreaStatus.Caution -> listOf(
                        Color(0xFFEB3830),
                        Color(0xFFF2802E),
                        Color(0xFFE64D40),
                        Color(0xFFF27326),
                        Color(0xFFEB3830),
                    )
                    LifeAreaStatus.Neutral -> listOf(
                        Gold,
                        Color(0xFFF2BF40),
                        GoldLight,
                        Color(0xFFD9A633),
                        Gold,
                    )
                }
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = ringColors,
                        center = Offset(cx, cy),
                    ),
                    radius = orbPx / 2f,
                    center = Offset(cx, cy),
                    style = Stroke(width = ringWidthPx),
                )

                // 4. Glass inner sphere (radial gradient — dark navy stops)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF293042),
                            Color(0xFF1A1F2A),
                            OrbInnerNavyDeep,
                        ),
                        center = Offset(cx, cy),
                        radius = innerPx * 0.55f,
                    ),
                    radius = innerPx / 2f,
                    center = Offset(cx, cy),
                )

                // 5. Specular highlight (top-left, white-alpha radial)
                //    iOS UnitPoint(0.30, 0.28) on the inner sphere → offset
                //    from centre by (-0.40 * innerR, -0.44 * innerR) approx.
                val specCenter = Offset(
                    x = cx - innerPx * 0.20f,
                    y = cy - innerPx * 0.22f,
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.15f),
                            Color.White.copy(alpha = 0.03f),
                            Color.Transparent,
                        ),
                        center = specCenter,
                        radius = innerPx * 0.40f,
                    ),
                    radius = innerPx * 0.40f,
                    center = specCenter,
                )

                // 6. Gold definition ring (1dp stroke, gold gradient)
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Gold.copy(alpha = 0.35f),
                            Gold.copy(alpha = 0.12f),
                            Gold.copy(alpha = 0.30f),
                        ),
                        start = Offset(cx - innerPx / 2f, cy - innerPx / 2f),
                        end = Offset(cx + innerPx / 2f, cy + innerPx / 2f),
                    ),
                    radius = innerPx / 2f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }

            // 7. Filled material icon — gold gradient brush with soft glow shadow.
            //    Compose Icon has no built-in gradient tint; we paint the icon
            //    with a Color overlay (gold) and lay a thin shadow underneath
            //    via Modifier.shadow on a wrapper Box.
            val iconSize = (orbSize.value * 0.32f).dp
            Box(
                modifier = Modifier
                    .size(iconSize)
                    .shadow(
                        elevation = 4.dp,
                        shape = CircleShape,
                        ambientColor = Gold.copy(alpha = 0.6f),
                        spotColor = Gold.copy(alpha = 0.6f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = area.name,
                    tint = GoldLight,
                    modifier = Modifier.size(iconSize),
                )
            }

            // 8. Status dot (top-right corner — 11dp filled circle, status colour,
            //    2.5dp dark border, glow shadow). iOS positions at offset
            //    (size*0.34, -size*0.34) from the orb centre.
            val dotOffsetPx = with(LocalDensity.current) { (orbSize.value * 0.34f).dp.toPx() }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = dotOffsetPx.roundToInt(),
                            y = (-dotOffsetPx).roundToInt(),
                        )
                    }
                    .size(11.dp)
                    .shadow(
                        elevation = 3.dp,
                        shape = CircleShape,
                        ambientColor = statusColor.copy(alpha = 0.8f),
                        spotColor = statusColor.copy(alpha = 0.8f),
                    )
                    .clip(CircleShape)
                    .background(statusColor)
                    .border(2.5.dp, OrbInnerNavyDeep, CircleShape),
            )
        }

        // 9. Uppercase label below — goldLight, 11sp semibold, letter-spacing 0.3
        Text(
            text = area.name.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = GoldLight,
            letterSpacing = 0.3.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(orbSize + 8.dp),
        )
    }
}

/**
 * Maps an 8-area life-area name to its filled Material icon. Names are matched
 * case-insensitively against common synonyms; unknown names fall back to a star.
 */
private fun storyOrbIconFor(name: String): ImageVector {
    return when (name.trim().lowercase()) {
        "career", "work" -> Icons.Filled.WorkOutline
        "relationship", "love", "romance" -> Icons.Filled.Favorite
        "finance", "wealth", "money" -> Icons.Filled.AccountBalanceWallet
        "health", "wellbeing" -> Icons.Filled.MedicalServices
        "family", "home" -> Icons.Filled.Home
        "education", "learning", "wisdom", "spiritual" -> Icons.AutoMirrored.Filled.MenuBook
        "investment", "investments" -> Icons.AutoMirrored.Filled.TrendingUp
        "sudden events", "sudden_events", "sudden", "destiny", "challenges" -> Icons.Filled.Star
        else -> Icons.Filled.Star
    }
}

@Composable
private fun DashaInsightCard(dashaInfo: HomeDashaInfo, onClick: () -> Unit = {}) {
    // Compose port of iOS DashaInsightCard
    // (ios_app/ios_app/Views/Home/Components/DashaInsightCard.swift):
    //   sparkles icon → period name → Theme row → optional meaning, with a
    //   quality pill (top-right) and gold-gradient arrow circle (bottom-right)
    //   layered as overlays over the main VStack.
    val periodText = localizedDashaPeriod(dashaInfo)
    val qualityKey = dashaInfo.quality?.lowercase().orEmpty()
    val qColor = when (qualityKey) {
        "good" -> Color(0xFF48BB78)
        "steady" -> Gold
        "caution" -> Color(0xFFED8936)
        else -> Gold
    }
    val qLabel = when (qualityKey) {
        "good" -> stringResource(R.string.status_good)
        "steady" -> stringResource(R.string.status_steady)
        "caution" -> stringResource(R.string.status_caution)
        else -> dashaInfo.quality.orEmpty()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Gold.copy(alpha = 0.08f),
                spotColor = Gold.copy(alpha = 0.08f),
            )
            .clip(RoundedCornerShape(16.dp))
            .background(NavySurface)
            .border(2.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 120.dp),
    ) {
        // Main content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            // Header row: gold radial-gradient circle with sparkles icon + period name.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.size(44.dp)) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Gold.copy(alpha = 0.4f),
                                    Gold.copy(alpha = 0.1f),
                                ),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = size.minDimension / 2f,
                            ),
                            radius = size.minDimension / 2f,
                            center = Offset(size.width / 2f, size.height / 2f),
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = Gold,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = periodText,
                    fontSize = 17.sp,
                    fontFamily = CanelaFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                    // Leave room for the quality pill that floats top-right.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // Reserve horizontal space so the period name doesn't run under
                // the absolute-positioned quality pill.
                if (qLabel.isNotEmpty()) {
                    Spacer(Modifier.width(72.dp))
                }
            }
            // Theme row — parity with iOS DashaInsightCard.theatermasks.fill row.
            if (!dashaInfo.theme.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.TheaterComedy,
                        contentDescription = null,
                        tint = Gold.copy(alpha = 0.8f),
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.home_dasha_theme_label) + ": " + dashaInfo.theme,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = GoldLight,
                    )
                }
            }
            // Meaning paragraph — parity with iOS optional meaning text.
            if (!dashaInfo.meaning.isNullOrBlank()) {
                Text(
                    text = dashaInfo.meaning!!,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    lineHeight = 18.sp,
                )
            }
        }
        // Top-right overlay: quality pill (Capsule).
        if (qLabel.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(50))
                    .background(qColor)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = qLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
        // Bottom-right overlay: gold-gradient arrow circle (parity with iOS
        // arrow.forward.circle.fill at bottom-trailing).
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 12.dp, end = 12.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(GoldLight, Gold))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color(red = 0.15f, green = 0.15f, blue = 0.2f),
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

/**
 * Builds the localized period string shown on the Dasha card. iOS sends
 * `dasha.period` as "Saturn-Saturn-Rahu"; the Android model exposes the
 * same data split across [HomeDashaInfo.mahadasha], [HomeDashaInfo.antardasha]
 * and [HomeDashaInfo.upcomingAntardasha] (the latter doubles as the third
 * level when the server populates it). Each segment is run through
 * [localizedPlanetSegment] so the label respects the active language.
 */
@Composable
private fun localizedDashaPeriod(dashaInfo: HomeDashaInfo): String {
    val rawSegments = buildList {
        if (dashaInfo.mahadasha.isNotBlank()) add(dashaInfo.mahadasha)
        if (dashaInfo.antardasha.isNotBlank()) add(dashaInfo.antardasha)
        dashaInfo.upcomingAntardasha?.takeIf { it.isNotBlank() }?.let { add(it) }
    }
    // Resolve each segment in the @Composable scope (stringResource is itself
    // @Composable, so we must collect inside a for-loop rather than a non-
    // composable lambda passed to map/joinToString).
    val localized = mutableListOf<String>()
    for (segment in rawSegments) {
        localized.add(localizedPlanetSegment(segment))
    }
    return localized.joinToString("-")
}

/**
 * Returns the localized name for a planet segment such as "Saturn" or
 * "Rahu". Falls back to the original string for unknown values.
 */
@Composable
private fun localizedPlanetSegment(name: String): String {
    val resId = when (name.trim().lowercase()) {
        "sun" -> R.string.planet_sun
        "moon" -> R.string.planet_moon
        "mars" -> R.string.planet_mars
        "mercury" -> R.string.planet_mercury
        "jupiter" -> R.string.planet_jupiter
        "venus" -> R.string.planet_venus
        "saturn" -> R.string.planet_saturn
        "rahu" -> R.string.planet_rahu
        "ketu" -> R.string.planet_ketu
        else -> null
    }
    return if (resId != null) stringResource(resId) else name.trim()
}

@Composable
private fun TransitAlertsRow(
    transits: List<HomeTransit>,
    onTransitTap: (HomeTransit) -> Unit = {},
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(transits) { transit ->
            TransitAlertCard(transit = transit, onClick = { onTransitTap(transit) })
        }
    }
}

@Composable
private fun TransitAlertCard(transit: HomeTransit, onClick: () -> Unit = {}) {
    // Compose port of iOS TransitOrbView (TransitInfluenceCard.swift:214-275):
    // compact orb + sign + arrow stack. The old card chrome (NavySurface card,
    // gold border, badge chip, description, house label) is intentionally
    // dropped to match the iOS layout.
    val borderColor = when (transit.badgeType.lowercase()) {
        "positive" -> Color(0xFF26D973)
        "caution", "warning" -> Color(0xFFEB3830)
        "neutral" -> Color(0xFFFFB800)
        else -> Gold
    }
    val planetDrawable = planetDrawableFor(transit.planet)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .width(80.dp)
            .clickable(onClick = onClick)
            .testTag("transit_card_${transit.planet.lowercase()}"),
    ) {
        // 68dp orb container — planet image + status ring + floating name capsule,
        // wrapped in a status-tinted shadow (iOS .shadow(borderColor.opacity(0.25))).
        Box(
            modifier = Modifier
                .size(68.dp)
                .shadow(
                    elevation = 5.dp,
                    shape = CircleShape,
                    ambientColor = borderColor.copy(alpha = 0.25f),
                    spotColor = borderColor.copy(alpha = 0.25f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Planet image (60dp, centered).
            if (planetDrawable != null) {
                Image(
                    painter = painterResource(planetDrawable),
                    contentDescription = transit.planet,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Fit,
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
            // Status-coloured stroke ring (68dp circle, 2dp, 0.5α).
            Canvas(modifier = Modifier.size(68.dp)) {
                drawCircle(
                    color = borderColor.copy(alpha = 0.5f),
                    radius = size.minDimension / 2f - 1.dp.toPx(),
                    center = Offset(size.width / 2f, size.height / 2f),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
            // Floating planet-name capsule, centred on the orb.
            Box(
                modifier = Modifier
                    .shadow(
                        elevation = 2.dp,
                        shape = RoundedCornerShape(50),
                        ambientColor = Color.Black.copy(alpha = 0.3f),
                        spotColor = Color.Black.copy(alpha = 0.3f),
                    )
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF141420).copy(alpha = 0.85f))
                    .border(0.5.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = transit.planet,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Gold,
                )
            }
        }
        // Full sign name (e.g. "Taurus", "Capricorn") — 10sp dim cream.
        Text(
            text = localizedSignFromAbbrev(transit.sign),
            fontSize = 10.sp,
            color = CreamDim,
        )
        // Gold-gradient arrow indicator (parity with arrow.forward.circle.fill).
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(GoldLight, Gold))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color(red = 0.15f, green = 0.15f, blue = 0.2f),
                modifier = Modifier.size(10.dp),
            )
        }
    }
}

/**
 * Maps a 2-letter sign abbreviation ("Ar", "Ta", … "Pi") to the full localized
 * sign name via the existing `R.string.sign_*` resources. Falls back to the
 * input string for unknown abbreviations. Mirrors iOS TransitOrbView.localizedSignName.
 */
@Composable
private fun localizedSignFromAbbrev(abbrev: String): String {
    val resId = when (abbrev.trim().lowercase()) {
        "ar" -> R.string.sign_ar
        "ta" -> R.string.sign_ta
        "ge" -> R.string.sign_ge
        "ca" -> R.string.sign_ca
        "le" -> R.string.sign_le
        "vi" -> R.string.sign_vi
        "li" -> R.string.sign_li
        "sc" -> R.string.sign_sc
        "sg" -> R.string.sign_sg
        "cp" -> R.string.sign_cp
        "aq" -> R.string.sign_aq
        "pi" -> R.string.sign_pi
        else -> null
    }
    return if (resId != null) stringResource(resId) else abbrev
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
        GoldGradientText(
            text = stringResource(R.string.yoga_positive_negative),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = CanelaFontFamily,
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
    // Issue P2-8: light haptic on selection.
    val haptics = LocalHapticFeedback.current
    // Issue P2-12: ScaleButtonStyle press feedback (scale 0.92 + alpha 0.85).
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "yoga_chip_press_scale",
    )
    val pressAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "yoga_chip_press_alpha",
    )
    // Issue P2-9: spring animation on selection-driven background/border alpha.
    val bgAlpha by animateFloatAsState(
        targetValue = if (selected) 0.22f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "yoga_chip_bg_alpha",
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.25f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "yoga_chip_border_alpha",
    )
    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                alpha = pressAlpha
            }
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) Gold.copy(alpha = bgAlpha) else NavySurface,
            )
            .border(
                0.5.dp,
                Gold.copy(alpha = borderAlpha),
                RoundedCornerShape(14.dp),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    // Issue P2-8: light haptic feedback on filter-pill selection.
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            )
            .testTag("home_yoga_filter_${filter.name.lowercase()}")
            // Issue P2-10: .isSelected accessibility trait parity with iOS.
            .semantics { this.selected = selected }
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
                // Issue 64: prefix 'H' to each house token on the front face,
                // matching iOS PremiumYogaCard behaviour.
                val housesDisplay = yoga.houses
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString(", ") { "H$it" }
                Text(
                    text = stringResource(R.string.home_yoga_houses_label, housesDisplay),
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
                // Issue 41/65: gold-gradient filled circle arrow CTA matching iOS.
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(GoldLight, Gold))),
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
                // Issue 44: tracked uppercase caption to match iOS (e.g. 'MILD','SEVERE').
                Text(
                    text = severity.uppercase(),
                    fontSize = 10.sp,
                    color = color.copy(alpha = 0.8f),
                    letterSpacing = 1.sp,
                )
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
            // Top row: emoji + name on left, X close button on right.
            // Adds an explicit dismiss affordance alongside the drag-handle —
            // mirrors iOS HomeView LifeAreaQuestionsSheet "Cancel" navigation button.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = lifeArea.emoji, fontSize = 28.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = lifeArea.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CanelaFontFamily,
                    color = CreamText,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("home_life_area_close_button"),
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

// R2-H28: brief popup shown before the full life-area questions sheet.
// P2 polish (iOS LifeAreaBriefPopup.swift parity):
//   - Spring scale+fade enter/exit transition (issues 9, 17, 20, 24, 25, 37)
//   - Fixed 280dp card width (issue 10)
//   - Radial gold gradient background + gradient stroke border + drop shadow (issues 8, 31, 32, 33)
//   - Gold-tinted divider under header (issue 5)
//   - Leading sparkles icon on Ask More (issues 6, 30)
//   - Single dismiss path (footer Close removed) (issues 7, 14, 16, 34)
//   - Haptic on dismiss (light) + medium on Ask More (issues 12, 13, 18, 36)
//   - testTag on Ask More for E2E parity (issues 11, 19)
//   - 60% black tap-to-dismiss backdrop (issue 26)
@Composable
private fun LifeAreaBriefPopup(
    lifeArea: HomeLifeArea,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    // Drive the AnimatedVisibility enter animation on first composition (issue 25).
    var isAppearing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isAppearing = true }

    Dialog(
        onDismissRequest = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Backdrop: 60% black with tap-to-dismiss (issues 26, 34).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDismiss()
                },
            contentAlignment = Alignment.Center,
        ) {
            // Spring scale+fade enter/exit (issues 9, 17, 20, 24, 25, 37).
            AnimatedVisibility(
                visible = isAppearing,
                enter = scaleIn(
                    initialScale = 0.85f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                exit = scaleOut(targetScale = 0.85f) + fadeOut(),
            ) {
                Box(
                    modifier = Modifier
                        .width(280.dp) // fixed width parity with iOS .frame(width: 280) (issue 10)
                        .shadow(
                            elevation = 15.dp,
                            shape = RoundedCornerShape(16.dp),
                            ambientColor = Gold.copy(alpha = 0.25f),
                            spotColor = Gold.copy(alpha = 0.25f),
                        )
                        .clip(RoundedCornerShape(16.dp))
                        // Radial gold cosmic glow over solid navy (issues 8, 31).
                        .background(Color(0xFF0D0826))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Gold.copy(alpha = 0.10f),
                                    Color.Transparent,
                                ),
                                radius = 450f,
                            ),
                        )
                        // Gold gradient stroke border (issues 8, 32).
                        .border(
                            1.dp,
                            Brush.linearGradient(
                                colors = listOf(
                                    Gold.copy(alpha = 0.6f),
                                    Gold.copy(alpha = 0.2f),
                                ),
                            ),
                            RoundedCornerShape(16.dp),
                        )
                        .padding(16.dp)
                        // Stop tap-through to backdrop dismiss.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { /* swallow */ },
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
                                // Status text — parity with iOS LifeAreaBriefPopup.swift:35-45.
                                // Unknown statuses fall back to textSecondary (issue 15).
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
                            // X close button — light haptic + dismiss (issues 12, 18).
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onDismiss()
                                },
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
                        // Gold-tinted divider under header (issue 5).
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(
                            color = Gold.copy(alpha = 0.3f),
                            thickness = 0.5.dp,
                        )
                        if (lifeArea.briefDescription.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = lifeArea.briefDescription,
                                fontSize = 13.sp,
                                color = CreamDim,
                                lineHeight = 18.sp,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        // Single dismiss path: only Ask More CTA (issues 7, 14, 16).
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Gold.copy(alpha = 0.18f))
                                    .border(
                                        0.5.dp,
                                        Gold.copy(alpha = 0.5f),
                                        RoundedCornerShape(10.dp),
                                    )
                                    .clickable {
                                        // Medium haptic to match iOS .play(.medium) (issues 13, 36).
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onConfirm()
                                    }
                                    .testTag("life_area_ask_more_button") // issues 11, 19
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                // Leading sparkles icon (issues 6, 30).
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = Gold,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = stringResource(R.string.ask_more_ellipsis),
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
        GoldGradientText(
            text = stringResource(R.string.home_what_in_my_mind),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = CanelaFontFamily,
        )
        Spacer(Modifier.height(8.dp))
        questions.chunked(2).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { question ->
                    QuickQuestionCard(
                        question = question,
                        onClick = { onQuestionTap(question) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
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
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(14.dp),
                ambientColor = Gold.copy(alpha = 0.08f),
                spotColor = Gold.copy(alpha = 0.08f),
            )
            .clip(RoundedCornerShape(14.dp))
            .background(NavySurface)
            .border(2.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
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
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(GoldLight, Gold)),
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
