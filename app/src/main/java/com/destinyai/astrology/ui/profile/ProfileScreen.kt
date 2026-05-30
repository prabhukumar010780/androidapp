package com.destinyai.astrology.ui.profile

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            viewModel.refreshAll()
        },
    )

    LaunchedEffect(Unit) { viewModel.loadProfile() }
    LaunchedEffect(state.isDeleted) { if (state.isDeleted) onDeletedAccount() }
    LaunchedEffect(state.isLoading) { if (!state.isLoading) isRefreshing = false }
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    // ── Dialogs & sheets ─────────────────────────────────────────────────────
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear chat history", color = CreamText, fontFamily = CanelaFontFamily) },
            text = { Text("All chat history will be permanently deleted.", color = CreamDim) },
            confirmButton = {
                TextButton(onClick = {
                    showClearHistoryDialog = false
                    viewModel.clearChatHistory()
                }) { Text("Clear", color = Color(0xFFFF5252), fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) { Text("Cancel", color = CreamDim) }
            },
            containerColor = NavySurface,
        )
    }

    if (state.showDeleteSheet) {
        DeleteAccountSheet(
            hasActiveSubscription = state.hasActiveSubscription,
            onDismiss = { viewModel.dismissDeleteConfirmation() },
            onConfirmDelete = { viewModel.confirmDeleteAccount() },
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
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CreamDim)
                        }
                        Text(
                            text = "Profile",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = CanelaFontFamily,
                            color = Gold,
                            modifier = Modifier.weight(1f),
                        )
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
                                            text = state.userName.ifEmpty { "Guest" },
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = CanelaFontFamily,
                                            color = CreamText,
                                        )
                                        if (state.email.isNotEmpty()) {
                                            Text(text = state.email, fontSize = 13.sp, color = CreamDim)
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
                                                text = if (isPaid) "Plus" else "Free",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isPaid) Gold else CreamDim,
                                            )
                                        }
                                    }
                                }
                            }

                            // ── R2-P2 Active chart row ────────────────────────────────
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NavySurface)
                                    .border(0.5.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                    .clickable { onNavigateToCharts() }
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
                                    text = "Viewing: ${state.userName.ifEmpty { "your" }}'s birth chart",
                                    fontSize = 13.sp,
                                    color = CreamDim,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    imageVector = Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = CreamDim.copy(alpha = 0.4f),
                                    modifier = Modifier.size(16.dp),
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
                                        text = "Upgrading to ${state.pendingUpgradePlanId}" +
                                            (state.pendingUpgradeDate?.let { " on $it" } ?: ""),
                                        fontSize = 13.sp,
                                        color = Gold,
                                    )
                                }
                            }

                            // Daily quota card
                            if (state.dailyQuota > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(NavySurface)
                                        .border(0.5.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                                        .padding(16.dp),
                                ) {
                                    Column {
                                        Text(
                                            text = "Daily Questions",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Gold.copy(alpha = 0.7f),
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "${state.dailyUsed} / ${state.dailyQuota} used today",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = CreamText,
                                        )
                                    }
                                }
                            }

                            // Settings button
                            OutlinedButton(
                                onClick = onNavigateToSettings,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(width = 1.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = CreamText,
                                    containerColor = NavySurface,
                                ),
                            ) {
                                Text("Settings", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            }

                            // ── Preferences section ───────────────────────────────────
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Preferences",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Gold.copy(alpha = 0.7f),
                                modifier = Modifier.fillMaxWidth(),
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
                                    PreferenceArrowRow(label = "Switch Profile", onClick = { viewModel.showProfileSwitcher() })
                                    HorizontalDivider(color = Gold.copy(alpha = 0.08f))

                                    // R2-P12 Partner manager
                                    PreferenceArrowRow(label = "Manage saved birth charts", onClick = onNavigateToPartners)
                                    HorizontalDivider(color = Gold.copy(alpha = 0.08f))

                                    PreferenceArrowRow(label = "Language", onClick = onNavigateToLanguage)
                                    HorizontalDivider(color = Gold.copy(alpha = 0.08f))

                                    PreferenceArrowRow(label = "Response Style", onClick = onNavigateToResponseStyle)
                                    HorizontalDivider(color = Gold.copy(alpha = 0.08f))

                                    PreferenceArrowRow(label = "Notification Preferences", onClick = onNavigateToNotificationPrefs)
                                    HorizontalDivider(color = Gold.copy(alpha = 0.08f))

                                    // R2-P9 Notification permission row
                                    val notifEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
                                    NotificationPermissionRow(
                                        enabled = notifEnabled,
                                        onClick = {
                                            if (!notifEnabled) {
                                                context.startActivity(
                                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                                                )
                                            }
                                        },
                                    )
                                    HorizontalDivider(color = Gold.copy(alpha = 0.08f))

                                    PreferenceToggleRow(
                                        label = "Chat History",
                                        checked = state.historyEnabled,
                                        onCheckedChange = { viewModel.toggleHistory(!state.historyEnabled) },
                                    )
                                    HorizontalDivider(color = Gold.copy(alpha = 0.08f))

                                    // R2-P7 Clear history row
                                    PreferenceArrowRow(label = "Clear chat history", onClick = { showClearHistoryDialog = true })
                                    HorizontalDivider(color = Gold.copy(alpha = 0.08f))

                                    // R2-P10 Analytics (backed by API)
                                    PreferenceToggleRow(
                                        label = "Analytics",
                                        checked = state.analyticsConsent,
                                        onCheckedChange = { viewModel.toggleAnalytics(!state.analyticsConsent) },
                                    )

                                    // R2-P8 FAQ Help
                                    HorizontalDivider(color = Gold.copy(alpha = 0.08f))
                                    PreferenceArrowRow(label = "Help & FAQ", onClick = onNavigateToFaq)
                                }
                            }

                            // Upgrade button (non-premium only)
                            if (!state.isPremium) {
                                Button(
                                    onClick = onNavigateToSubscription,
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Gold,
                                        contentColor = Color(0xFF0D0D1A),
                                    ),
                                ) {
                                    Text("✦  Upgrade to Premium", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                }
                            }

                            // R2-P6 Manage subscription (premium only)
                            if (state.isPremium && state.planId.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = {
                                        val url = "https://play.google.com/store/account/subscriptions?" +
                                            "sku=${state.planId}&package=com.destinyai.astrology"
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
                                    Text("Manage subscription", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                }
                            }

                            Spacer(Modifier.weight(1f))

                            if (state.error != null) {
                                Text(text = state.error ?: "", color = Color(0xFFFF8A80), fontSize = 13.sp)
                            }

                            // R2-P11 Support links
                            SupportLinksSection(context = context)

                            TextButton(
                                onClick = { viewModel.showDeleteConfirmation() },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Delete Account", color = Color(0xFFFF5252), fontWeight = FontWeight.SemiBold)
                            }

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
}

