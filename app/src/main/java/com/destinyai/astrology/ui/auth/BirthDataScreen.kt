package com.destinyai.astrology.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.NavyVariant

@Composable
fun BirthDataScreen(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: BirthDataViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadSaved() }
    LaunchedEffect(state.isSaved) { if (state.isSaved) onSaved() }

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
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = CreamDim,
                    )
                }
                Text(
                    text = "Your Birth Details",
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
                Spacer(Modifier.height(8.dp))

                Text(
                    text = "We need your birth details to generate accurate predictions.",
                    fontSize = 15.sp,
                    color = CreamDim,
                )

                CosmicInputField(
                    value = state.userName,
                    onValueChange = viewModel::setUserName,
                    label = "Your Name",
                )
                CosmicInputField(
                    value = state.dateOfBirth,
                    onValueChange = viewModel::setDateOfBirth,
                    label = "Date of Birth (YYYY-MM-DD)",
                    placeholder = "1990-05-15",
                )
                CosmicInputField(
                    value = state.timeOfBirth,
                    onValueChange = viewModel::setTimeOfBirth,
                    label = "Time of Birth (HH:MM)",
                    placeholder = "14:30",
                    enabled = !state.timeUnknown,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.timeUnknown,
                        onCheckedChange = viewModel::setTimeUnknown,
                        colors = CheckboxDefaults.colors(
                            checkedColor = Gold,
                            uncheckedColor = CreamDim,
                        ),
                    )
                    Text(
                        text = "Time of birth unknown",
                        fontSize = 15.sp,
                        color = CreamDim,
                    )
                }

                CosmicInputField(
                    value = state.cityOfBirth,
                    onValueChange = { viewModel.setLocation(it, 0.0, 0.0) },
                    label = "City of Birth",
                )

                Text(
                    text = "Gender",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Gold.copy(alpha = 0.7f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("Male", "Female", "Other").forEach { gender ->
                        FilterChip(
                            selected = state.gender == gender.lowercase(),
                            onClick = { viewModel.setGender(gender.lowercase()) },
                            label = { Text(gender, color = if (state.gender == gender.lowercase()) Color(0xFF0D0D1A) else CreamDim) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Gold,
                                containerColor = NavySurface,
                                selectedLabelColor = Color(0xFF0D0D1A),
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = state.gender == gender.lowercase(),
                                borderColor = Gold.copy(alpha = 0.3f),
                                selectedBorderColor = Gold,
                            ),
                        )
                    }
                }

                if (state.error != null) {
                    Text(
                        text = state.error ?: "",
                        color = Color(0xFFFF8A80),
                        fontSize = 13.sp,
                    )
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { viewModel.save() },
                    enabled = viewModel.isValid && !state.isLoading,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Gold,
                        disabledContainerColor = NavyVariant,
                        contentColor = Color(0xFF0D0D1A),
                        disabledContentColor = CreamDim,
                    ),
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color(0xFF0D0D1A),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Save & Continue", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun CosmicInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = CreamDim) },
        placeholder = if (placeholder.isNotEmpty()) ({ Text(placeholder, color = CreamDim.copy(alpha = 0.5f)) }) else null,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Gold,
            unfocusedBorderColor = Gold.copy(alpha = 0.25f),
            disabledBorderColor = Gold.copy(alpha = 0.1f),
            focusedTextColor = CreamText,
            unfocusedTextColor = CreamText,
            disabledTextColor = CreamDim,
            cursorColor = Gold,
            focusedLabelColor = Gold,
            unfocusedContainerColor = NavySurface,
            focusedContainerColor = NavySurface,
            disabledContainerColor = NavySurface.copy(alpha = 0.5f),
        ),
    )
}
