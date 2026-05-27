package com.destinyai.astrology.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
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

@Composable
fun BirthDetailsScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: BirthDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadBirthData() }
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CreamDim)
                }
                Text(
                    text = "Birth Details",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CanelaFontFamily,
                    color = Gold,
                    modifier = Modifier.weight(1f),
                )
                if (state.isLoading) {
                    CircularProgressIndicator(color = Gold, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(Modifier.height(4.dp))

                // Editable section
                BirthDetailsSection(title = "Identity") {
                    BirthDetailField(
                        label = "Name",
                        value = state.name,
                        onValueChange = viewModel::setName,
                        editable = true,
                    )
                    BirthDetailRow(
                        label = "Gender",
                        value = state.gender.ifEmpty { "Not set" },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Male", "Female", "Other").forEach { g ->
                            FilterChip(
                                selected = state.gender.lowercase() == g.lowercase(),
                                onClick = { viewModel.setGender(g.lowercase()) },
                                label = {
                                    Text(
                                        g,
                                        color = if (state.gender.lowercase() == g.lowercase()) Color(0xFF0D0D1A) else CreamDim,
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Gold,
                                    containerColor = NavySurface,
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = state.gender.lowercase() == g.lowercase(),
                                    borderColor = Gold.copy(alpha = 0.3f),
                                    selectedBorderColor = Gold,
                                ),
                            )
                        }
                    }
                }

                // Read-only section
                BirthDetailsSection(title = "Birth Data") {
                    BirthDetailRow(label = "Date of Birth", value = state.dateOfBirth.ifEmpty { "—" })
                    BirthDetailRow(label = "Time of Birth", value = state.timeOfBirth.ifEmpty { "—" })
                    BirthDetailRow(label = "Place of Birth", value = state.cityOfBirth.ifEmpty { "—" })
                }

                if (state.error != null) {
                    Text(text = state.error ?: "", color = Color(0xFFFF8A80), fontSize = 13.sp)
                }

                Button(
                    onClick = { viewModel.saveName() },
                    enabled = !state.isLoading && state.name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Gold,
                        contentColor = Color(0xFF0D0D1A),
                        disabledContainerColor = NavySurface,
                        disabledContentColor = CreamDim,
                    ),
                ) {
                    Text("Save Changes", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun BirthDetailsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NavySurface)
            .border(0.5.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Gold.copy(alpha = 0.7f))
        content()
    }
}

@Composable
private fun BirthDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, fontSize = 14.sp, color = CreamDim)
        Text(text = value, fontSize = 14.sp, color = CreamText, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BirthDetailField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    editable: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = CreamDim) },
        enabled = editable,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Gold,
            unfocusedBorderColor = Gold.copy(alpha = 0.25f),
            focusedTextColor = CreamText,
            unfocusedTextColor = CreamText,
            cursorColor = Gold,
            focusedLabelColor = Gold,
            unfocusedContainerColor = NavySurface,
            focusedContainerColor = NavySurface,
        ),
    )
}
