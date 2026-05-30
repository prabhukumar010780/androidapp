package com.destinyai.astrology.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
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
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.TextTertiary

@Composable
fun BirthDetailsScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: BirthDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.loadBirthData() }
    LaunchedEffect(state.saveSuccess) { if (state.saveSuccess) onSaved() }

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

                // R2-P13 "EDITABLE" section header
                SectionHeaderLabel(text = "EDITABLE")

                // Editable section
                BirthDetailsSection(title = "Identity") {
                    BirthDetailField(
                        label = "Name",
                        value = state.name,
                        onValueChange = viewModel::setName,
                        editable = true,
                    )
                    BirthDetailRow(label = "Gender", value = state.gender.ifEmpty { "Not set" }, readOnly = false)
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

                // R2-P14 "BIRTH DATA" section header
                SectionHeaderLabel(text = "BIRTH DATA")

                // R2-P15 Read-only section with Lock icon trailing
                BirthDetailsSection(title = "Birth Data") {
                    BirthDetailRow(label = "Date of Birth", value = state.dateOfBirth.ifEmpty { "—" }, readOnly = true)
                    BirthDetailRow(label = "Time of Birth", value = state.timeOfBirth.ifEmpty { "—" }, readOnly = true)
                    BirthDetailRow(label = "Place of Birth", value = state.cityOfBirth.ifEmpty { "—" }, readOnly = true)
                }

                if (state.error != null) {
                    Text(text = state.error ?: "", color = Color(0xFFFF8A80), fontSize = 13.sp)
                }

                // R2-P16 Save button enabled only when hasChanges
                Button(
                    onClick = { viewModel.saveName() },
                    enabled = !state.isLoading && state.name.isNotBlank() && state.hasChanges,
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

                // R2-P17 Support-info block for birth data changes
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(NavySurface)
                        .border(0.5.dp, Gold.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                ) {
                    Column {
                        Text(
                            text = "Need to update birth date, time, or place?",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CreamDim,
                        )
                        Spacer(Modifier.height(4.dp))
                        val dob = state.dateOfBirth
                        val time = state.timeOfBirth
                        val city = state.cityOfBirth
                        val subject = "Birth Data Update Request"
                        val body = "Please update my birth data:\nDate: $dob\nTime: $time\nCity: $city"
                        val mailto = "mailto:support@destinyai.app?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}"
                        TextButton(
                            onClick = {
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SENDTO, Uri.parse(mailto)),
                                        "Contact support",
                                    ),
                                )
                            },
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text("Contact support", color = Gold, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SectionHeaderLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextTertiary,
        letterSpacing = 1.2.sp,
        modifier = Modifier.fillMaxWidth(),
    )
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
private fun BirthDetailRow(label: String, value: String, readOnly: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, fontSize = 14.sp, color = CreamDim, modifier = Modifier.weight(1f))
        if (readOnly) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "Read only",
                tint = CreamDim.copy(alpha = 0.4f),
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
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
