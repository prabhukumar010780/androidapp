package com.destinyai.astrology.ui.compatibility

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
    var searchText by remember { mutableStateOf("") }

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

            if (filteredPartners.isEmpty()) {
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
                                .clickable {
                                    viewModel.selectSavedPartner(partner)
                                    onDismiss()
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = partner.name.ifEmpty { "Partner" },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CreamText,
                                )
                                if (!partner.cityOfBirth.isNullOrEmpty()) {
                                    Text(
                                        text = partner.cityOfBirth,
                                        fontSize = 13.sp,
                                        color = CreamDim,
                                    )
                                }
                            }
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