@Composable
private fun NotificationPermissionRow(enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Notifications,
            contentDescription = null,
            tint = CreamDim.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text = "Notifications", fontSize = 15.sp, color = CreamText, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (enabled) Gold.copy(alpha = 0.15f) else Color(0xFFFF5252).copy(alpha = 0.1f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(
                text = if (enabled) "Allowed" else "Denied",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) Gold else Color(0xFFFF8A80),
            )
        }
    }
}

@Composable
private fun SupportLinksSection(context: android.content.Context) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NavySurface)
            .border(0.5.dp, Gold.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
    ) {
        SupportLinkRow(
            label = "Contact us",
            onClick = {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@destinyai.app?subject=Support"))
                context.startActivity(Intent.createChooser(intent, "Contact support"))
            },
        )
        HorizontalDivider(color = Gold.copy(alpha = 0.08f))
        SupportLinkRow(
            label = "Privacy policy",
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://destinyai.app/privacy")))
            },
        )
        HorizontalDivider(color = Gold.copy(alpha = 0.08f))
        SupportLinkRow(
            label = "Terms of service",
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://destinyai.app/terms")))
            },
        )
    }
}

@Composable
private fun SupportLinkRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, fontSize = 15.sp, color = CreamDim, modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = CreamDim.copy(alpha = 0.4f),
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun PreferenceArrowRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
private fun PreferenceToggleRow(label: String, checked: Boolean, onCheckedChange: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, fontSize = 15.sp, color = CreamText, modifier = Modifier.weight(1f))
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
