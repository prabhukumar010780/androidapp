package com.destinyai.astrology.ui.subscription

import android.app.Activity
import androidx.activity.compose.LocalActivity
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.R
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
    val hasActiveSub by viewModel.hasActiveSubscription.collectAsStateWithLifecycle()
    val activePlanId by viewModel.activePlanId.collectAsStateWithLifecycle()
    val conflict by viewModel.subscriptionConflict.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isPlusTrialEligible by viewModel.isPlusTrialEligible.collectAsStateWithLifecycle()

    val activity = LocalActivity.current as Activity

    LaunchedEffect(Unit) {
        viewModel.loadPlans()
        viewModel.loadCurrentPlan()
    }

    // Conflict banner dialog
    if (conflict != null) {
        AlertDialog(
            onDismissRequest = { /* intentionally non-dismissable via outside tap */ },
            title = { Text(stringResource(R.string.billing_conflict_title), color = Gold) },
            text = { Text(stringResource(R.string.billing_conflict_message), color = CreamDim) },
            confirmButton = {
                TextButton(onClick = { /* user must manage in Play Store */ }) {
                    Text(stringResource(R.string.billing_conflict_dismiss), color = Gold)
                }
            },
            containerColor = NavySurface,
        )
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
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cancel),
                        tint = CreamDim,
                    )
                }
                Text(
                    text = stringResource(R.string.choose_plan_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CanelaFontFamily,
                    color = Gold,
                    modifier = Modifier.weight(1f),
                )
            }

            if (state.isLoading && state.plans.isEmpty()) {
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
                                text = stringResource(R.string.unlock_premium),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = CanelaFontFamily,
                                color = Gold,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.get_cosmic_guidance),
                                style = MaterialTheme.typography.bodyMedium,
                                color = CreamDim,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    // Already-premium banner (server plan or Play active sub)
                    if (state.isPremium || hasActiveSub) {
                        item {
                            val planLabel = activePlanId ?: state.currentPlanId
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
                                    Text(
                                        "✦  You're already Premium!",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Gold,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text("Plan: $planLabel", fontSize = 13.sp, color = CreamDim)
                                }
                            }
                        }
                    } else {
                        // Plans from Play Billing (ProductDetails) when available,
                        // fallback to backend PlanDto list
                        items(state.plans.filter { !it.isFree }) { plan ->
                            val isYearly = plan.planId.contains("yearly", ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(NavySurface)
                                    .border(
                                        width = if (isYearly) 1.dp else 0.5.dp,
                                        color = if (isYearly) Gold else Gold.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(16.dp),
                                    )
                                    .padding(16.dp),
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column {
                                            Text(
                                                text = plan.displayName,
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = CanelaFontFamily,
                                                color = CreamText,
                                            )
                                            if (isYearly) {
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    text = stringResource(R.string.billing_free_trial),
                                                    fontSize = 11.sp,
                                                    color = Gold,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                            } else if (
                                                plan.planId.contains("plus", ignoreCase = true) &&
                                                isPlusTrialEligible
                                            ) {
                                                Spacer(Modifier.height(4.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Gold.copy(alpha = 0.15f))
                                                        .border(0.5.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                                ) {
                                                    Text(
                                                        text = "✦ Free Trial Available",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Gold,
                                                    )
                                                }
                                            }
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "$${if (isYearly) plan.priceYearly else plan.priceMonthly}/yr",
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Gold,
                                            )
                                            if (isYearly) {
                                                Text(
                                                    text = stringResource(R.string.billing_best_value),
                                                    fontSize = 11.sp,
                                                    color = Gold.copy(alpha = 0.8f),
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = if (plan.dailyQuota < 0) stringResource(R.string.unlimited) + " questions"
                                        else "${plan.dailyQuota} questions/day",
                                        fontSize = 13.sp,
                                        color = CreamDim,
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            // ProductDetails-based purchase is wired when
                                            // billingManager.products is non-empty; for now the
                                            // button is wired via the plan's product ID.
                                            // Full wiring happens in a future slice when
                                            // SubscriptionScreen receives ProductDetails from VM.
                                        },
                                        enabled = !isLoading,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Gold,
                                            contentColor = Color(0xFF0D0D1A),
                                            disabledContainerColor = Gold.copy(alpha = 0.4f),
                                        ),
                                    ) {
                                        if (isLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                color = Color(0xFF0D0D1A),
                                                strokeWidth = 2.dp,
                                            )
                                        } else {
                                            Text(
                                                stringResource(R.string.billing_subscribe),
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
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

                    // Restore Purchases
                    if (!state.isPremium && !hasActiveSub) {
                        item {
                            TextButton(
                                onClick = { viewModel.restorePurchases() },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = stringResource(R.string.restore_purchases),
                                    color = CreamDim,
                                    fontSize = 13.sp,
                                )
                            }
                        }

                        item {
                            Text(
                                text = stringResource(R.string.subscription_terms),
                                fontSize = 11.sp,
                                color = CreamDim.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                            )
                        }
                    }

                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }
    }
}
