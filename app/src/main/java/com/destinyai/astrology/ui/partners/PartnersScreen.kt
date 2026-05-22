package com.destinyai.astrology.ui.partners

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
fun PartnersScreen(
    onBack: () -> Unit,
    viewModel: PartnersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadPartners() }

    CosmicBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CreamDim)
                }
                Text(
                    text = "Saved Birth Charts",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CanelaFontFamily,
                    color = CreamText,
                    modifier = Modifier.weight(1f),
                )
                // Add button (gold pill)
                Button(
                    onClick = { viewModel.toggleAddForm() },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Gold,
                        contentColor = Color(0xFF0D0D1A),
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.height(36.dp),
                ) {
                    Text("+ Add", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }

            // Add form
            if (state.showAddForm) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(NavySurface)
                        .border(0.5.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Add Partner",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Gold.copy(alpha = 0.7f),
                        )
                        PartnerTextField(value = state.formName, onValueChange = viewModel::setFormName, label = "Name")
                        PartnerTextField(
                            value = state.formDob, onValueChange = viewModel::setFormDob,
                            label = "Date of Birth (YYYY-MM-DD)", keyboardType = KeyboardType.Number,
                        )
                        PartnerTextField(
                            value = state.formTime, onValueChange = viewModel::setFormTime,
                            label = "Time of Birth (HH:MM)", keyboardType = KeyboardType.Number,
                        )
                        PartnerTextField(
                            value = state.formCity,
                            onValueChange = { viewModel.setFormLocation(it, 0.0, 0.0) },
                            label = "City of Birth",
                        )
                        if (state.error != null) {
                            Text(text = state.error ?: "", color = Color(0xFFFF8A80), fontSize = 13.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.toggleAddForm() },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = ButtonDefaults.outlinedButtonBorder(enabled = true),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CreamDim),
                            ) { Text("Cancel") }
                            Button(
                                onClick = { viewModel.addPartner() },
                                enabled = state.isFormValid && !state.isSaving,
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Gold, contentColor = Color(0xFF0D0D1A),
                                    disabledContainerColor = NavyVariant, disabledContentColor = CreamDim,
                                ),
                            ) {
                                if (state.isSaving) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF0D0D1A), strokeWidth = 2.dp)
                                } else {
                                    Text("Save", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Gold, modifier = Modifier.size(28.dp))
                }
            } else if (state.partners.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "No partners saved yet", color = CreamDim, fontSize = 16.sp)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(state.partners, key = { it.id }) { partner ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(NavySurface)
                                .border(0.5.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Avatar with gradient circle + initial
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(Gold, Color(0xFFF5D060)))),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = partner.name.firstOrNull()?.uppercase() ?: "?",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0D0D1A),
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = partner.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CreamText,
                                )
                                Text(
                                    text = "${partner.dateOfBirth} · ${partner.cityOfBirth}",
                                    fontSize = 13.sp,
                                    color = CreamDim,
                                )
                            }
                            IconButton(onClick = { viewModel.deletePartner(partner.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color(0xFFFF5252))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PartnerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = CreamDim) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
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
