package com.destinyai.astrology.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.R
import com.destinyai.astrology.data.remote.NotificationDto
import com.destinyai.astrology.services.HapticManager
import com.destinyai.astrology.services.NotificationRouter
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.GoldDim
import com.destinyai.astrology.ui.theme.NavySurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** iOS parity (NotificationModels.swift:55-66) — icon switching by notification type. */
private fun iconForType(type: String?): ImageVector = when (type?.uppercase()) {
    "DAILY_PREDICTION", "DAILY_PREDICTION_READY" -> Icons.Filled.WbSunny
    "TRANSIT_ALERT" -> Icons.Filled.AutoAwesome
    "SUBSCRIPTION_EXPIRING" -> Icons.Filled.CreditCard
    "WELCOME" -> Icons.Filled.Star
    "LIFE_ALERT" -> Icons.Filled.Warning
    "COMPATIBILITY_READY" -> Icons.Filled.Favorite
    "CUSTOM_ALERT" -> Icons.Filled.NotificationsActive
    else -> Icons.Filled.Notifications
}

/** iOS parity (NotificationInboxView.swift:238-244) — tone -> accent color. */
private fun accentForTone(overallTone: String?): Color = when (overallTone?.lowercase()) {
    "positive" -> Gold
    "cautionary", "caution" -> Color(0xFFFFA500)
    else -> CreamDim.copy(alpha = 0.5f)
}

/** iOS parity (NotificationModels.swift:69-72) — life-area chip with general filter. */
private fun topicChipText(topic: String?): String? {
    val t = topic?.takeIf { it.isNotEmpty() && it != "general" } ?: return null
    return t.replace('_', ' ').split(' ').joinToString(" ") { word ->
        word.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase(Locale.getDefault()) else c.toString() }
    }
}

/** iOS parity (NotificationModels.swift:41-46) — "MMM d, yyyy" exact-date format. */
private fun formatTimeAgo(createdAt: String?): String {
    val raw = createdAt?.takeIf { it.isNotEmpty() } ?: return ""
    val parsers = listOf(
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
    )
    val date: Date? = parsers.firstNotNullOfOrNull { fmt -> runCatching { fmt.parse(raw) }.getOrNull() }
    if (date == null) return ""
    val out = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return out.format(date)
}

/** iOS parity (NotificationDetailSheet.canAskMore in NotificationInboxView.swift:463-467). */
private fun canAskMore(type: String?): Boolean = type?.uppercase() in setOf(
    "DAILY_PREDICTION_READY",
    "DAILY_PREDICTION",
    "TRANSIT_ALERT",
    "LIFE_ALERT",
    "CUSTOM_ALERT",
    "WELCOME",
)

private fun displayTitleOf(notif: NotificationDto): String =
    notif.subject?.takeIf { it.isNotBlank() }
        ?: notif.title?.takeIf { it.isNotBlank() }
        ?: (notif.type?.replace('_', ' ')?.split(' ')?.joinToString(" ") { word ->
            word.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase(Locale.getDefault()) else c.toString() }
        } ?: "")

