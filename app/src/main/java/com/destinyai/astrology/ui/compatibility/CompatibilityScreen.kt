package com.destinyai.astrology.ui.compatibility

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.NavyVariant

@Composable
fun CompatibilityScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onNavigateToPartners: () -> Unit,
    viewModel: CompatibilityViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadUserData() }

    CosmicBackground(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            CompatibilityHeader(onNavigateToPartners = onNavigateToPartners)

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
                    // You card
                    YouCard(name = state.personAName)

                    // Partner form section
                    SectionHeader(title = "Partner Details")

                    CosmicTextField(
                        value = state.partnerName,
                        onValueChange = viewModel::setPartnerName,
                        label = "Partner Name",
                    )
                    CosmicTextField(
                        value = state.partnerDob,
                        onValueChange = viewModel::setPartnerDob,
                        label = "Date of Birth (YYYY-MM-DD)",
                        keyboardType = KeyboardType.Number,
                    )
                    CosmicTextField(
                        value = state.partnerTime,
                        onValueChange = viewModel::setPartnerTime,
                        label = "Time of Birth (HH:MM)",
                        keyboardType = KeyboardType.Number,
                    )
                    CosmicTextField(
                        value = state.partnerCity,
                        onValueChange = { viewModel.setPartnerLocation(it, 0.0, 0.0) },
                        label = "City of Birth",
                    )
                }

                // Result card
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

                // Analyze button
                Button(
                    onClick = { viewModel.analyze() },
                    enabled = state.canAnalyze && !state.isAnalyzing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Gold,
                        disabledContainerColor = NavyVariant,
                        contentColor = Color(0xFF0D0D1A),
                        disabledContentColor = Color(0xFF718096),
                    ),
                ) {
                    if (state.isAnalyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color(0xFF0D0D1A),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Analyze Compatibility", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun CompatibilityHeader(onNavigateToPartners: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "❤️  Compatibility",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = CreamText,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNavigateToPartners) {
            Icon(Icons.Filled.People, contentDescription = "Partners", tint = Gold.copy(alpha = 0.8f))
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
private fun YouCard(name: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(listOf(NavySurface, NavyVariant))
            )
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
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color(0xFF718096)) },
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

@Composable
private fun CompatibilityResultCard(score: Int?, result: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(listOf(NavySurface, NavyVariant))
            )
            .border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(20.dp),
    ) {
        Column {
            if (score != null) {
                Text(
                    text = "$score%",
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
