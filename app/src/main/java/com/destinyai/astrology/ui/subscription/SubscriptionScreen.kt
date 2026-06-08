package com.destinyai.astrology.ui.subscription

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.R
import com.destinyai.astrology.data.billing.RestoreResult
import com.destinyai.astrology.services.HapticManager
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.NavyVariant
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
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
    val products by viewModel.products.collectAsStateWithLifecycle()
    val restoreResult by viewModel.restoreResult.collectAsStateWithLifecycle()
    // iOS parity (SubscriptionView.swift:11-12, 312, 692-696) — per-card
    // spinner state.
    val purchasingProductId by viewModel.purchasingProductId.collectAsStateWithLifecycle()
    // iOS parity (SubscriptionView.swift:493-500) — dismiss after purchase.
    val purchaseSuccess by viewModel.purchaseSuccess.collectAsStateWithLifecycle()
    // iOS parity (SubscriptionView.swift:113-117, 478-516) — alert on failure.
    val purchaseError by viewModel.purchaseError.collectAsStateWithLifecycle()
    // iOS parity (SubscriptionView.swift:17, 391-428) — restore in-flight.
    val isRestoring by viewModel.isRestoring.collectAsStateWithLifecycle()
    // iOS parity (SubscriptionView.swift:621-635, 720-725) — render "Scheduled"
    // badge on the plan card matching the pending Core→Plus auto-renew change.
    val pendingUpgradePid by viewModel.pendingUpgradeProductId.collectAsStateWithLifecycle()
    val pendingUpgradeEffectiveDate by viewModel.pendingUpgradeEffectiveDate.collectAsStateWithLifecycle()
    // iOS BUG-2 parity (SubscriptionView.swift:243-276) — persistent inline
    // banner state that does not reset when an alert is dismissed.
    val conflictDetectedThisSession by viewModel.conflictDetectedThisSession.collectAsStateWithLifecycle()
    // iOS parity (SubscriptionView.swift:308-340) — 4 cards (core m/y + plus m/y).
    val displayPlans by viewModel.displayPlans.collectAsStateWithLifecycle()
    // iOS parity (SubscriptionView.swift:289-306) — prefer Apple/Play active plan
    // when DB still says "free_*" so user does not see "Choose Plus" on owned plan.
    val effectiveCurrentPlanId by viewModel.effectiveCurrentPlanId.collectAsStateWithLifecycle()

    val activity = LocalActivity.current as Activity
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = remember { HapticManager(context) }
    // iOS parity (SubscriptionView.swift:344-384): collapsible "What is
    // Destiny Matching?" info section.
    var showDestinyMatchingInfo by remember { mutableStateOf(false) }

    val restoreSuccessMsg = stringResource(R.string.restore_success)
    val restoreEmptyMsg = stringResource(R.string.restore_no_purchases)
    val restoreFailedMsg = stringResource(R.string.restore_failed)
    val productNotAvailableMsg = stringResource(R.string.product_not_available_error)

    LaunchedEffect(Unit) {
        // iOS parity (SubscriptionView.swift:118-130 .task) — auto-refresh products
        // and reconcile when StoreKit reports active sub but backend says free.
        viewModel.onScreenOpen()
    }

    // Restore Purchases — surface result per Apple HIG / Play policy.
    LaunchedEffect(restoreResult) {
        when (val r = restoreResult) {
            is RestoreResult.Success -> {
                snackbarHostState.showSnackbar(restoreSuccessMsg)
                viewModel.consumeRestoreResult()
                onBack()
            }
            is RestoreResult.NoPurchases -> {
                snackbarHostState.showSnackbar(restoreEmptyMsg)
                viewModel.consumeRestoreResult()
            }
            is RestoreResult.Error -> {
                snackbarHostState.showSnackbar(r.message.ifBlank { restoreFailedMsg })
                viewModel.consumeRestoreResult()
            }
            null -> Unit
        }
    }

    // iOS parity (SubscriptionView.swift:493-500) — dismiss the paywall when
    // BillingManager confirms entitlement, matching iOS dismiss().
    LaunchedEffect(purchaseSuccess) {
        if (purchaseSuccess) {
            viewModel.consumePurchaseSuccess()
            onBack()
        }
    }

    // iOS parity (SubscriptionView.swift:113-117, 478-516) — surface a snackbar
    // when purchase fails (null product, BillingManager error, etc.).
    LaunchedEffect(purchaseError) {
        purchaseError?.let { err ->
            val msg = if (err == "product_not_available_error") productNotAvailableMsg else err
            snackbarHostState.showSnackbar(msg)
            viewModel.consumePurchaseError()
        }
    }

    // iOS parity (SubscriptionView.swift:113-117 .alert) — promote any
    // BillingManager / load-plans error to a dismissible snackbar instead of
    // letting it sit forever in the LazyColumn with no acknowledgement path.
    LaunchedEffect(state.error) {
        state.error?.let { err ->
            snackbarHostState.showSnackbar(err)
            viewModel.consumeError()
        }
    }

    // iOS BUG-2 parity (SubscriptionView.swift:243-276): conflict is rendered
    // as an inline persistent banner inside the LazyColumn — NOT as an
    // AlertDialog that vanishes after dismissal. The previous AlertDialog
    // implementation has been removed.

    CosmicBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .semantics { testTag = "subscription_screen" },
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        haptic.light()
                        onBack()
                    },
                    modifier = Modifier.semantics { testTag = "subscription_back_button" },
                ) {
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
                // iOS parity (SubscriptionView.swift:168-192): toolbar refresh
                // button → forces backend status sync + reconcile so users with
                // a redeemed offer code or lagged Play webhook can manually
                // recover without restarting the app.
                IconButton(
                    onClick = {
                        haptic.light()
                        viewModel.refreshStatus()
                    },
                    modifier = Modifier.semantics { testTag = "subscription_refresh_button" },
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.a11y_refresh_subscription),
                        tint = Gold,
                    )
                }
                // iOS parity (SubscriptionView.swift:107-110): trailing "Done"
                // toolbar action that dismisses the paywall sheet.
                TextButton(
                    onClick = {
                        haptic.light()
                        onBack()
                    },
                    modifier = Modifier.semantics { testTag = "subscription_done_button" },
                ) {
                    Text(
                        text = stringResource(R.string.done_action),
                        color = Gold,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (state.isLoading && state.plans.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Gold, modifier = Modifier.size(28.dp))
                }
            } else if (!state.isLoading && displayPlans.isEmpty()) {
                // iOS parity (SubscriptionView.swift:33-36) — explicit
                // "no_plans_available" empty state when the backend returned
                // zero paid plans (e.g. only free tier seeded).
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { testTag = "subscription_empty_state" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.no_plans_available),
                        color = CreamDim,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(40.dp),
                    )
                }
            } else {
                // iOS parity (SubscriptionView.swift:77-82): pull-to-refresh
                // wrapper so users with a redeemed offer code or lagged Play
                // webhook have a manual recovery path without app restart.
                PullToRefreshBox(
                    isRefreshing = isLoading,
                    onRefresh = { viewModel.refreshStatus() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        // iOS parity (SubscriptionView.swift:39-44): single
                        // centered subheader "Upgrade to keep going..." instead
                        // of a separate icon + title + body block.
                        Text(
                            text = stringResource(R.string.upgrade_to_keep_going),
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 16.sp,
                            color = CreamDim,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .semantics { testTag = "subscription_subheader" },
                        )
                    }

                    // iOS BUG-2 parity (SubscriptionView.swift:243-276):
                    // persistent inline orange-bordered cross-account conflict
                    // banner. Stays visible until logout (sign-out clears
                    // conflictDetectedThisSession in BillingManager).
                    if (conflictDetectedThisSession) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0x1AFF9800)) // orange 10% alpha
                                    .border(1.dp, Color(0xFFFF9800).copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                                    .semantics { testTag = "subscription_conflict_banner" },
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFFF9800),
                                    modifier = Modifier.padding(top = 1.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.conflict_banner_title),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CreamText,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.conflict_banner_body),
                                        fontSize = 12.sp,
                                        color = CreamDim,
                                    )
                                }
                            }
                        }
                    }

                    // iOS parity (SubscriptionView.swift:198-235 activatingBanner):
                    // shown when StoreKit reports active sub but backend says
                    // free (no conflict). Recovers stale-state during the
                    // Play→backend reconcile window post-purchase.
                    if (hasActiveSub && !state.isPremium && conflict == null && !conflictDetectedThisSession) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(NavySurface)
                                    .border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Gold,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.subscription_activating_title),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Gold,
                                        fontFamily = CanelaFontFamily,
                                    )
                                    Text(
                                        text = stringResource(R.string.subscription_activating_subtitle),
                                        fontSize = 12.sp,
                                        color = CreamDim,
                                    )
                                }
                            }
                        }
                    }

                    // iOS parity (SubscriptionView.swift:308-340): always render
                    // plan cards regardless of premium state so a Core
                    // subscriber can upgrade to Plus. The previous
                    // already-premium takeover blocked the Core→Plus path.
                    //
                    // SUBSCRIPTION-GAP-1 fix: backend returns ONE PlanDto per
                    // tier with planId='core'/'plus' (no period suffix), so the
                    // old `plan.planId.contains("yearly")` always returned
                    // false and yearly cards never rendered. We now consume
                    // `displayPlans` (4 entries: core m/y + plus m/y) which
                    // mirrors iOS's product-driven card model.
                    val corePlanRef = displayPlans.firstOrNull {
                        it.tier == "core" && it.period == "monthly"
                    }?.source
                    items(displayPlans) { displayPlan ->
                            val plan = displayPlan.source
                            val isYearly = displayPlan.isYearly
                            val isPlus = displayPlan.isPlus
                            val tier = displayPlan.tier
                            val period = displayPlan.period
                            // Match backend plan to a Play Billing ProductDetails so
                            // we can show locale-formatted pricing instead of a hard-
                            // coded "$" prefix (iOS parity, SubscriptionView.swift:649-660).
                            val productDetails = products.firstOrNull {
                                it.productId.equals(plan.planId, ignoreCase = true)
                            } ?: products.firstOrNull {
                                it.productId.contains(tier, ignoreCase = true) &&
                                    it.productId.contains(period, ignoreCase = true)
                            }
                            // formattedPrice from Play Billing is already locale-aware
                            // (₹599 in IN, $7.99 in US). Falls back to backend USD only
                            // when Play products haven't loaded yet.
                            // iOS parity (SubscriptionView.swift:745-756) — when products
                            // fail to load AND device locale is India, show ₹249/₹599
                            // instead of dollar-prefixed backend price.
                            val isIndiaLocale = java.util.Locale.getDefault().country
                                .equals("IN", ignoreCase = true)
                            val backendUsd = displayPlan.price
                            val fallbackPrice = if (isIndiaLocale) {
                                if (isPlus) "₹599" else "₹249"
                            } else {
                                "$$backendUsd"
                            }
                            val formattedPrice: String = productDetails
                                ?.subscriptionOfferDetails
                                ?.firstOrNull()
                                ?.pricingPhases
                                ?.pricingPhaseList
                                ?.lastOrNull()
                                ?.formattedPrice
                                ?: fallbackPrice
                            val periodSuffix = if (isYearly) {
                                "/" + stringResource(R.string.per_year_short_suffix)
                            } else {
                                "/" + stringResource(R.string.per_month_short_suffix)
                            }
                            // iOS parity (SubscriptionView.swift:603-635, 758-774):
                            // current-plan badge + dynamic CTA — "Upgrade to Plus" if
                            // user is on Core viewing Plus, "Choose Plus" if free,
                            // "Current Plan" if same.
                            //
                            // SUBSCRIPTION-GAP-2 fix: prefer effectiveCurrentPlanId
                            // (Apple/Play active plan when DB still says free_*)
                            // so user does not see "Choose Plus" on owned plan
                            // during the cross-window between Apple/Play webhook
                            // landing and the next backend status sync.
                            val resolvedCurrentPlan = effectiveCurrentPlanId.orEmpty()
                            val isCurrentPlan =
                                resolvedCurrentPlan.equals(plan.planId, ignoreCase = true) &&
                                    // PlanDto.planId is tier-only ('core'/'plus') with no
                                    // period suffix — only treat as current when the
                                    // active product also matches this card's period.
                                    (
                                        activePlanId == null ||
                                            (
                                                activePlanId?.contains(tier, ignoreCase = true) == true &&
                                                    activePlanId?.contains(period, ignoreCase = true) == true
                                            )
                                        ) ||
                                    (
                                        resolvedCurrentPlan.contains(tier, ignoreCase = true) &&
                                            resolvedCurrentPlan.contains(period, ignoreCase = true)
                                        ) ||
                                    (
                                        activePlanId?.contains(tier, ignoreCase = true) == true &&
                                            activePlanId?.contains(period, ignoreCase = true) == true
                                        )
                            val ctaText: String = when {
                                isCurrentPlan -> stringResource(R.string.current_plan_label)
                                isPlus && resolvedCurrentPlan.equals("core", ignoreCase = true) ->
                                    stringResource(R.string.upgrade_to_plus_label)
                                isPlus -> stringResource(R.string.choose_plus_label)
                                else -> stringResource(R.string.choose_core_label)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(NavySurface)
                                    .border(
                                        width = if (isCurrentPlan) 2.dp else if (isYearly) 1.dp else 0.5.dp,
                                        color = if (isCurrentPlan) Gold else if (isYearly) Gold else Gold.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(16.dp),
                                    )
                                    .padding(16.dp)
                                    .semantics { testTag = "subscription_plan_card" },
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = plan.displayName,
                                                    fontSize = 17.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = CanelaFontFamily,
                                                    color = CreamText,
                                                )
                                                if (isCurrentPlan) {
                                                    Spacer(Modifier.width(8.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(50))
                                                            .background(Gold)
                                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                                    ) {
                                                        Text(
                                                            text = stringResource(R.string.current_plan),
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = Color(0xFF0D0D1A),
                                                        )
                                                    }
                                                }
                                                // iOS parity (SubscriptionView.swift:621-635) —
                                                // "Scheduled" badge on the card matching the
                                                // pending Core→Plus tier change.
                                                val isScheduled = pendingUpgradePid != null &&
                                                    pendingUpgradePid?.contains(tier, ignoreCase = true) == true &&
                                                    pendingUpgradePid?.contains(period, ignoreCase = true) == true
                                                if (isScheduled) {
                                                    Spacer(Modifier.width(8.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(50))
                                                            .background(Gold.copy(alpha = 0.2f))
                                                            .border(0.5.dp, Gold, RoundedCornerShape(50))
                                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                                    ) {
                                                        Text(
                                                            text = stringResource(R.string.scheduled_plan),
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = Gold,
                                                        )
                                                    }
                                                }
                                            }
                                            // iOS parity (SubscriptionView.swift:638-642):
                                            // plan.description rendered below the plan name.
                                            plan.description?.takeIf { it.isNotBlank() }?.let { desc ->
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    text = desc,
                                                    fontSize = 14.sp,
                                                    color = CreamDim,
                                                )
                                            }
                                            if (isYearly) {
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    text = stringResource(R.string.billing_free_trial),
                                                    fontSize = 11.sp,
                                                    color = Gold,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                            } else if (
                                                isPlus &&
                                                isPlusTrialEligible &&
                                                !hasActiveSub &&
                                                conflict == null
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
                                                        text = stringResource(R.string.subscription_free_trial_available),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Gold,
                                                    )
                                                }
                                            }
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "$formattedPrice$periodSuffix",
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
                                        text = if (plan.dailyQuota < 0) stringResource(R.string.subscription_unlimited_questions)
                                        else stringResource(R.string.subscription_questions_per_day, plan.dailyQuota),
                                        fontSize = 13.sp,
                                        color = CreamDim,
                                    )
                                    // iOS parity (SubscriptionView.swift:521-572, 664-678):
                                    // Per-plan entitlements list with checkmarks. Plus shows
                                    // an "Everything in Core, plus:" header.
                                    // iOS parity (SubscriptionView.swift:535-572 displayFeatures):
                                    // Core → only entitlements with non-empty marketingText.
                                    // Plus → only entitlements with non-empty marketingText that
                                    // ALSO differ from the Core marketingText for the same featureId.
                                    val rawEntitlements = plan.entitlements.orEmpty()
                                    val features = if (isPlus) {
                                        val coreTexts = corePlanRef?.entitlements
                                            .orEmpty()
                                            .mapNotNull { e ->
                                                e.marketingText?.takeIf { it.isNotEmpty() }
                                                    ?.let { e.featureId to it }
                                            }
                                            .toMap()
                                        rawEntitlements.filter { ent ->
                                            val text = ent.marketingText
                                            if (text.isNullOrEmpty()) return@filter false
                                            val coreText = coreTexts[ent.featureId]
                                            coreText == null || coreText != text
                                        }
                                    } else {
                                        rawEntitlements.filter { !it.marketingText.isNullOrEmpty() }
                                    }
                                    if (features.isNotEmpty()) {
                                        if (isPlus) {
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                text = stringResource(R.string.everything_in_core_plus),
                                                fontSize = 12.sp,
                                                color = Gold,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        features.forEach { feature ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.Top,
                                            ) {
                                                Text(
                                                    text = "✓",
                                                    color = Gold,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(top = 1.dp),
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    // iOS parity (SubscriptionView.swift:782-784, 802-806):
                                                    // append "(coming soon)" caption when displayName
                                                    // contains "coming soon".
                                                    val isComingSoon = feature.displayName
                                                        .contains("coming soon", ignoreCase = true)
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = feature.displayName,
                                                            fontSize = 13.sp,
                                                            color = CreamText,
                                                            fontWeight = FontWeight.SemiBold,
                                                        )
                                                        if (isComingSoon) {
                                                            Spacer(Modifier.width(4.dp))
                                                            Text(
                                                                text = stringResource(R.string.coming_soon),
                                                                fontSize = 10.sp,
                                                                color = CreamDim.copy(alpha = 0.7f),
                                                            )
                                                        }
                                                    }
                                                    val sub = feature.marketingText
                                                        ?: feature.description
                                                    if (!sub.isNullOrBlank()) {
                                                        Text(
                                                            text = sub,
                                                            fontSize = 11.sp,
                                                            color = CreamDim,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    // iOS parity (SubscriptionView.swift:683-690):
                                    // dual-line "Start 7-Day Free Trial" / "then
                                    // <price> · cancel anytime" CTA when trial-eligible.
                                    val showTrialCta = isPlus &&
                                        isPlusTrialEligible &&
                                        !hasActiveSub &&
                                        conflict == null &&
                                        !isCurrentPlan
                                    val isScheduledForThisCard = pendingUpgradePid != null &&
                                        pendingUpgradePid?.contains(tier, ignoreCase = true) == true &&
                                        pendingUpgradePid?.contains(period, ignoreCase = true) == true
                                    // iOS parity (SubscriptionView.swift:11-12, 312, 692-696)
                                    // — only this card shows a spinner when ITS productId is
                                    // being purchased.
                                    val thisCardPurchasing = purchasingProductId != null &&
                                        productDetails != null &&
                                        purchasingProductId == productDetails.productId
                                    Button(
                                        onClick = {
                                            haptic.light()
                                            // iOS parity (SubscriptionView.swift:478-482):
                                            // always invoke purchase — VM surfaces a
                                            // product_not_available error via snackbar
                                            // when productDetails is null instead of
                                            // silently no-opping.
                                            val offerToken = productDetails
                                                ?.subscriptionOfferDetails
                                                ?.firstOrNull()
                                                ?.offerToken
                                            viewModel.purchase(productDetails, activity, offerToken)
                                        },
                                        enabled = !thisCardPurchasing && !isCurrentPlan && !isScheduledForThisCard &&
                                            purchasingProductId == null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(if (showTrialCta) 56.dp else 44.dp)
                                            .semantics { testTag = "subscription_cta_button" },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Gold,
                                            contentColor = Color(0xFF0D0D1A),
                                            disabledContainerColor = Gold.copy(alpha = 0.4f),
                                        ),
                                    ) {
                                        if (thisCardPurchasing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                color = Color(0xFF0D0D1A),
                                                strokeWidth = 2.dp,
                                            )
                                        } else if (showTrialCta) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.start_7_day_free_trial),
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                                Text(
                                                    text = stringResource(
                                                        R.string.trial_then_price_format,
                                                        "$formattedPrice$periodSuffix",
                                                    ),
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF0D0D1A).copy(alpha = 0.85f),
                                                )
                                            }
                                        } else {
                                            Text(
                                                ctaText,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    }

                                    // iOS parity (SubscriptionView.swift:720-725):
                                    // "Subscription starts <date>" label below CTA
                                    // when this plan is a pending upgrade.
                                    if (isScheduledForThisCard && pendingUpgradeEffectiveDate != null) {
                                        Spacer(Modifier.height(6.dp))
                                        val formattedDate = DateFormat
                                            .getDateInstance(DateFormat.MEDIUM)
                                            .format(Date(pendingUpgradeEffectiveDate!!))
                                        Text(
                                            text = stringResource(
                                                R.string.subscription_starts_on_format,
                                                formattedDate,
                                            ),
                                            color = Color(0xFFFF9800),
                                            fontSize = 12.sp,
                                        )
                                    }
                                }
                            }
                        }

                    // iOS parity (SubscriptionView.swift:113-117 .alert) —
                    // state.error is surfaced via the LaunchedEffect snackbar
                    // above (then auto-cleared via consumeError) instead of a
                    // static, non-dismissable Text inside the scroll view.

                    // iOS parity (SubscriptionView.swift:391-428): Restore
                    // Purchases is ALWAYS visible — premium users on a new
                    // device need it to recover entitlement.
                    item {
                        TextButton(
                            onClick = {
                                haptic.light()
                                viewModel.restorePurchases()
                            },
                            enabled = !isRestoring,
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { testTag = "subscription_restore_button" },
                        ) {
                            // iOS parity (SubscriptionView.swift:415-424) —
                            // CircularProgressIndicator next to label while restore runs.
                            if (isRestoring) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = Gold,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                text = stringResource(R.string.restore_purchases),
                                color = CreamDim,
                                fontSize = 13.sp,
                            )
                        }
                    }

                    // iOS parity — Premium-only "Manage Subscription" deep link
                    // to the Play Store subscriptions page (cancel / change /
                    // resubscribe). Apple handles this via the system account
                    // screen; on Android we open Play directly.
                    if (state.isPremium || hasActiveSub) {
                        item {
                            val managePackage = context.packageName
                            val manageSku = activePlanId
                            TextButton(
                                onClick = {
                                    haptic.light()
                                    val base = "https://play.google.com/store/account/subscriptions"
                                    val url = if (!manageSku.isNullOrBlank()) {
                                        "$base?sku=$manageSku&package=$managePackage"
                                    } else {
                                        base
                                    }
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { testTag = "subscription_manage_button" },
                            ) {
                                Text(
                                    text = stringResource(R.string.manage_subscription_action),
                                    color = Gold,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }

                    if (!state.isPremium && !hasActiveSub) {
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

                        // iOS parity (SubscriptionView.swift:438-441):
                        // italic fair-use disclaimer in the footer block.
                        item {
                            Text(
                                text = stringResource(R.string.fair_use_notice),
                                fontSize = 11.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                color = CreamDim.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }

                    // iOS parity (SubscriptionView.swift:465-475 appleAccountNote)
                    // — rendered UNCONDITIONALLY on iOS for all users. Previously
                    // gated behind !isPremium && !hasActiveSub on Android, so
                    // premium users saw no Play account disclosure.
                    item {
                        Text(
                            text = stringResource(R.string.play_subscription_notice),
                            fontSize = 11.sp,
                            color = CreamDim.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }

                    // iOS parity (SubscriptionView.swift:344-384, 347-362):
                    // Destiny Matching expander is rendered UNCONDITIONALLY on
                    // iOS — premium and free users alike see it. Previously
                    // gated behind !isPremium && !hasActiveSub, hiding the
                    // feature explainer from premium users entirely.
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                        ) {
                            TextButton(
                                onClick = {
                                    haptic.light()
                                    showDestinyMatchingInfo = !showDestinyMatchingInfo
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { testTag = "subscription_destiny_matching_expander" },
                            ) {
                                Icon(
                                    imageVector = if (showDestinyMatchingInfo)
                                        Icons.Filled.KeyboardArrowUp
                                    else Icons.Filled.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = CreamDim,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.what_is_destiny_matching),
                                    fontSize = 16.sp,
                                    fontFamily = CanelaFontFamily,
                                    color = CreamDim,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (showDestinyMatchingInfo) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(NavySurface)
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.destiny_matching_desc_1),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CreamDim,
                                    )
                                    Text(
                                        text = stringResource(R.string.destiny_matching_desc_2),
                                        fontSize = 14.sp,
                                        color = CreamDim,
                                    )
                                    Text(
                                        text = stringResource(R.string.destiny_matching_desc_3),
                                        fontSize = 14.sp,
                                        color = CreamDim,
                                    )
                                }
                            }
                        }
                    }

                    // iOS parity (SubscriptionView.swift:443-457 footerDisclaimers):
                    // Terms / Privacy links are shown UNCONDITIONALLY for ALL
                    // users — Apple Guideline 3.1.2(a) and Play Subscription
                    // policy require functional Terms + Privacy links on every
                    // paywall load. Previously gated behind !isPremium &&
                    // !hasActiveSub, breaking parity and policy.
                    item {
                        val termsUrl = stringResource(R.string.terms_url)
                        val privacyUrl = stringResource(R.string.privacy_url)
                        val termsLabel = stringResource(R.string.terms_of_service)
                        val privacyLabel = stringResource(R.string.privacy_policy)
                        val annotated = buildAnnotatedString {
                            pushStringAnnotation(tag = "URL", annotation = termsUrl)
                            withStyle(
                                SpanStyle(
                                    color = Gold,
                                    textDecoration = TextDecoration.Underline,
                                ),
                            ) { append(termsLabel) }
                            pop()
                            append("   •   ")
                            pushStringAnnotation(tag = "URL", annotation = privacyUrl)
                            withStyle(
                                SpanStyle(
                                    color = Gold,
                                    textDecoration = TextDecoration.Underline,
                                ),
                            ) { append(privacyLabel) }
                            pop()
                        }
                        androidx.compose.foundation.text.ClickableText(
                            text = annotated,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = CreamDim,
                                textAlign = TextAlign.Center,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .semantics { testTag = "subscription_legal_links" },
                            onClick = { offset ->
                                annotated.getStringAnnotations(
                                    tag = "URL",
                                    start = offset,
                                    end = offset,
                                ).firstOrNull()?.let { ann ->
                                    haptic.light()
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(ann.item))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                }
                            },
                        )
                    }

                    item { Spacer(Modifier.height(32.dp)) }
                }
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}
