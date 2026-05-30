package com.destinyai.astrology.ui.compatibility

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.ExperimentalMaterial3Api
import com.destinyai.astrology.domain.model.AnalysisStep
import com.destinyai.astrology.domain.model.PartnerData
import com.destinyai.astrology.ui.subscription.SubscriptionScreen
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.NavyVariant
import java.util.Calendar

@Composable
fun CompatibilityScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onNavigateToPartners: () -> Unit,
    onNavigateToHistory: (() -> Unit)? = null,
    viewModel: CompatibilityViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val partners by viewModel.partners.collectAsStateWithLifecycle()
    val activePartnerIndex by viewModel.activePartnerIndex.collectAsStateWithLifecycle()
    val hasFailedPartners by viewModel.hasFailedPartners.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.loadUserData() }

    val today = remember {
        val c = Calendar.getInstance()
        "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    // Date picker
    val calendar = Calendar.getInstance()
    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, day ->
                viewModel.setPartnerDob("%04d-%02d-%02d".format(year, month + 1, day))
                viewModel.setShowDatePicker(false)
            },
            calendar.get(Calendar.YEAR) - 25,
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
        ).apply { datePicker.maxDate = System.currentTimeMillis() }
    }

    // Time picker
    val timePickerDialog = remember {
        TimePickerDialog(
            context,
            { _, hour, minute ->
                viewModel.setPartnerTime("%02d:%02d".format(hour, minute))
                viewModel.setShowTimePicker(false)
            },
            12, 0, true,
        )
    }

    LaunchedEffect(state.showDatePicker) { if (state.showDatePicker) datePickerDialog.show() }
    LaunchedEffect(state.showTimePicker) { if (state.showTimePicker) timePickerDialog.show() }

    // Gender bottom sheet
    var showGenderSheet by remember { mutableStateOf(false) }

    // Streaming overlay — show over form while analyzing
    if (state.showStreamingView) {
        if (partners.size > 1) {
            MultiPartnerStreamingView(
                isVisible = true,
                partners = partners,
                completedResults = emptyList(),
                currentPartnerIndex = activePartnerIndex,
                currentStep = AnalysisStep.CALCULATING_CHARTS,
                totalPartners = partners.count { it.isComplete },
            )
            return
        } else {
            CompatibilityStreamingView(
                isVisible = true,
                currentStep = AnalysisStep.CALCULATING_CHARTS,
                streamingText = "",
            )
            return
        }
    }

    // Comparison overview after multi-partner analysis
    if (state.showComparisonOverview) {
        ComparisonOverviewView(
            results = emptyList(),
            userName = state.personAName.ifEmpty { "You" },
            onSelectPartner = {},
            onBack = { viewModel.setShowComparisonOverview(false) },
            onNewMatch = {
                viewModel.clearResult()
                viewModel.setShowComparisonOverview(false)
            },
        )
        return
    }

    CosmicBackground(modifier = modifier) {
        Column(modifier = Modifier
            .fillMaxSize()
            .semantics(mergeDescendants = true) { contentDescription = "compat_screen" }) {
            CompatibilityHeader(
                onNavigateToPartners = onNavigateToPartners,
                onHistory = {
                    viewModel.loadHistory()
                    onNavigateToHistory?.invoke()
                },
                onReset = { viewModel.resetPartnerForm() },
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(Modifier.height(8.dp))

                if (!state.personALoaded) {
                    BirthDataWarningCard()
                } else {
                    YouCard(
                        name = state.personAName,
                        summary = formattedUserSummary(
                            name = state.personAName,
                            gender = state.personAGender,
                            dob = state.personADob,
                            time = state.personATime,
                            city = state.personACity,
                            timeUnknown = state.personATimeUnknown,
                        ),
                    )

                    // Age block banner — shown prominently between user card and form
                    val bannerAgeMessage = ageBlockMessage(
                        userDob = state.personADob,
                        partnerDob = state.partnerDob,
                        today = today,
                    )
                    if (ageBlockBannerVisible(bannerAgeMessage)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFED8936).copy(alpha = 0.15f))
                                .border(1.dp, Color(0xFFED8936).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("⚠️", fontSize = 18.sp)
                            Text(
                                text = bannerAgeMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFED8936),
                                lineHeight = 18.sp,
                            )
                        }
                    }

                    if (partners.size >= 1) {
                        PartnerTabStrip(
                            partners = partners,
                            activeIndex = activePartnerIndex,
                            onSelectPartner = viewModel::selectPartner,
                            onAddPartner = viewModel::addPartner,
                            onRemovePartner = viewModel::removePartner,
                        )
                    }

                    SectionHeader(title = "Partner Details")

                    // Name + Gender row (with saved partner picker icon)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CosmicTextField(
                            value = state.partnerName,
                            onValueChange = viewModel::setPartnerName,
                            label = "Name",
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { viewModel.showPartnerPicker() },
                            modifier = Modifier
                                .size(48.dp)
                                .border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                        ) {
                            Icon(
                                Icons.Filled.PersonAdd,
                                contentDescription = "Load saved partner",
                                tint = Gold,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        // Gender picker button
                        PickerField(
                            icon = Icons.Filled.Person,
                            label = if (state.partnerGender.isEmpty()) "Gender" else state.partnerGender.replaceFirstChar { it.uppercase() },
                            isPlaceholder = state.partnerGender.isEmpty(),
                            modifier = Modifier.width(130.dp),
                            onClick = { showGenderSheet = true },
                        )
                    }

                    // Date + Time row
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PickerField(
                            icon = Icons.Filled.CalendarMonth,
                            label = if (state.partnerDob.isEmpty()) "Date of Birth" else state.partnerDob,
                            isPlaceholder = state.partnerDob.isEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .semantics { contentDescription = "compat_person2_dob" },
                            onClick = { viewModel.setShowDatePicker(true) },
                        )
                        PickerField(
                            icon = Icons.Filled.Schedule,
                            label = when {
                                state.partnerTimeUnknown -> "Unknown"
                                state.partnerTime.isEmpty() -> "Time of Birth"
                                else -> state.partnerTime
                            },
                            isPlaceholder = state.partnerTime.isEmpty() && !state.partnerTimeUnknown,
                            modifier = Modifier.width(130.dp),
                            onClick = {
                                if (!state.partnerTimeUnknown) viewModel.setShowTimePicker(true)
                            },
                            enabled = !state.partnerTimeUnknown,
                        )
                    }

                    // City / Location picker
                    PickerField(
                        icon = Icons.Filled.LocationOn,
                        label = if (state.partnerCity.isEmpty()) "City of Birth" else state.partnerCity,
                        isPlaceholder = state.partnerCity.isEmpty(),
                        onClick = { viewModel.setShowLocationSearch(true) },
                    )

                    // Inline location search dialog
                    if (state.showLocationSearch) {
                        LocationSearchDialog(
                            onLocationSelected = { city, lat, lon ->
                                viewModel.setPartnerLocation(city, lat, lon)
                                viewModel.setShowLocationSearch(false)
                            },
                            onDismiss = { viewModel.setShowLocationSearch(false) },
                        )
                    }

                    // Time unknown + save row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                viewModel.setPartnerTimeUnknown(!state.partnerTimeUnknown)
                            },
                        ) {
                            Checkbox(
                                checked = state.partnerTimeUnknown,
                                onCheckedChange = { viewModel.setPartnerTimeUnknown(it) },
                                colors = CheckboxDefaults.colors(checkedColor = Gold),
                            )
                            Text(
                                text = "Time unknown",
                                style = MaterialTheme.typography.labelSmall,
                                color = CreamDim,
                            )
                        }
                    }

                    if (state.partnerTimeUnknown) {
                        Text(
                            text = "Compatibility uses sun-sign approximation when time unknown.",
                            style = MaterialTheme.typography.labelSmall,
                            fontStyle = FontStyle.Italic,
                            color = CreamDim,
                        )
                    }
                }

                if (state.result.isNotEmpty()) {
                    CompatibilityResultCard(score = state.score, result = state.result)
                }

                if (state.error != null) {
                    Text(
                        text = state.error ?: "",
                        color = Color(0xFFFF8A80),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (hasFailedPartners) {
                    OutlinedButton(
                        onClick = viewModel::retryFailedPartners,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(26.dp),
                        border = BorderStroke(1.dp, Gold.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Gold),
                    ) {
                        Text("Retry Failed", fontWeight = FontWeight.SemiBold)
                    }
                }

                // R2-CM5: Save partner to birth charts checkbox
                // Hidden if user is Free plan OR partner was loaded from saved charts
                if (state.isPlus && !state.partnerFromSaved) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            viewModel.setSavePartnerToBirthCharts(!state.savePartnerToBirthCharts)
                        },
                    ) {
                        Checkbox(
                            checked = state.savePartnerToBirthCharts,
                            onCheckedChange = { viewModel.setSavePartnerToBirthCharts(it) },
                            colors = CheckboxDefaults.colors(checkedColor = Gold),
                        )
                        Text(
                            text = "Save partner to my birth charts",
                            style = MaterialTheme.typography.labelSmall,
                            color = CreamDim,
                        )
                    }
                }

                // R2-CM6: Duplicate alert dialog
                if (state.showDuplicateAlert) {
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissDuplicateAlert() },
                        containerColor = NavySurface,
                        title = {
                            Text(
                                "Partner already saved",
                                style = MaterialTheme.typography.titleMedium,
                                color = CreamText,
                            )
                        },
                        text = {
                            Text(
                                "This partner is already in your history. Would you like to use the saved entry or continue?",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CreamDim,
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { viewModel.dismissDuplicateAlert(); viewModel.analyze() }) {
                                Text("Save anyway", color = Gold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { viewModel.dismissDuplicateAlert() }) {
                                Text("Use saved", color = CreamDim)
                            }
                        },
                    )
                }

                // R2-CM7: ShimmerButton replaces plain Button
                val completedCount = partners.count { it.isComplete }
                val ageWarning = ageBlockMessage(
                    userDob = state.personADob,
                    partnerDob = state.partnerDob,
                    today = today,
                )
                if (ageWarning != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = ageWarning,
                            style = MaterialTheme.typography.labelSmall,
                            color = CreamDim,
                        )
                    }
                }
                if (state.isAnalyzing) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Gold,
                            strokeWidth = 2.dp,
                        )
                    }
                } else {
                    ShimmerButton(
                        text = analyzeButtonTitle(completedCount, isAnalyzing = false),
                        onClick = {
                            if (state.canAnalyze && ageWarning == null) {
                                val dupId = viewModel.checkForDuplicate()
                                if (dupId != null) {
                                    viewModel.showDuplicateAlert(dupId)
                                } else {
                                    viewModel.analyze()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = analyzeButtonContentDescription() },
                        enabled = state.canAnalyze && ageWarning == null,
                    )
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }

    // Gender selection sheet
    if (showGenderSheet) {
        GenderSelectionSheet(
            selected = state.partnerGender,
            onSelect = { gender ->
                viewModel.setPartnerGender(gender)
                showGenderSheet = false
            },
            onDismiss = { showGenderSheet = false },
        )
    }

    // Saved partner picker sheet
    if (state.showPartnerPicker) {
        PartnerPickerSheet(
            viewModel = viewModel,
            onDismiss = { viewModel.dismissPartnerPicker() },
        )
    }

    if (state.showPaywall) {
        SubscriptionScreen(
            onBack = { viewModel.dismissPaywall() },
        )
    }
}

@Composable
private fun CompatibilityHeader(
    onNavigateToPartners: () -> Unit,
    onHistory: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // History icon — left
        IconButton(
            onClick = onHistory,
            modifier = Modifier.semantics { contentDescription = "compat_history_button" },
        ) {
            Icon(Icons.Filled.History, contentDescription = null, tint = Gold.copy(alpha = 0.8f))
        }
        // Title — centre
        Text(
            text = "❤️  Compatibility",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = CreamText,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        // Partners icon
        IconButton(onClick = onNavigateToPartners) {
            Icon(Icons.Filled.People, contentDescription = "Partners", tint = Gold.copy(alpha = 0.8f))
        }
        // Reset icon — right
        IconButton(
            onClick = onReset,
            modifier = Modifier.semantics { contentDescription = "compat_reset_button" },
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = "Reset form", tint = Gold.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun BirthDataWarningCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NavySurface)
            .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Text(
            text = "✦  Save your birth details first to run compatibility analysis.",
            style = MaterialTheme.typography.bodyMedium,
            color = CreamDim,
        )
    }
}

@Composable
private fun YouCard(name: String, summary: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(NavySurface, NavyVariant)))
            .border(0.5.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "👤", fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "You",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF718096),
                )
                Text(
                    text = name.ifEmpty { "Me" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = CreamText,
                )
                if (summary.isNotEmpty()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = CreamDim,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = Gold.copy(alpha = 0.7f),
    )
}

