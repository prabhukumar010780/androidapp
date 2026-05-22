package com.destinyai.astrology.ui.subscription

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
fun SubscriptionScreen(
    onBack: () -> Unit,
    viewModel: SubscriptionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadPlans()
        viewModel.loadCurrentPlan()
    }

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
                    text = "Choose Plan",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CanelaFontFamily,
                    color = Gold,
                    modifier = Modifier.weight(1f),
                )
            }

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Gold, modifier = Modifier.size(28.dp))
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(text = "✦", fontSize = 56.sp, color = Gold)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Unlock Unlimited Insights",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = CanelaFontFamily,
                                color = Gold,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Get unlimited daily questions and exclusive features.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CreamDim,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    if (state.isPremium) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Brush.linearGradient(listOf(NavySurface, NavyVariant)))
                                    .border(1.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("✦  You're already Premium!", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Gold)
                                    Spacer(Modifier.height(4.dp))
                                    Text("Plan: ${state.currentPlanId}", fontSize = 13.sp, color = CreamDim)
                                }
                            }
                        }
                    } else {
                        items(state.plans) { plan ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(NavySurface)
                                    .border(0.5.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                    .padding(16.dp),
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = plan.displayName,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = CanelaFontFamily,
                                            color = CreamText,
                                        )
                                        Text(
                                            text = if (plan.isFree) "Free" else "$${plan.priceMonthly}/mo",
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Gold,
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = if (plan.dailyQuota < 0) "Unlimited questions" else "${plan.dailyQuota} questions/day",
                                        fontSize = 13.sp,
                                        color = CreamDim,
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Button(
                                        onClick = { /* Google Play Billing */ },
                                        modifier = Modifier.fillMaxWidth().height(44.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Gold,
                                            contentColor = Color(0xFF0D0D1A),
                                        ),
                                    ) {
                                        Text("Subscribe", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }

                    if (state.error != null) {
                        item {
                            Text(text = state.error ?: "", color = Color(0xFFFF8A80), fontSize = 13.sp)
                        }
                    }

                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }
    }
}
