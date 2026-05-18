package com.destinyai.astrology.ui.charts

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
fun ChartsScreen(
    onBack: () -> Unit,
    viewModel: ChartsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadChartData() }

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
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = CreamDim,
                    )
                }
                Text(
                    text = "Birth Chart",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CanelaFontFamily,
                    color = Gold,
                    modifier = Modifier.weight(1f),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(Modifier.height(4.dp))

                if (!state.hasData) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(NavySurface)
                            .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "🪐", fontSize = 48.sp)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "No birth chart yet",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = CanelaFontFamily,
                                color = Gold,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Save your birth details to generate your Vedic birth chart.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CreamDim,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else {
                    // Birth details card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(NavySurface)
                            .border(0.5.dp, Gold.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                            .padding(16.dp),
                    ) {
                        Column {
                            Text(
                                text = "Birth Details",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Gold.copy(alpha = 0.7f),
                            )
                            Spacer(Modifier.height(8.dp))
                            ChartDetailRow("Date", state.dateOfBirth)
                            if (!state.timeUnknown) {
                                ChartDetailRow("Time", state.timeOfBirth)
                            }
                            ChartDetailRow("City", state.cityOfBirth)
                        }
                    }

                    // Chart card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(listOf(NavySurface, NavyVariant)))
                            .border(0.5.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "✦", fontSize = 64.sp, color = Gold)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Your Vedic Chart",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = CanelaFontFamily,
                                color = Gold,
                            )
                            Text(
                                text = "Chart style: ${state.chartStyle.replaceFirstChar { it.uppercase() }}",
                                fontSize = 13.sp,
                                color = CreamDim,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Full interactive chart coming soon.\nAsk the AI about your planets and houses in the Chat tab.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CreamDim,
                                textAlign = TextAlign.Center,
                                lineHeight = 22.sp,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ChartDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, fontSize = 13.sp, color = CreamDim)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CreamText)
    }
}
