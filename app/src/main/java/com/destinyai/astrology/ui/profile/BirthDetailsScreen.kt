package com.destinyai.astrology.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.DarkNavyContrast
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.TextTertiary

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BirthDetailsScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: BirthDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    // Hoist HapticManager so it isn't reconstructed on every recomposition
    // of the support-button onClick lambda.
    val haptic = remember { com.destinyai.astrology.services.HapticManager(context) }

    LaunchedEffect(Unit) { viewModel.loadBirthData() }
    // Mirrors iOS BirthDetailsView.swift:87-91 — show "Changes saved" alert
    // first, then dismiss when user acknowledges. We surface a brief snackbar
    // and navigate after it's shown so the user has confirmation feedback.
    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) {
            snackbarHostState.showSnackbar(
                context.getString(com.destinyai.astrology.R.string.name_gender_updated),
            )
            onSaved()
        }
    }
    // Surface save errors via snackbar so users get explicit feedback when the
    // network call fails — previously the inline red text could be missed below
    // the fold on small screens.
    LaunchedEffect(state.error) {
        val err = state.error
        if (!err.isNullOrBlank()) {
            snackbarHostState.showSnackbar(err)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
    CosmicBackground {
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .testTag("birth_details_back")
                        .semantics { contentDescription = "Back" },
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(com.destinyai.astrology.R.string.birth_back), tint = CreamDim)
                }
                Text(
                    text = stringResource(com.destinyai.astrology.R.string.birth_details),
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
                SectionHeaderLabel(text = stringResource(com.destinyai.astrology.R.string.birth_section_editable))

                // Editable section
                BirthDetailsSection(title = stringResource(com.destinyai.astrology.R.string.birth_section_identity)) {
                    BirthDetailField(
                        label = stringResource(com.destinyai.astrology.R.string.birth_field_name),
                        value = state.name,
                        onValueChange = viewModel::setName,
                        editable = true,
                    )
                    BirthDetailRow(label = stringResource(com.destinyai.astrology.R.string.birth_field_gender), value = state.gender.ifEmpty { stringResource(com.destinyai.astrology.R.string.not_set) }, readOnly = false)
                    // iOS parity (BirthDetailsView.swift:75-80): the 4 valid backend values
                    // are "" (prefer_not_to_say), "male", "female", "non-binary".
                    // Each FilterChip stores the canonical iOS value; the label is human-readable.
                    val genderOptions = listOf(
                        "" to stringResource(com.destinyai.astrology.R.string.gender_prefer_not_say),
                        "male" to stringResource(com.destinyai.astrology.R.string.gender_male),
                        "female" to stringResource(com.destinyai.astrology.R.string.gender_female),
                        "non-binary" to stringResource(com.destinyai.astrology.R.string.gender_non_binary),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        genderOptions.forEach { (value, label) ->
                            val selected = state.gender == value
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.setGender(value) },
                                label = {
                                    Text(
                                        label,
                                        color = if (selected) DarkNavyContrast else CreamDim,
                                    )
                                },
                                modifier = Modifier.testTag("birth_details_gender_$value"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Gold,
                                    containerColor = NavySurface,
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = Gold.copy(alpha = 0.3f),
                                    selectedBorderColor = Gold,
                                ),
                            )
                        }
                    }
                }

                // R2-P14 "BIRTH DATA" section header
                SectionHeaderLabel(text = stringResource(com.destinyai.astrology.R.string.birth_section_birth_data))

                // R2-P15 Read-only section with Lock icon trailing
                BirthDetailsSection(title = stringResource(com.destinyai.astrology.R.string.birth_section_birth_data_title)) {
                    BirthDetailRow(label = stringResource(com.destinyai.astrology.R.string.date_of_birth), value = state.dateOfBirth.ifEmpty { stringResource(com.destinyai.astrology.R.string.birth_value_dash) }, readOnly = true)
                    BirthDetailRow(label = stringResource(com.destinyai.astrology.R.string.time_of_birth), value = state.timeOfBirth.ifEmpty { stringResource(com.destinyai.astrology.R.string.birth_value_dash) }, readOnly = true)
                    BirthDetailRow(label = stringResource(com.destinyai.astrology.R.string.place_of_birth), value = state.cityOfBirth.ifEmpty { stringResource(com.destinyai.astrology.R.string.birth_value_dash) }, readOnly = true)
                }

                if (state.error != null) {
                    Text(
                        text = state.error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        modifier = Modifier.testTag("birth_details_error"),
                    )
                }

                // R2-P16 Save button enabled only when hasChanges
                Button(
                    onClick = { viewModel.saveName() },
                    enabled = !state.isLoading && state.name.isNotBlank() && state.hasChanges,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("birth_details_save")
                        .semantics { contentDescription = "Save Changes" },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Gold,
                        contentColor = DarkNavyContrast,
                        disabledContainerColor = NavySurface,
                        disabledContentColor = CreamDim,
                    ),
                ) {
                    Text(stringResource(com.destinyai.astrology.R.string.birth_save_changes), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }

                // R2-P17 Support-info block for birth data changes
                // iOS parity: BirthDetailsView.swift:223-261 — info-circle header,
                // multi-line subtitle, and prominent full-width gold email CTA button.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(NavySurface)
                        .border(0.5.dp, Gold.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = CreamDim,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = stringResource(com.destinyai.astrology.R.string.need_update_birth_data),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = CreamText,
                            )
                        }
                        Text(
                            text = stringResource(com.destinyai.astrology.R.string.contact_support_birth_data),
                            fontSize = 12.sp,
                            color = CreamDim,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                        )
                        val dob = state.dateOfBirth
                        val time = state.timeOfBirth
                        val city = state.cityOfBirth
                        val subject = stringResource(com.destinyai.astrology.R.string.birth_update_request_subject)
                        val body = stringResource(com.destinyai.astrology.R.string.birth_update_request_body, dob, time, city)
                        val supportEmail = stringResource(com.destinyai.astrology.R.string.waitlist_support_email)
                        val mailto = "mailto:$supportEmail?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}"
                        val supportLabel = stringResource(com.destinyai.astrology.R.string.birth_contact_support)
                        Button(
                            onClick = {
                                haptic.light()
                                // iOS parity: BirthDetailsView.swift:244-257, 336-346
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SENDTO, Uri.parse(mailto)),
                                        supportLabel,
                                    ),
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("birth_details_contact_support")
                                .semantics { contentDescription = "Contact Support" },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Gold,
                                contentColor = DarkNavyContrast,
                            ),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Email,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = supportEmail,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
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
                contentDescription = stringResource(com.destinyai.astrology.R.string.birth_read_only_cd),
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
