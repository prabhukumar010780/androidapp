package com.destinyai.astrology.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.R
import com.destinyai.astrology.data.local.prefs.AlertItem
import com.destinyai.astrology.services.HapticManager
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface

private const val MAX_CHARS = 200

// iOS parity (NotificationPreferencesSheet.swift:578-630): inline suggestions list shown
// only when adding a new alert (not editing). Tapping a suggestion fills the text field.
private val sheetSuggestions: List<Int> = listOf(
    R.string.notif_suggestion_daily_morning,
    R.string.notif_suggestion_transits_week,
    R.string.notif_suggestion_compat_results,
    R.string.notif_suggestion_renewal,
)

/**
 * R2-S13e: ModalBottomSheet for adding or editing a custom notification alert.
 *
 * @param existing  non-null when editing; null when adding a new alert.
 * @param onSave    called with the (text, frequency, frequencyDay) on confirmation.
 * @param onDismiss called when the sheet should be closed without saving.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditAlertSheet(
    existing: AlertItem? = null,
    onSave: (text: String, frequency: String, frequencyDay: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = remember { HapticManager(context) }

    var text by remember { mutableStateOf(existing?.text ?: "") }
    var frequency by remember { mutableStateOf(existing?.frequency ?: "Daily") }
    // iOS parity (NotificationPreferencesSheet.swift:493): day index for weekly/monthly alerts.
    var frequencyDay by remember { mutableStateOf(existing?.frequencyDay) }

    val freqDaily = stringResource(R.string.notif_freq_daily)
    val freqWeekly = stringResource(R.string.notif_freq_weekly)
    val freqMonthly = stringResource(R.string.notif_freq_monthly)
    val frequencyOptions = remember(freqDaily, freqWeekly, freqMonthly) {
        listOf(
            "Daily" to freqDaily,
            "Weekly" to freqWeekly,
            "Monthly" to freqMonthly,
        )
    }

    // iOS parity (NotificationPreferencesSheet.swift:671): auto-focus text field on open.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .testTag("add_edit_alert_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = if (existing == null) stringResource(R.string.notif_add_alert_title)
                       else stringResource(R.string.notif_edit_alert_dialog_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CanelaFontFamily,
                color = Gold,
            )

            // Multi-line text field
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= MAX_CHARS) text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp, max = 160.dp)
                    .focusRequester(focusRequester)
                    .testTag("alert_text_field"),
                placeholder = {
                    Text(
                        stringResource(R.string.notif_describe_alert),
                        color = CreamDim,
                        fontSize = 14.sp,
                    )
                },
                supportingText = {
                    Text(
                        text = stringResource(R.string.notif_char_count_format, text.length, MAX_CHARS),
                        color = CreamDim,
                        fontSize = 11.sp,
                    )
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                maxLines = 6,
                minLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Gold,
                    unfocusedBorderColor = Gold.copy(alpha = 0.3f),
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                ),
            )

            // Frequency picker
            Text(
                text = stringResource(R.string.notif_frequency),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Gold.copy(alpha = 0.7f),
            )

            Column(modifier = Modifier.selectableGroup()) {
                frequencyOptions.forEach { (key, displayName) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = frequency == key,
                                onClick = {
                                    haptic.light()
                                    frequency = key
                                    // iOS parity (NotificationPreferencesSheet.swift:540): reset day on freq change.
                                    frequencyDay = null
                                },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 8.dp)
                            .testTag("frequency_$key"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = frequency == key,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Gold,
                                unselectedColor = CreamDim,
                            ),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = displayName,
                            fontSize = 15.sp,
                            color = if (frequency == key) Gold else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // iOS parity: Weekly => 7-day chip row; Monthly => 1-31 dropdown.
            if (frequency == "Weekly") {
                WeekdayChipPicker(
                    selected = frequencyDay,
                    onSelect = { day ->
                        haptic.light()
                        frequencyDay = day
                    },
                )
            } else if (frequency == "Monthly") {
                MonthDayDropdown(
                    selected = frequencyDay,
                    onSelect = { day ->
                        haptic.light()
                        frequencyDay = day
                    },
                )
            }

            // iOS parity (NotificationPreferencesSheet.swift:578-630): Suggestions section,
            // only shown for new alerts (existing == null). Tap fills the text state.
            if (existing == null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.suggestions_label),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Gold,
                        )
                        Text(
                            text = stringResource(R.string.tap_to_add),
                            fontSize = 12.sp,
                            color = CreamDim,
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(NavySurface)
                            .border(0.5.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                    ) {
                        sheetSuggestions.forEachIndexed { index, suggestionRes ->
                            val suggestion = stringResource(suggestionRes)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptic.light()
                                        text = suggestion
                                    }
                                    .testTag("alert_suggestion_$index")
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = Gold,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = suggestion,
                                    fontSize = 14.sp,
                                    color = CreamText,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    Icons.Filled.AddCircle,
                                    contentDescription = null,
                                    tint = Gold,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            if (index < sheetSuggestions.lastIndex) {
                                HorizontalDivider(color = Gold.copy(alpha = 0.08f), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        haptic.light()
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("alert_sheet_cancel"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CreamDim),
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = {
                        if (text.isNotBlank()) {
                            haptic.light()
                            onSave(text.trim(), frequency, frequencyDay)
                            onDismiss()
                        }
                    },
                    enabled = text.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("alert_sheet_save"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Gold,
                        contentColor = Color(0xFF0D0D1A),
                        disabledContainerColor = Gold.copy(alpha = 0.3f),
                        disabledContentColor = Color(0xFF0D0D1A).copy(alpha = 0.4f),
                    ),
                ) {
                    Text(stringResource(R.string.save), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Weekly day-of-week chip row (iOS parity: 7 chips, 0=Sunday … 6=Saturday).
 */
@Composable
private fun WeekdayChipPicker(
    selected: Int?,
    onSelect: (Int) -> Unit,
) {
    val labels = listOf(
        R.string.notif_weekday_sun,
        R.string.notif_weekday_mon,
        R.string.notif_weekday_tue,
        R.string.notif_weekday_wed,
        R.string.notif_weekday_thu,
        R.string.notif_weekday_fri,
        R.string.notif_weekday_sat,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.notif_weekday_picker_label),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Gold.copy(alpha = 0.7f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("weekday_picker"),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            labels.forEachIndexed { index, labelRes ->
                val isSelected = selected == index
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Gold else NavySurface)
                        .border(
                            0.5.dp,
                            if (isSelected) Gold else Gold.copy(alpha = 0.3f),
                            CircleShape,
                        )
                        .clickable { onSelect(index) }
                        .testTag("weekday_$index"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(labelRes),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) Color(0xFF0D0D1A) else CreamText,
                    )
                }
            }
        }
    }
}

/**
 * Monthly day-of-month dropdown (1-31).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthDayDropdown(
    selected: Int?,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.notif_monthly_picker_label),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Gold.copy(alpha = 0.7f),
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.testTag("monthday_picker"),
        ) {
            OutlinedTextField(
                readOnly = true,
                value = selected?.toString() ?: "",
                onValueChange = {},
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Gold,
                    unfocusedBorderColor = Gold.copy(alpha = 0.3f),
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                (1..31).forEach { day ->
                    DropdownMenuItem(
                        text = { Text(day.toString()) },
                        onClick = {
                            onSelect(day)
                            expanded = false
                        },
                        modifier = Modifier.testTag("monthday_$day"),
                    )
                }
            }
        }
    }
}
