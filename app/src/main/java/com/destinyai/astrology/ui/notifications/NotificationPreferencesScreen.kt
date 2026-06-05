package com.destinyai.astrology.ui.notifications

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.R
import com.destinyai.astrology.data.local.prefs.AlertItem
import com.destinyai.astrology.services.HapticManager
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface
import kotlinx.coroutines.launch

private val suggestions: List<Int> = listOf(
    R.string.notif_suggestion_daily_morning,
    R.string.notif_suggestion_transits_week,
    R.string.notif_suggestion_compat_results,
    R.string.notif_suggestion_renewal,
)

/**
 * Returns true when the OS will deliver notifications: POST_NOTIFICATIONS granted on Android 13+
 * AND the user hasn't disabled notifications system-wide for this app.
 */
private fun isNotificationsEffectivelyEnabled(context: android.content.Context): Boolean {
    val runtimeOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    return runtimeOk && NotificationManagerCompat.from(context).areNotificationsEnabled()
}

@Composable
fun NotificationPreferencesScreen(
    onBack: () -> Unit,
    viewModel: NotificationPreferencesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = remember { HapticManager(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.alerts_saved)

    var showAddSheet by remember { mutableStateOf(false) }
    var editingAlert by remember { mutableStateOf<AlertItem?>(null) }
    // iOS parity (NotificationPreferencesSheet.swift:225-239): require explicit confirm before deleting an alert.
    var alertToDelete by remember { mutableStateOf<AlertItem?>(null) }

    // R2-S8: Android 13+ runtime permission launcher (mirrors iOS requestPermission)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.setPermissionGranted(granted && NotificationManagerCompat.from(context).areNotificationsEnabled())
        if (granted) {
            viewModel.setPushEnabled(true)
        }
    }

    // Re-check permission state on screen entry and on resume (user may toggle in OS settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.setPermissionGranted(isNotificationsEffectivelyEnabled(context))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.setPermissionGranted(isNotificationsEffectivelyEnabled(context))
        viewModel.loadPrefs()
    }

    // iOS parity (NotificationPreferencesSheet.swift:194-201, 164-184):
    // Auto-dismiss after a successful save and surface a transient toast.
    LaunchedEffect(state.isSaved, state.error) {
        if (state.isSaved && state.error == null) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = savedMessage,
                    duration = SnackbarDuration.Short,
                )
            }
            haptic.success()
            onBack()
        }
    }

    if (showAddSheet) {
        AddEditAlertSheet(
            existing = null,
            onSave = { text, freq, day -> viewModel.addAlert(text, freq, day) },
            onDismiss = { showAddSheet = false },
        )
    }

    editingAlert?.let { item ->
        AddEditAlertSheet(
            existing = item,
            onSave = { text, freq, day -> viewModel.updateAlert(item.id, text, freq, day) },
            onDismiss = { editingAlert = null },
        )
    }

    // iOS parity (NotificationPreferencesSheet.swift:225-239): destructive confirm dialog.
    alertToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { alertToDelete = null },
            title = { Text(stringResource(R.string.notif_delete_alert_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.remove_alert_confirm_format,
                        item.text.take(50),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    haptic.light()
                    viewModel.deleteAlert(item.id)
                    alertToDelete = null
                }) {
                    Text(
                        stringResource(R.string.delete_action),
                        color = Color(0xFFFF8A80),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    haptic.light()
                    alertToDelete = null
                }) {
                    Text(stringResource(R.string.cancel_action))
                }
            },
        )
    }

    CosmicBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header — iOS parity adds an explicit "Cancel" text button alongside the back arrow.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        haptic.light()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.notif_back), tint = CreamDim)
                    }
                    // iOS parity (NotificationPreferencesSheet.swift:189-191): leading "Cancel" toolbar button.
                    TextButton(
                        onClick = {
                            haptic.light()
                            onBack()
                        },
                        modifier = Modifier.testTag("notif_prefs_cancel"),
                    ) {
                        Text(
                            text = stringResource(R.string.cancel_action),
                            fontSize = 14.sp,
                            color = CreamDim,
                        )
                    }
                    Text(
                        text = stringResource(R.string.notif_preferences_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CanelaFontFamily,
                        color = Gold,
                        modifier = Modifier.weight(1f),
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Spacer(Modifier.height(4.dp))

                    // ── R2-S7: Channel toggles ────────────────────────────────────
                    NotifSectionCard(title = stringResource(R.string.notif_channels_section)) {
                        NotifChannelToggleRow(
                            label = stringResource(R.string.notif_channel_push),
                            checked = state.pushEnabled,
                            onCheckedChange = { newValue ->
                                haptic.light()
                                // R2-S8: mirror iOS handlePushToggleAttempt
                                // notDetermined → request, denied → open settings, granted → toggle
                                if (!newValue) {
                                    viewModel.setPushEnabled(false)
                                    return@NotifChannelToggleRow
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val status = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    )
                                    if (status == PackageManager.PERMISSION_GRANTED) {
                                        viewModel.setPushEnabled(true)
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                } else {
                                    // Pre-Android 13: no runtime permission required
                                    if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                                        viewModel.setPushEnabled(true)
                                    } else {
                                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        }
                                        context.startActivity(intent)
                                    }
                                }
                            },
                        )
                        HorizontalDivider(color = Gold.copy(alpha = 0.08f), thickness = 0.5.dp)
                        NotifChannelToggleRow(
                            label = stringResource(R.string.notif_channel_email_label),
                            checked = state.emailEnabled,
                            onCheckedChange = {
                                haptic.light()
                                viewModel.setEmailEnabled(it)
                            },
                        )
                        HorizontalDivider(color = Gold.copy(alpha = 0.08f), thickness = 0.5.dp)
                        NotifChannelToggleRow(
                            label = stringResource(R.string.notif_channel_inapp_label),
                            checked = state.inAppEnabled,
                            onCheckedChange = {
                                haptic.light()
                                viewModel.setInAppEnabled(it)
                            },
                        )
                    }

                    // ── R2-S8: Push permission denied card ───────────────────────
                    if (!state.isPermissionGranted) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF2A1A1A))
                                .border(0.5.dp, Color(0xFFFF8A80).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFF8A80),
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = stringResource(R.string.notif_blocked_in_settings),
                                fontSize = 13.sp,
                                color = Color(0xFFFF8A80),
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = {
                                    haptic.light()
                                    // R2-S8: Try runtime request on Android 13+ if not yet permanently denied,
                                    // otherwise fall through to OS notification settings (mirrors iOS denied → settings).
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS,
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        }
                                        context.startActivity(intent)
                                    }
                                },
                            ) {
                                Text(stringResource(R.string.notif_enable), color = Gold, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }

                    // ── R2-S13c: Custom alerts section ───────────────────────────
                    NotifSectionCard(title = stringResource(R.string.notif_custom_alerts_section)) {
                        if (state.alertItems.isEmpty()) {
                            Text(
                                text = stringResource(R.string.notif_no_custom_alerts),
                                fontSize = 13.sp,
                                color = CreamDim,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        } else {
                            state.alertItems.forEachIndexed { index, item ->
                                AlertItemRow(
                                    item = item,
                                    onEdit = {
                                        haptic.light()
                                        editingAlert = item
                                    },
                                    onDelete = {
                                        haptic.light()
                                        alertToDelete = item
                                    },
                                )
                                if (index < state.alertItems.lastIndex) {
                                    HorizontalDivider(color = Gold.copy(alpha = 0.08f), thickness = 0.5.dp)
                                }
                            }
                        }
                    }

                    // Add new alert button
                    OutlinedButton(
                        onClick = {
                            haptic.light()
                            showAddSheet = true
                        },
                        enabled = state.canAddMore,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("notif_add_alert_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (state.canAddMore) Gold else CreamDim,
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            width = 0.5.dp,
                        ),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (state.canAddMore) stringResource(R.string.notif_add_alert) else stringResource(R.string.notif_max_alerts_reached),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    // ── R2-S13d: Suggestions section ─────────────────────────────
                    // iOS parity (NotificationPreferencesSheet.swift:144): keep visible when !canAddMore,
                    // but render disabled (dim plus icon, swallow clicks) instead of hiding.
                    val resolvedSuggestions = suggestions.map { stringResource(it) }
                    val existingTexts = state.alertItems.map { it.text.lowercase() }.toSet()
                    val filteredSuggestions = resolvedSuggestions.filter { s ->
                        s.lowercase() !in existingTexts
                    }
                    if (filteredSuggestions.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.notif_suggestions),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Gold.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        NotifSectionCard(title = null) {
                            filteredSuggestions.forEachIndexed { index, suggestion ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = state.canAddMore) {
                                            haptic.light()
                                            viewModel.addAlert(suggestion, "Daily", null)
                                        }
                                        .padding(horizontal = 0.dp, vertical = 14.dp)
                                        .testTag("notif_suggestion_$index"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = suggestion,
                                        fontSize = 14.sp,
                                        // iOS parity: dim text alongside the plus icon when disabled.
                                        color = if (state.canAddMore) CreamText else CreamDim,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Icon(
                                        Icons.Filled.Add,
                                        contentDescription = stringResource(R.string.notif_add),
                                        // iOS parity (NotificationPreferencesSheet.swift:144): tertiary tint when disabled.
                                        tint = if (state.canAddMore) Gold.copy(alpha = 0.7f) else CreamDim.copy(alpha = 0.5f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                if (index < filteredSuggestions.lastIndex) {
                                    HorizontalDivider(color = Gold.copy(alpha = 0.08f), thickness = 0.5.dp)
                                }
                            }
                        }
                    }

                    if (state.error != null) {
                        Text(text = state.error ?: "", color = Color(0xFFFF8A80), fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            haptic.light()
                            viewModel.save()
                        },
                        enabled = !state.isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("notif_save_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Gold,
                            contentColor = Color(0xFF0D0D1A),
                        ),
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF0D0D1A), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.notif_save_preferences), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        }
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }

            // iOS parity (NotificationPreferencesSheet.swift:164-184): floating capsule toast on save.
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .testTag("notif_saved_toast"),
            ) { data ->
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = NavySurface,
                    border = BorderStroke(0.5.dp, Gold.copy(alpha = 0.4f)),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = data.visuals.message,
                            color = CreamText,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotifSectionCard(
    title: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Gold.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(NavySurface)
                .border(0.5.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp),
            content = content,
        )
    }
}