@Composable
private fun CosmicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color(0xFF718096)) },
        modifier = modifier.fillMaxWidth(),
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

@Composable
private fun PickerField(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isPlaceholder: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .then(if (modifier == Modifier) Modifier.fillMaxWidth() else modifier)
            .clip(RoundedCornerShape(12.dp))
            .background(NavySurface.copy(alpha = if (enabled) 1f else 0.5f))
            .border(1.dp, Gold.copy(alpha = if (enabled) 0.25f else 0.1f), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) Gold.copy(alpha = 0.7f) else Gold.copy(alpha = 0.3f),
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isPlaceholder || !enabled) Color(0xFF718096) else CreamText,
                maxLines = 1,
            )
        }
    }
}

internal fun analyzeButtonContentDescription(): String = "compat_analyze_button"

internal fun partnerTabLabel(index: Int): String = "Partner ${index + 1}"

internal fun analyzeButtonTitle(completedCount: Int, isAnalyzing: Boolean): String = when {
    isAnalyzing -> "Analyzing…"
    completedCount > 1 -> "Compare All ($completedCount)"
    else -> "Analyze Compatibility"
}

internal fun formattedUserSummary(
    name: String,
    gender: String,
    dob: String,
    time: String,
    city: String,
    timeUnknown: Boolean,
): String {
    val parts = buildList {
        if (name.isNotEmpty()) add(name)
        if (gender.isNotEmpty()) add(gender.replaceFirstChar { it.uppercase() })
        if (dob.isNotEmpty()) add(dob)
        if (!timeUnknown && time.isNotEmpty()) add(time)
        if (city.isNotEmpty()) add(city)
    }
    return parts.joinToString(" · ")
}

