package com.destinyai.astrology.ui.compatibility

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.R
import com.destinyai.astrology.data.remote.PartnerDto
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.NavyVariant

/**
 * Mirrors iOS PartnerPickerSheet.swift — filters saved partners by gender,
 * excluded IDs (active profile + already-selected partners), forCompatibility
 * flag, and a search query (name or city). Provides an add-new affordance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerPickerSheet(
    viewModel: CompatibilityViewModel,
    onDismiss: () -> Unit,
    gender: String? = null,
    excludeIds: Set<String> = emptySet(),
    forCompatibilityOnly: Boolean = false,
    onAddNew: (() -> Unit)? = null,
) {
    val savedPartners by viewModel.savedPartners.collectAsStateWithLifecycle()
    val isLoading by viewModel.isSavedPartnersLoading.collectAsStateWithLifecycle()
    var searchText by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) { viewModel.loadSavedPartners() }

    // iOS parity (PartnerPickerSheet.swift:22-61): apply filter chain.
    val shouldExcludeSelf = excludeIds.contains("self")
    val filteredPartners: List<PartnerDto> = remember(savedPartners, searchText, gender, excludeIds, forCompatibilityOnly) {
        savedPartners
            .filter { p ->
                if (shouldExcludeSelf && p.isSelf) false
                else !excludeIds.contains(p.id)
            }
            .filter { p -> gender == null || p.gender == gender }
            .filter { p -> !forCompatibilityOnly || p.forCompatibility }
            .filter { p ->
                if (searchText.isBlank()) true
                else p.name.contains(searchText, ignoreCase = true) ||
                    (p.cityOfBirth?.contains(searchText, ignoreCase = true) == true)
            }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NavySurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
        ) {
            Text(
                text = stringResource(R.string.partner_picker_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Gold,
            )
            Spacer(Modifier.height(16.dp))

            // Search bar (iOS parity lines 135-155)
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.partner_picker_search_placeholder),
                        color = CreamDim,
                    )
                },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = Gold)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions.Default,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Gold,
                    unfocusedBorderColor = Gold.copy(alpha = 0.25f),
                    focusedTextColor = CreamText,
                    unfocusedTextColor = CreamText,
                    cursorColor = Gold,
                    unfocusedContainerColor = NavyVariant,
                    focusedContainerColor = NavyVariant,
                ),
            )
            Spacer(Modifier.height(12.dp))

            if (isLoading && savedPartners.isEmpty()) {
                // Loading branch — mirrors iOS spinner so the sheet doesn't flash
                // "No matches found" during the async loadSavedPartners() fetch.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Gold, strokeWidth = 2.dp)
                }
            } else if (filteredPartners.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (savedPartners.isEmpty()) {
                            stringResource(R.string.no_saved_birth_charts_yet)
                        } else {
                            stringResource(R.string.partner_picker_no_matches)
                        },
                        color = CreamDim,
                        fontSize = 15.sp,
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 360.dp),
                ) {
                    items(filteredPartners, key = { it.id }) { partner ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(NavySurface)
                                .border(0.5.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .semantics { contentDescription = "compat_saved_partner_row" }
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.selectSavedPartner(partner)
                                    onDismiss()
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // iOS parity (PartnerPickerSheet.swift:210-268): gold avatar circle
                            // with initial.
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Gold.copy(alpha = 0.85f), Gold.copy(alpha = 0.45f)),
                                        ),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = partner.name.trim().firstOrNull()?.uppercase() ?: "?",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0D0D1A),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = partner.name.ifEmpty { stringResource(R.string.partner_default_name) },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CreamText,
                                )
                                // Gender symbol + formatted DOB + city sub-row (iOS parity).
                                val genderSymbol = when (partner.gender.lowercase()) {
                                    "male", "m" -> "♂"
                                    "female", "f" -> "♀"
                                    else -> ""
                                }
                                val dobLabel = formatPickerDob(partner.dateOfBirth)
                                val subParts = listOfNotNull(
                                    genderSymbol.takeIf { it.isNotEmpty() },
                                    dobLabel.takeIf { it.isNotEmpty() },
                                    partner.cityOfBirth?.takeIf { it.isNotEmpty() },
                                )
                                if (subParts.isNotEmpty()) {
                                    Text(
                                        text = subParts.joinToString("  •  "),
                                        fontSize = 13.sp,
                                        color = CreamDim,
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = Gold.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            // Add-new affordance (iOS parity lines 270-295). Only shown when caller supplies handler.
            if (onAddNew != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(0.5.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .clickable {
                            onAddNew()
                            onDismiss()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        tint = Gold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.partner_picker_add_new),
                        color = Gold,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/** iOS parity (PartnerPickerSheet long-date DOB): format an ISO/`yyyy-MM-dd` DOB to a
 *  friendly "MMM d, yyyy"; returns empty on parse failure so the sub-row omits it. */
private fun formatPickerDob(dob: String?): String {
    val raw = dob?.takeIf { it.isNotBlank() } ?: return ""
    val patterns = listOf("yyyy-MM-dd", "yyyy-MM-dd'T'HH:mm:ss", "MM/dd/yyyy")
    for (p in patterns) {
        val parsed = runCatching {
            java.text.SimpleDateFormat(p, java.util.Locale.US).parse(raw)
        }.getOrNull()
        if (parsed != null) {
            return java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(parsed)
        }
    }
    return raw
}
