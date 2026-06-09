package com.destinyai.astrology.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.ui.theme.AppTheme
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.DarkNavyContrast
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.PremiumSelectionSheet
import com.destinyai.astrology.ui.theme.TextTertiary
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BirthDetailsScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: BirthDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = remember { com.destinyai.astrology.services.HapticManager(context) }
    val haptics = LocalHapticFeedback.current

    var showSaveDialog by remember { mutableStateOf(false) }
    var showGenderSheet by remember { mutableStateOf(false) }
    // Snackbar host to surface save failures — iOS BirthDetailsView never fails
    // (UserDefaults), but Android writes to DataStore + scoped prefs which can
    // throw. Audit found state.error is set in the VM but never rendered.
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailedMessage = stringResource(com.destinyai.astrology.R.string.birth_details_save_failed)
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(saveFailedMessage)
            viewModel.clearError()
        }
    }

    LaunchedEffect(Unit) { viewModel.loadBirthData() }
    // iOS parity (BirthDetailsView.swift:87-91): show alert dialog on save success;
    // navigation only happens after the user explicitly taps OK.
    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) {
            showSaveDialog = true
        }
    }

    // iOS parity: animate Save action color between gold (active) and tertiary (disabled).
    val saveColor by animateColorAsState(
        targetValue = if (state.hasChanges && state.name.isNotBlank() && !state.isSaving) {
            Gold
        } else {
            TextTertiary
        },
        label = "save_action_color",
    )

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // iOS parity (BirthDetailsView.swift:53-65): Save lives in the toolbar
            // confirmationAction slot; tinted gold when hasChanges, disabled otherwise.
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(com.destinyai.astrology.R.string.birth_details),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = CanelaFontFamily,
                        color = Gold,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .testTag("birth_details_back")
                            .semantics { contentDescription = "birth_details_back" },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(com.destinyai.astrology.R.string.birth_back),
                            tint = CreamDim,
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.saveName()
                        },
                        enabled = state.hasChanges && state.name.isNotBlank() && !state.isSaving,
                        modifier = Modifier
                            .testTag("birth_details_save")
                            .semantics { contentDescription = "birth_details_save" },
                    ) {
                        Text(
                            text = stringResource(com.destinyai.astrology.R.string.save),
                            color = saveColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            )
        },
    ) { innerPadding ->
        CosmicBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(Modifier.height(4.dp))

                // iOS parity (BirthDetailsView.swift:95-110): gold avatar circle
                // with person icon + 'your_birth_info' subtitle.
                BirthDetailsHeader()

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
                    // iOS parity (BirthDetailsView.swift:144-170): gender opens the
                    // PremiumSelectionSheet picker; the row itself is the affordance.
                    GenderSelectorRow(
                        label = stringResource(com.destinyai.astrology.R.string.birth_field_gender),
                        gender = state.gender,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            showGenderSheet = true
                        },
                    )
                }

                // R2-P14 "BIRTH DATA" section header
                SectionHeaderLabel(text = stringResource(com.destinyai.astrology.R.string.birth_section_birth_data))

                // R2-P15 Read-only section with leading icons + Lock trailing
                BirthDetailsSection(title = stringResource(com.destinyai.astrology.R.string.birth_section_birth_data_title)) {
                    val notSet = stringResource(com.destinyai.astrology.R.string.not_set)
                    BirthDetailRow(
                        leadingIcon = Icons.Filled.CalendarToday,
                        label = stringResource(com.destinyai.astrology.R.string.date_of_birth),
                        value = formatDateLong(state.dateOfBirth).ifEmpty { notSet },
                        readOnly = true,
                    )
                    // iOS parity (BirthDetailsView.swift:300-313): when the
                    // user-scoped birthTimeUnknown flag is set, render the
                    // "birth_time_unknown" label instead of the formatted time.
                    val timeDisplay = when {
                        state.birthTimeUnknown -> stringResource(com.destinyai.astrology.R.string.birth_time_unknown)
                        state.timeOfBirth.isNotEmpty() -> formatTimeAmPm(state.timeOfBirth)
                        else -> notSet
                    }
                    BirthDetailRow(
                        leadingIcon = Icons.Filled.Schedule,
                        label = stringResource(com.destinyai.astrology.R.string.time_of_birth),
                        value = timeDisplay,
                        readOnly = true,
                    )
                    BirthDetailRow(
                        leadingIcon = Icons.Filled.LocationOn,
                        label = stringResource(com.destinyai.astrology.R.string.place_of_birth),
                        value = state.cityOfBirth.ifEmpty { notSet },
                        readOnly = true,
                    )
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
                                .semantics { contentDescription = "birth_details_contact_support" },
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

    // iOS parity (BirthDetailsView.swift:68-84): gender PremiumSelectionSheet picker.
    if (showGenderSheet) {
        PremiumSelectionSheet(
            title = stringResource(com.destinyai.astrology.R.string.select_gender),
            selectedValue = state.gender,
            options = listOf(
                "" to stringResource(com.destinyai.astrology.R.string.gender_prefer_not_say),
                "male" to stringResource(com.destinyai.astrology.R.string.gender_male),
                "female" to stringResource(com.destinyai.astrology.R.string.gender_female),
                "non-binary" to stringResource(com.destinyai.astrology.R.string.gender_non_binary),
            ),
            onSelect = { value -> viewModel.setGender(value) },
            onDismiss = { showGenderSheet = false },
        )
    }

    // iOS parity (BirthDetailsView.swift:87-91): modal alert with explicit OK
    // before navigation. User must acknowledge the save before we dismiss.
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = {
                showSaveDialog = false
                onSaved()
            },
            title = {
                Text(
                    text = stringResource(com.destinyai.astrology.R.string.changes_saved),
                    fontFamily = CanelaFontFamily,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(text = stringResource(com.destinyai.astrology.R.string.name_gender_updated))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSaveDialog = false
                        onSaved()
                    },
                    modifier = Modifier
                        .testTag("birth_details_save_ok")
                        .semantics { contentDescription = "birth_details_save_ok" },
                ) {
                    Text(
                        text = stringResource(com.destinyai.astrology.R.string.ok),
                        color = Gold,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            containerColor = NavySurface,
            titleContentColor = CreamText,
            textContentColor = CreamDim,
        )
    }
}