@Composable
private fun NotifChannelToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = CreamText)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Gold,
                checkedTrackColor = Gold.copy(alpha = 0.3f),
            ),
        )
    }
}

/**
 * iOS parity (NotificationPreferencesSheet.swift:272): map each frequency to a leading icon.
 */
private fun frequencyIcon(frequency: String): ImageVector = when (frequency.lowercase()) {
    "daily" -> Icons.Filled.Today
    "weekly" -> Icons.Filled.DateRange
    "monthly" -> Icons.Filled.CalendarMonth
    else -> Icons.Filled.Today
}

@Composable
private fun AlertItemRow(
    item: AlertItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // iOS parity (NotificationPreferencesSheet.swift:272): leading frequency icon.
        Icon(
            imageVector = frequencyIcon(item.frequency),
            contentDescription = null,
            tint = Gold,
            modifier = Modifier
                .size(20.dp)
                .testTag("alert_freq_icon_${item.frequency.lowercase()}"),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.text,
                fontSize = 14.sp,
                color = CreamText,
                maxLines = 2,
            )
            Surface(
                modifier = Modifier.padding(top = 4.dp),
                shape = RoundedCornerShape(6.dp),
                color = Gold.copy(alpha = 0.15f),
            ) {
                Text(
                    text = item.frequency,
                    fontSize = 11.sp,
                    color = Gold,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = CreamDim, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color(0xFFFF8A80), modifier = Modifier.size(18.dp))
        }
    }
}