private fun displayBodyOf(notif: NotificationDto, fallback: String): String =
    notif.preview?.takeIf { it.isNotBlank() }
        ?: notif.body?.takeIf { it.isNotBlank() }
        ?: fallback

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onNotifPrefs: () -> Unit,
    onGuestSignInRequest: () -> Unit = {},
    onUpgradeRequest: () -> Unit = {},
    /** iOS parity (NotificationDetailSheet.onNavigateToHome) — invoked after the detail sheet
     *  routes to chat/compatibility/subscription so the parent can dismiss the inbox and
     *  surface the destination screen. NotificationRouter.pendingDeepLink drives the actual nav. */
    onAskMore: ((type: String?, prefill: String) -> Unit)? = null,
    onOpenCompatibility: (() -> Unit)? = null,
    onOpenSubscription: (() -> Unit)? = null,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<NotificationDto?>(null) }
    val sheetState = rememberModalBottomSheetState()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val haptic = remember { HapticManager(context) }

    LaunchedEffect(Unit) { viewModel.loadNotifications() }

    // Mirrors iOS NotificationInboxView.swift:174-179 — trigger loadMore on last item appearance.
    LaunchedEffect(listState, state.notifications.size, state.hasMore) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisible ->
                if (lastVisible != null && state.hasMore && !state.isLoadingMore &&
                    lastVisible >= state.notifications.size - 1
                ) {
                    viewModel.loadMore()
                }
            }
    }

    CosmicBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .semantics { testTagsAsResourceId = true }
                .testTag("notifications_screen"),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Close button (circle)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(NavySurface),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(onClick = {
                        haptic.light()
                        onBack()
                    }, modifier = Modifier.size(36.dp).testTag("notif_inbox_close")) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.a11y_close),
                            tint = CreamDim,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.notifications_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CanelaFontFamily,
                        color = CreamText,
                    )
                    if (state.unreadCount > 0) {
                        Text(
                            text = stringResource(R.string.unread_count_format, state.unreadCount),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Gold,
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // Mark all read (circle)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(NavySurface),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        onClick = {
                            if (state.unreadCount > 0) {
                                haptic.light()
                                viewModel.markAllRead()
                            }
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Filled.DoneAll,
                            contentDescription = stringResource(R.string.a11y_mark_all_read),
                            tint = if (state.unreadCount > 0) Gold else CreamDim,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // Personalize alerts button (gold CTA) — gated by guest + alerts entitlement
            Button(
                onClick = {
                    haptic.light()
                    when {
                        state.isGuest -> onGuestSignInRequest()
                        !state.hasAlertsFeature -> onUpgradeRequest()
                        else -> onNotifPrefs()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    // iOS parity (NotificationInboxView.swift:147-153) — flip to muted when guest.
                    containerColor = if (state.isGuest) CreamDim else Gold,
                    contentColor = Color(0xFF0D0D1A),
                ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // iOS parity (NotificationInboxView.swift:132-133) — leading person.badge.plus / bell.badge icon.
                    Icon(
                        imageVector = if (state.isGuest)
                            Icons.Filled.PersonAdd
                        else
                            Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = if (state.isGuest)
                            stringResource(R.string.sign_up_to_personalize_alerts)
                        else
                            stringResource(R.string.personalize_alerts_cta),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                    // iOS parity (NotificationInboxView.swift:138-141) — trailing crown.fill premium indicator.
                    if (!state.isGuest) {
                        Icon(
                            imageVector = Icons.Filled.WorkspacePremium,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // iOS parity (NotificationInboxView.swift:192): pull-to-refresh wraps the inbox content.
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Gold, modifier = Modifier.size(28.dp))
                    }
                } else if (state.notifications.isEmpty()) {
                    // iOS parity (NotificationInboxView.swift:212-231) — bell icon + title + descriptive subtitle.
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(40.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsOff,
                            contentDescription = null,
                            tint = GoldDim,
                            modifier = Modifier.size(60.dp),
                        )
                        Text(
                            text = stringResource(R.string.no_notifications),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = CanelaFontFamily,
                            color = CreamText,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = stringResource(R.string.no_notifications_desc),
                            fontSize = 14.sp,
                            color = CreamDim,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.notifications, key = { it.id }) { notif ->
                            NotificationRowItem(
                                notif = notif,
                                onClick = {
                                    haptic.light()
                                    if (!notif.isRead) viewModel.markRead(notif.id)
                                    selected = notif
                                },
                            )
                        }
                        if (state.isLoadingMore) {
                            item(key = "__loading_more__") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(color = Gold, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        selected?.let { notif ->
            ModalBottomSheet(
                onDismissRequest = { selected = null },
                sheetState = sheetState,
                containerColor = NavySurface,
            ) {
                NotificationDetailSheetContent(
                    notif = notif,
                    onAskMore = { type, prefill ->
                        haptic.light()
                        // iOS parity (NotificationInboxView.swift:387-394) — emit deep link before dismiss.
                        NotificationRouter.route(
                            type = type,
                            prefill = prefill,
                            autoSubmit = true,
                            newThread = true,
                        )
                        selected = null
                        onAskMore?.invoke(type, prefill) ?: onBack()
                    },
                    onOpenCompatibility = {
                        haptic.light()
                        NotificationRouter.route(type = notif.type)
                        selected = null
                        onOpenCompatibility?.invoke() ?: onBack()
                    },
                    onOpenSubscription = {
                        haptic.light()
                        NotificationRouter.route(type = notif.type)
                        selected = null
                        onOpenSubscription?.invoke() ?: onBack()
                    },
                    onDone = {
                        haptic.light()
                        selected = null
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun NotificationRowItem(
    notif: NotificationDto,
    onClick: () -> Unit,
) {
    val accent = accentForTone(notif.overallTone)
    val title = displayTitleOf(notif)
    // iOS parity (NotificationModels.swift:80-82) — fallback "Tap to view details" for empty rows.
    val body = displayBodyOf(notif, stringResource(R.string.tap_to_view_details))
    val timeAgo = formatTimeAgo(notif.createdAt)
    val chip = topicChipText(notif.topic)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NavySurface)
            .border(
                1.dp,
                if (!notif.isRead) accent.copy(alpha = 0.25f) else Color.Transparent,
                RoundedCornerShape(16.dp),
            )
            .clickable { onClick() }
            .semantics { testTagsAsResourceId = true }
            .testTag("notification_row"),
        verticalAlignment = Alignment.Top,
    ) {
        // Tone accent bar (iOS parity NotificationInboxView.swift:248-254)
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(72.dp)
                .padding(vertical = 12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
        Spacer(Modifier.width(10.dp))

        // Icon circle (now type-aware — iOS parity NotificationInboxView.swift:257-265)
        Box(
            modifier = Modifier
                .padding(top = 14.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (!notif.isRead) accent.copy(alpha = 0.15f) else NavySurface,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = iconForType(notif.type),
                contentDescription = null,
                tint = if (!notif.isRead) accent else CreamDim,
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = if (!notif.isRead) FontWeight.Bold else FontWeight.Medium,
                    color = CreamText,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (timeAgo.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = timeAgo,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = CreamDim,
                    )
                }
            }

            // Topic chip (iOS parity NotificationInboxView.swift:284-292)
            if (chip != null) {
                Spacer(Modifier.height(5.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = accent.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = chip,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }

            Spacer(Modifier.height(5.dp))

            Text(
                text = body,
                fontSize = 13.sp,
                color = CreamDim,
                lineHeight = 18.sp,
                maxLines = 2,
            )
        }

        // Unread indicator dot
        if (!notif.isRead) {
            Box(
                modifier = Modifier
                    .padding(top = 18.dp, end = 12.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Gold),
            )
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun NotificationDetailSheetContent(
    notif: NotificationDto,
    onAskMore: (type: String?, prefill: String) -> Unit,
    onOpenCompatibility: () -> Unit,
    onOpenSubscription: () -> Unit,
    onDone: () -> Unit,
) {
    val title = displayTitleOf(notif)
    // iOS parity (NotificationModels.swift:80-82) — fallback "Tap to view details" for empty rows.
    val body = displayBodyOf(notif, stringResource(R.string.tap_to_view_details))
    val timeAgo = formatTimeAgo(notif.createdAt)
    val tellMoreFallback = stringResource(R.string.ask_more)
    // iOS parity (NotificationInboxView.swift:386) — localized "Tell me more about <title>" fallback.
    val tellMoreAboutFallback = stringResource(R.string.tell_me_more_about_format, title)
    val bodyScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
    ) {
        // Header: icon circle + title + timestamp (iOS parity NotificationInboxView.swift:344-364)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Gold.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = iconForType(notif.type),
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = CanelaFontFamily,
                    color = CreamText,
                )
                if (timeAgo.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = timeAgo,
                        fontSize = 12.sp,
                        color = CreamDim,
                    )
                }
            }
        }

        HorizontalDivider(color = CreamDim.copy(alpha = 0.2f))

        // iOS parity (NotificationInboxView.swift:369-377) — scrollable body so multi-paragraph
        // notifications don't push the action buttons off the sheet.
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(bodyScrollState),
        ) {
            Text(
                text = body,
                fontSize = 15.sp,
                color = CreamDim,
                lineHeight = 22.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }

        HorizontalDivider(color = CreamDim.copy(alpha = 0.2f))

        // Action buttons (iOS parity NotificationInboxView.swift:382-453)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val type = notif.type?.uppercase()
            when {
                canAskMore(notif.type) -> {
                    val prompt = notif.chatPrompt
                        ?.takeIf { it.isNotBlank() }
                        ?: tellMoreAboutFallback
                    Button(
                        onClick = { onAskMore(notif.type, prompt) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .semantics { testTagsAsResourceId = true }
                            .testTag("notification_action_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Gold,
                            contentColor = Color(0xFF0D0D1A),
                        ),
                    ) {
                        Text(
                            text = notif.chatPrompt?.takeIf { it.isNotBlank() } ?: tellMoreFallback,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            maxLines = 1,
                        )
                    }
                }
                type == "COMPATIBILITY_READY" -> {
                    Button(
                        onClick = onOpenCompatibility,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .semantics { testTagsAsResourceId = true }
                            .testTag("notification_action_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Gold,
                            contentColor = Color(0xFF0D0D1A),
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.notification_action_compat),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                        )
                    }
                }
                type == "SUBSCRIPTION_EXPIRING" -> {
                    Button(
                        onClick = onOpenSubscription,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .semantics { testTagsAsResourceId = true }
                            .testTag("notification_action_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Gold,
                            contentColor = Color(0xFF0D0D1A),
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.manage_subscription),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Gold.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Gold),
            ) {
                Text(
                    text = stringResource(R.string.done),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
            }
        }
    }
}