@Composable
private fun BirthDetailsHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Gold.copy(alpha = 0.1f))
                .border(1.dp, Gold.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = Gold,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = stringResource(com.destinyai.astrology.R.string.your_birth_info),
            fontSize = 14.sp,
            fontFamily = CanelaFontFamily,
            color = CreamText,
            modifier = Modifier.semantics { contentDescription = "your_birth_info_label" },
        )
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
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Gold.copy(alpha = 0.7f),
        )
        content()
    }
}

@Composable
private fun BirthDetailRow(
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    label: String,
    value: String,
    readOnly: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = CreamDim,
                modifier = Modifier
                    .size(14.dp)
                    .semantics { contentDescription = "birth_row_icon" },
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = label,
            fontSize = 14.sp,
            color = CreamDim,
            modifier = Modifier.weight(1f),
        )
        if (readOnly) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = stringResource(com.destinyai.astrology.R.string.birth_read_only_cd),
                tint = CreamDim.copy(alpha = 0.4f),
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = value,
            fontSize = 14.sp,
            color = CreamText,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun GenderSelectorRow(
    label: String,
    gender: String,
    onClick: () -> Unit,
) {
    val display = when (gender) {
        "male" -> stringResource(com.destinyai.astrology.R.string.gender_male)
        "female" -> stringResource(com.destinyai.astrology.R.string.gender_female)
        "non-binary" -> stringResource(com.destinyai.astrology.R.string.gender_non_binary)
        else -> stringResource(com.destinyai.astrology.R.string.gender_prefer_not_say)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AppTheme.colors.inputBackground)
            .border(1.dp, Gold.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
            .testTag("birth_details_gender_row")
            .semantics { contentDescription = "birth_details_gender_row" }
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = CreamDim,
        )
        Text(
            text = display,
            fontSize = 14.sp,
            color = CreamText,
            fontWeight = FontWeight.SemiBold,
        )
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
        modifier = Modifier
            .fillMaxWidth()
            .testTag("birth_details_name_field")
            .semantics { contentDescription = "birth_details_name_field" },
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

// iOS parity (BirthDetailsView.swift:286-297): format DOB in a long, locale-aware
// style (e.g. "July 1, 1994") to avoid DD/MM vs MM/DD ambiguity.
private fun formatDateLong(raw: String): String {
    if (raw.isBlank()) return ""
    return runCatching {
        val date = LocalDate.parse(raw, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.getDefault()))
    }.getOrDefault(raw)
}

// iOS parity (BirthDetailsView.swift:299-313): force en_US AM/PM display so time
// reads "9:30 AM" regardless of device locale.
private fun formatTimeAmPm(raw: String): String {
    if (raw.isBlank()) return ""
    return runCatching {
        val time = LocalTime.parse(raw, DateTimeFormatter.ofPattern("HH:mm"))
        time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
    }.getOrDefault(raw)
}