internal fun ageBlockMessage(userDob: String, partnerDob: String, today: String): String? {
    if (userDob.isEmpty() || partnerDob.isEmpty()) return null
    fun isMinor(dob: String): Boolean {
        return try {
            val parts = dob.split("-")
            val todayParts = today.split("-")
            val birthYear = parts[0].toInt()
            val birthMonth = parts[1].toInt()
            val birthDay = parts[2].toInt()
            val todayYear = todayParts[0].toInt()
            val todayMonth = todayParts[1].toInt()
            val todayDay = todayParts[2].toInt()
            val age = todayYear - birthYear -
                if (todayMonth < birthMonth || (todayMonth == birthMonth && todayDay < birthDay)) 1 else 0
            age < 18
        } catch (_: Exception) { false }
    }
    return if (isMinor(userDob) || isMinor(partnerDob))
        "Destiny matching requires both individuals to be 18 or older"
    else null
}

@Composable
private fun PartnerTabStrip(
    partners: List<PartnerData>,
    activeIndex: Int,
    onSelectPartner: (Int) -> Unit,
    onAddPartner: () -> Unit,
    onRemovePartner: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        partners.forEachIndexed { index, partner ->
            val isActive = index == activeIndex
            FilterChip(
                selected = isActive,
                onClick = { onSelectPartner(index) },
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = partnerTabLabel(index),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        if (partner.isComplete) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(10.dp))
                        }
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Gold,
                    selectedLabelColor = Color(0xFF0D0D1A),
                    containerColor = Color.Transparent,
                    labelColor = Gold,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isActive,
                    borderColor = Gold.copy(alpha = 0.5f),
                    selectedBorderColor = Gold,
                ),
            )
        }

        if (partners.size < 3) {
            IconButton(
                onClick = onAddPartner,
                modifier = Modifier
                    .size(32.dp)
                    .border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add partner", tint = Gold, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(Modifier.weight(1f))

        if (partners.size > 1) {
            IconButton(
                onClick = { onRemovePartner(activeIndex) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove partner", tint = Color(0xFFFC8181).copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun CompatibilityResultCard(score: Int?, result: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(NavySurface, NavyVariant)))
            .border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(20.dp),
    ) {
        Column {
            if (score != null) {
                Text(
                    text = compatibilityScoreLabel(score, 36),
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    color = Gold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Compatibility Score",
                    style = MaterialTheme.typography.labelMedium,
                    color = CreamDim,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
            }
            Text(
                text = result,
                style = MaterialTheme.typography.bodyMedium,
                color = CreamText,
                lineHeight = 22.sp,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenderSelectionSheet(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf("male" to "Male", "female" to "Female", "non-binary" to "Non-Binary", "prefer_not_to_say" to "Prefer not to say")
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = NavySurface) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Gender Identity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = CreamText)
            Spacer(Modifier.height(8.dp))
            options.forEach { (key, display) ->
                val isSelected = selected == key
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) Gold.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable { onSelect(key) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(display, style = MaterialTheme.typography.bodyMedium, color = if (isSelected) Gold else CreamText, modifier = Modifier.weight(1f))
                    if (isSelected) Icon(Icons.Filled.Check, contentDescription = null, tint = Gold, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// Simple city-name entry dialog — mirrors iOS LocationSearchView sheet
@Composable
private fun LocationSearchDialog(
    onLocationSelected: (city: String, lat: Double, lon: Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var cityInput by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavySurface,
        title = {
            Text("City of Birth", style = MaterialTheme.typography.titleMedium, color = CreamText)
        },
        text = {
            OutlinedTextField(
                value = cityInput,
                onValueChange = { cityInput = it },
                label = { Text("Enter city name", color = Color(0xFF718096)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Gold,
                    unfocusedBorderColor = Gold.copy(alpha = 0.25f),
                    focusedTextColor = CreamText,
                    unfocusedTextColor = CreamText,
                    cursorColor = Gold,
                    unfocusedContainerColor = NavySurface,
                    focusedContainerColor = NavySurface,
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (cityInput.isNotBlank()) {
                        onLocationSelected(cityInput.trim(), 0.0, 0.0)
                    }
                },
                enabled = cityInput.isNotBlank(),
            ) {
                Text("Select", color = Gold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = CreamDim)
            }
        },
    )
}

internal fun ageBlockBannerVisible(ageMessage: String?): Boolean =
    !ageMessage.isNullOrBlank()
