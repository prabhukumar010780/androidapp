package com.destinyai.astrology.ui.partners

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.R
import com.destinyai.astrology.data.remote.PartnerDto
import com.destinyai.astrology.services.HapticManager
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.NavyVariant
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Partner Picker Sheet for the Partners area — mirrors iOS
 * PartnerPickerSheet.swift (Views/Partners/PartnerPickerSheet.swift).
 *
 * Differs from the compatibility-area PartnerPickerSheet (which is bound to
 * CompatibilityViewModel) by being decoupled — it takes an onSelect callback
 * so any caller (Compatibility flow, future flows) can reuse the same UI by
 * passing a different handler. Mirrors the iOS API:
 *   PartnerPickerSheet(gender, excludeIds, forCompatibilityOnly, onSelect)
 *
 * Filters the saved partners list by gender, excluded IDs (active profile +
 * already-selected partners), forCompatibility flag, and a search query
 * (name or city).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerPickerSheet(
    onDismiss: () -> Unit,
    onSelect: (PartnerDto) -> Unit,
    gender: String? = null,
    excludeIds: Set<String> = emptySet(),
    forCompatibilityOnly: Boolean = false,
    onAddNew: (() -> Unit)? = null,
    viewModel: PartnersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var searchText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val haptic = remember { HapticManager(context) }

    LaunchedEffect(Unit) { viewModel.loadPartners() }

    // iOS parity (PartnerPickerSheet.swift:22-61): apply filter chain.
    val shouldExcludeSelf = excludeIds.contains("self")
    val filtered: List<PartnerDto> = remember(
        state.partners,
        searchText,
        gender,
        excludeIds,
        forCompatibilityOnly,
    ) {
        state.partners
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
            // iOS parity (PartnerPickerSheet.swift:91-97): top-bar Cancel button so users
            // have a visible text affordance to dismiss instead of relying on drag/scrim.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.partner_picker_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Gold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        haptic.light()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("partner_picker_cancel"),
                ) {
                    Text(
                        text = stringResource(R.string.cancel_action),
                        color = CreamDim,
                        fontSize = 14.sp,
                    )
                }
            }
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

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (state.partners.isEmpty()) {
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
                    items(filtered, key = { it.id }) { partner ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(NavySurface)
                                .border(0.5.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .clickable {
                                    haptic.light()
                                    onSelect(partner)
                                    onDismiss()
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .testTag("partner_picker_row_${partner.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            // iOS parity (PartnerPickerSheet.swift:218-228): 44dp gold-gradient
                            // avatar circle with the partner's first initial.
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        brush = Brush.linearGradient(
                                            listOf(Gold, Color(0xFFF5D060)),
                                        ),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = partner.name.firstOrNull()?.uppercase() ?: "?",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0D0D1A),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                // iOS parity (PartnerPickerSheet.swift:232-234): no
                                // hardcoded fallback — empty name renders as empty.
                                Text(
                                    text = partner.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CreamText,
                                )
                                // iOS parity (PartnerPickerSheet.swift:236-251):
                                // gender symbol (gold gradient) + formatted DOB + city
                                // joined by " · " in a sub-row.
                                PartnerPickerInfoRow(partner = partner)
                            }
                            // iOS parity (PartnerPickerSheet.swift:256-258): trailing chevron
                            // suggesting forward navigation / tappable row.
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = stringResource(R.string.partner_chevron_cd),
                                tint = Gold.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }

            // Add-new affordance — iOS parity (PartnerPickerSheet.swift:270-295).
            // If caller doesn't pass onAddNew, fall back to PartnersViewModel's
            // requestAddPartner() so the user can still create a chart inline.
            val effectiveAddNew = onAddNew ?: { viewModel.requestAddPartner() }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(0.5.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .clickable {
                        effectiveAddNew()
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

/**
 * iOS parity (PartnerPickerSheet.swift:236-251) — sub-row showing the
 * partner's gender symbol (gold gradient), formatted date of birth, and
 * city, joined by " · " separators.
 */
@Composable
private fun PartnerPickerInfoRow(partner: PartnerDto) {
    val symbol = when (partner.gender) {
        "male" -> stringResource(R.string.partner_gender_symbol_male)
        "female" -> stringResource(R.string.partner_gender_symbol_female)
        else -> ""
    }
    val dob = formatPickerDob(partner.dateOfBirth)
    val city = partner.cityOfBirth?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    if (symbol.isEmpty() && dob.isNullOrEmpty() && city.isNullOrEmpty()) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (symbol.isNotEmpty()) {
            Text(
                text = symbol,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(
                    brush = Brush.linearGradient(listOf(Gold, Color(0xFFF5D060))),
                ),
            )
        }
        if (!dob.isNullOrEmpty()) {
            Text(text = dob, color = CreamDim, fontSize = 12.sp)
        }
        if (!city.isNullOrEmpty()) {
            if (!dob.isNullOrEmpty() || symbol.isNotEmpty()) {
                Text(text = "•", color = CreamDim, fontSize = 12.sp)
            }
            Text(text = city, color = CreamDim, fontSize = 12.sp, maxLines = 1)
        }
    }
}

private fun formatPickerDob(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return try {
        val inFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = inFmt.parse(raw) ?: return raw
        val outFmt = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
        outFmt.format(date)
    } catch (_: Exception) {
        raw
    }
}
