package com.destinyai.astrology.ui.notifications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.data.local.prefs.AlertItem
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.Gold

private val frequencyOptions = listOf("Daily", "Weekly", "Monthly")
private const val MAX_CHARS = 200

/**
 * R2-S13e: ModalBottomSheet for adding or editing a custom notification alert.
 *
 * @param existing  non-null when editing; null when adding a new alert.
 * @param onSave    called with the (text, frequency) on confirmation.
 * @param onDismiss called when the sheet should be closed without saving.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditAlertSheet(
    existing: AlertItem? = null,
    onSave: (text: String, frequency: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(existing?.text ?: "") }
    var frequency by remember { mutableStateOf(existing?.frequency ?: "Daily") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = if (existing == null) "Add Alert" else "Edit Alert",
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
                    .heightIn(min = 96.dp, max = 160.dp),
                placeholder = {
                    Text(
                        "Describe your alert...",
                        color = CreamDim,
                        fontSize = 14.sp,
                    )
                },
                supportingText = {
                    Text(
                        text = "${text.length} / $MAX_CHARS",
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
                text = "Frequency",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Gold.copy(alpha = 0.7f),
            )

            Column(modifier = Modifier.selectableGroup()) {
                frequencyOptions.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = frequency == option,
                                onClick = { frequency = option },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = frequency == option,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Gold,
                                unselectedColor = CreamDim,
                            ),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = option,
                            fontSize = 15.sp,
                            color = if (frequency == option) Gold else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CreamDim),
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        if (text.isNotBlank()) {
                            onSave(text.trim(), frequency)
                            onDismiss()
                        }
                    },
                    enabled = text.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Gold,
                        contentColor = Color(0xFF0D0D1A),
                        disabledContainerColor = Gold.copy(alpha = 0.3f),
                        disabledContentColor = Color(0xFF0D0D1A).copy(alpha = 0.4f),
                    ),
                ) {
                    Text("Save", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
