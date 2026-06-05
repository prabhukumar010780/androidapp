package com.destinyai.astrology.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.R
import com.destinyai.astrology.ui.components.auth.AuthOrbitalRings
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold

// iOS parity (ProfileSetupLoadingView.SetupPhase): each of the 4 setup phases has
// a localized title + subtitle pair backed by string resources (required for
// 13-language support). Index order matches iOS .calculatingChart, .analyzingPlanets,
// .generatingInsights, .complete.
private data class PhaseStrings(
    val titleRes: Int,
    val subtitleRes: Int,
    // iOS parity (ProfileSetupLoadingView.swift:22-30): per-phase central icon —
    // hexagongrid / sparkles / brain.head.profile / checkmark.
    val icon: ImageVector,
)

private val phaseStrings = listOf(
    PhaseStrings(R.string.setup_phase_birth_chart, R.string.setup_phase_birth_chart_subtitle, Icons.Filled.GridView),
    PhaseStrings(R.string.setup_phase_planetary, R.string.setup_phase_planetary_subtitle, Icons.Filled.AutoAwesome),
    PhaseStrings(R.string.setup_phase_insights, R.string.setup_phase_insights_subtitle, Icons.Filled.Psychology),
    PhaseStrings(R.string.setup_phase_ready, R.string.setup_phase_ready_subtitle, Icons.Filled.CheckCircle),
)

@Composable
fun ProfileSetupLoadingScreen(
    onComplete: () -> Unit,
    viewModel: ProfileSetupLoadingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val phaseIndex = uiState.phaseIndex.coerceIn(0, phaseStrings.size - 1)

    val animatedProgress by animateFloatAsState(
        targetValue = uiState.progress,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "progress",
    )

    // Kick off real prefetch (chart + today's prediction) once on entry — matches
    // iOS .task { performSetup() } in ProfileSetupLoadingView.swift
    LaunchedEffect(Unit) {
        viewModel.startSetup(onComplete)
    }

    // Android 13+ POST_NOTIFICATIONS runtime permission — request once during
    // final onboarding step so FCM tokens can be registered. iOS parity:
    // notification gating handled via NotificationsViewModel on launch.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* result ignored — token registration happens regardless */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "rings")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "rotation",
    )

    CosmicBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            // Orbital ring animation — 3 concentric rings (inner/middle/outer)
            // matching iOS ProfileSetupLoadingView.swift:54 OrbitalRingsView.
            Box(
                modifier = Modifier.size(160.dp),
                contentAlignment = Alignment.Center,
            ) {
                AuthOrbitalRings(
                    rotation = rotation,
                    modifier = Modifier.size(160.dp),
                )
                // iOS parity (ProfileSetupLoadingView.swift:58-68): center icon swaps
                // per phase and transitions to a CheckCircle when the setup completes.
                // While loading, a slow pulse alpha mimics SwiftUI .symbolEffect(.pulse).
                val pulseTransition = rememberInfiniteTransition(label = "pulse")
                val pulseAlpha by pulseTransition.animateFloat(
                    initialValue = 0.6f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(900, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "pulseAlpha",
                )
                AnimatedContent(
                    targetState = uiState.isComplete,
                    transitionSpec = {
                        (scaleIn(animationSpec = tween(350)) + fadeIn()) togetherWith
                            (scaleOut(animationSpec = tween(250)) + fadeOut())
                    },
                    label = "centerIconSwap",
                ) { complete ->
                    val icon = if (complete) Icons.Filled.CheckCircle
                    else phaseStrings[phaseIndex].icon
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Gold,
                        modifier = Modifier
                            .size(36.dp)
                            .alpha(if (complete) 1f else pulseAlpha),
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            // iOS parity (ProfileSetupLoadingView.swift:75-77): "Setting Up Your Profile"
            // header above the phase title block.
            Text(
                text = stringResource(R.string.setting_up_profile),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CanelaFontFamily,
                color = CreamText,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))

            // Phase title (localized)
            Text(
                text = stringResource(phaseStrings[phaseIndex].titleRes),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CanelaFontFamily,
                color = CreamText,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(phaseStrings[phaseIndex].subtitleRes),
                fontSize = 15.sp,
                color = CreamDim,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )

            Spacer(Modifier.height(48.dp))

            // Gold progress bar
            Column(modifier = Modifier.fillMaxWidth()) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = Gold,
                    trackColor = Gold.copy(alpha = 0.15f),
                    strokeCap = StrokeCap.Round,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Step ${phaseIndex + 1} of ${phaseStrings.size}",
                    fontSize = 12.sp,
                    color = CreamDim,
                    modifier = Modifier.align(Alignment.End),
                )
            }

            Spacer(Modifier.weight(1f))

            // iOS parity (ProfileSetupLoadingView.swift:94-97): subtle cosmic-alignment
            // footer caption at the bottom of the column.
            Text(
                text = stringResource(R.string.cosmic_alignment),
                fontSize = 12.sp,
                color = CreamDim.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 40.dp),
            )
        }
    }
}
