package com.destinyai.astrology.ui.notifications

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.data.local.prefs.AlertItem
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface

private val suggestions = listOf(
    "Daily morning insight",
    "Important transits this week",
    "Compatibility match results",
    "Subscription renewal reminder",
)

@Composable
fun NotificationPreferencesScreen(
    onBack: () -> Unit,
    viewModel: NotificationPreferencesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showAddSheet by remember { mutableStateOf(false) }
    var editingAlert by remember { mutableStateOf<AlertItem?>(null) }

    LaunchedEffect(Unit) { viewModel.loadPrefs() }

    if (showAddSheet) {
        AddEditAlertSheet(
            existing = null,
            onSave = { text, freq -> viewModel.addAlert(text, freq) },
            onDismiss = { showAddSheet = false },
        )
    }

    editingAlert?.let { item ->
        AddEditAlertSheet(
            existing = item,
            onSave = { text, freq -> viewModel.updateAlert(item.id, text, freq) },
            onDismiss = { editingAlert = null },
        )
    }

    CosmicBackground {
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
                    text = "Notification Preferences",
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
                NotifSectionCard(title = "Channels") {
                    NotifChannelToggleRow(
                        label = "Push Notifications",
                        checked = state.pushEnabled,
                        onCheckedChange = viewModel::setPushEnabled,
                    )
                    HorizontalDivider(color = Gold.copy(alpha = 0.08f), thickness = 0.5.dp)
                    NotifChannelToggleRow(
                        label = "Email",
                        checked = state.emailEnabled,
                        onCheckedChange = viewModel::setEmailEnabled,
                    )
                    HorizontalDivider(color = Gold.copy(alpha = 0.08f), thickness = 0.5.dp)
                    NotifChannelToggleRow(
                        label = "In-App",
                        checked = state.inAppEnabled,
                        onCheckedChange = viewModel::setInAppEnabled,
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
                            text = "Notifications are blocked in system settings.",
                            fontSize = 13.sp,
                            color = Color(0xFFFF8A80),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                context.startActivity(intent)
                            },
                        ) {
                            Text("Enable", color = Gold, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }

                // ── R2-S13c: Custom alerts section ───────────────────────────
                NotifSectionCard(title = "Custom Alerts") {
                    if (state.alertItems.isEmpty()) {
                        Text(
                            text = "No custom alerts yet. Add one below.",
                            fontSize = 13.sp,
                            color = CreamDim,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        state.alertItems.forEachIndexed { index, item ->
                            AlertItemRow(
                                item = item,
                                onEdit = { editingAlert = item },
                                onDelete = { viewModel.deleteAlert(item.id) },
                            )
                            if (index < state.alertItems.lastIndex) {
                                HorizontalDivider(color = Gold.copy(alpha = 0.08f), thickness = 0.5.dp)
                            }
                        }
                    }
                }

                // Add new alert button
                OutlinedButton(
                    onClick = { showAddSheet = true },
                    enabled = state.canAddMore,
                    modifier = Modifier.fillMaxWidth(),
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
                        if (state.canAddMore) "Add Alert" else "Maximum 5 alerts reached",
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                // ── R2-S13d: Suggestions section ─────────────────────────────
                val existingTexts = state.alertItems.map { it.text.lowercase() }.toSet()
                val filteredSuggestions = suggestions.filter { s ->
                    s.lowercase() !in existingTexts
                }
                if (filteredSuggestions.isNotEmpty() && state.canAddMore) {
                    Text(
                        text = "SUGGESTIONS",
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
                                        viewModel.addAlert(suggestion, "Daily")
                                    }
                                    .padding(horizontal = 0.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = suggestion,
                                    fontSize = 14.sp,
                                    color = CreamText,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = "Add",
                                    tint = Gold.copy(alpha = 0.7f),
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
                if (state.isSaved) {
                    Text(text = "Preferences saved", color = Gold, fontSize = 13.sp)
                }

                Button(
                    onClick = { viewModel.save() },
                    enabled = !state.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Gold,
                        contentColor = Color(0xFF0D0D1A),
                    ),
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF0D0D1A), strokeWidth = 2.dp)
                    } else {
                        Text("Save Preferences", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                }

                Spacer(Modifier.height(32.dp))
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
