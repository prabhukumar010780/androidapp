package com.destinyai.astrology.ui.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.R
import com.destinyai.astrology.services.HapticManager
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.GoldLight
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.NavyVariant
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LanguageSelectionScreen(
    onNavigateNext: () -> Unit,
    viewModel: LanguageSelectionViewModel = hiltViewModel(),
) {
    val selectedCode by viewModel.selectedCode.collectAsStateWithLifecycle()
    val soundEnabled by viewModel.isSoundEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = remember { HapticManager(context) }

    // Particle burst state — iOS parity (LanguageSelectionView.swift:86-89, 252-255):
    // showParticles flips on for ~0.6s after a card tap to render the gold burst overlay.
    var particleTrigger by remember { mutableStateOf(0) }

    // Staggered entrance animation flag — iOS parity (LanguageSelectionView.swift:235-239).
    var animateContent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animateContent = true }

    CosmicBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // iOS parity (LanguageSelectionView.swift:51-71): top-right speaker toggle
                // that flips the persisted sound flag via SoundManager.toggleSound().
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f))
                            .clickable {
                                haptic.light()
                                viewModel.toggleSound()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (soundEnabled) {
                                Icons.Filled.VolumeUp
                            } else {
                                Icons.Filled.VolumeOff
                            },
                            contentDescription = stringResource(
                                if (soundEnabled) R.string.sound_on_a11y else R.string.sound_off_a11y,
                            ),
                            tint = CreamDim,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // iOS parity (LanguageSelectionView.swift:100-136): animated celestial
                // header — outer radial glow + rotating stroke ring + tiny orbiting dot
                // + central gold-gradient globe icon.
                AnimatedGlobeHeader()

                Spacer(Modifier.height(24.dp))

                // "Destiny AI Astrology" header
                Text(
                    text = stringResource(R.string.destiny_ai_brand_name),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Gold,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(8.dp))

                // "Personalized guidance, in your language"
                Text(
                    text = stringResource(R.string.personalized_guidance),
                    fontSize = 16.sp,
                    color = CreamDim,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(32.dp))

                // 3-column grid with staggered entrance animation per card.
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(viewModel.languages) { index, lang ->
                        val isSelected = lang.code == selectedCode
                        // iOS parity (LanguageSelectionView.swift:163-170): per-index
                        // staggered fade-in + slide-up with 60ms cumulative delay.
                        val cardAlpha by animateFloatAsState(
                            targetValue = if (animateContent) 1f else 0f,
                            animationSpec = tween(
                                durationMillis = 700,
                                delayMillis = 150 + index * 60,
                            ),
                            label = "cardAlpha$index",
                        )
                        val cardOffsetY by animateFloatAsState(
                            targetValue = if (animateContent) 0f else 30f,
                            animationSpec = tween(
                                durationMillis = 700,
                                delayMillis = 150 + index * 60,
                            ),
                            label = "cardOffset$index",
                        )
                        Box(
                            modifier = Modifier.graphicsLayer {
                                this.alpha = cardAlpha
                                this.translationY = cardOffsetY
                            },
                        ) {
                            LanguageCard(
                                lang = lang,
                                isSelected = isSelected,
                                onClick = {
                                    // Haptic + sound fired by ViewModel.selectLanguage.
                                    viewModel.selectLanguage(lang.code)
                                    particleTrigger++
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Continue button
                val selectedLang = viewModel.languages.find { it.code == selectedCode }
                val buttonText = if (selectedLang != null) {
                    stringResource(R.string.continue_in_language_format, selectedLang.nativeName)
                } else {
                    stringResource(R.string.select_a_language)
                }

                Button(
                    onClick = {
                        // Haptic + success chime fired by ViewModel.confirmSelection.
                        viewModel.confirmSelection()
                        onNavigateNext()
                    },
                    enabled = selectedCode != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Gold,
                        disabledContainerColor = NavyVariant,
                        contentColor = Color(0xFF0D0D1A),
                        disabledContentColor = CreamDim,
                    ),
                ) {
                    Text(
                        text = buttonText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                    )
                }

                Spacer(Modifier.height(32.dp))
            }

            // iOS parity (LanguageSelectionView.swift:86-89, 252-255): gold particle
            // burst overlay rendered when a card is tapped. Re-keys on `particleTrigger`.
            if (particleTrigger > 0) {
                key(particleTrigger) {
                    ParticleBurst()
                }
            }
        }
    }
}

/**
 * iOS parity (LanguageSelectionView.swift:100-136). Renders the animated header
 * with three layers: an outer radial glow halo, a slowly rotating stroked ring,
 * a tiny gold dot orbiting along the ring, and a central globe icon tinted with
 * the gold gradient.
 */
@Composable
private fun AnimatedGlobeHeader() {
    val transition = rememberInfiniteTransition(label = "globeOrbit")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "rotation",
    )
    Box(
        modifier = Modifier.size(80.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Outer radial glow halo
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Gold.copy(alpha = 0.3f), Color.Transparent),
                    ),
                ),
        )
        // Rotating stroke ring + orbiting dot drawn together.
        Canvas(
            modifier = Modifier
                .size(70.dp)
                .rotate(rotation),
        ) {
            // Stroke ring
            drawCircle(
                color = Gold.copy(alpha = 0.2f),
                radius = size.minDimension / 2 - 1.dp.toPx(),
                style = Stroke(width = 1.dp.toPx()),
            )
            // Orbiting dot — fixed at right edge of ring (offset.x = 35dp in iOS).
            val r = size.minDimension / 2 - 3.dp.toPx()
            val cx = size.width / 2 + r
            val cy = size.height / 2
            drawCircle(
                color = GoldLight,
                radius = 3.dp.toPx(),
                center = Offset(cx, cy),
            )
        }
        // Central globe icon — gold gradient tint via brush.
        Icon(
            imageVector = Icons.Filled.Public,
            contentDescription = null,
            tint = Gold,
            modifier = Modifier.size(34.dp),
        )
    }
}

/**
 * iOS parity (LanguageSelectionView.swift:387-434): radiates ~24 gold particles
 * from the center fading to alpha 0 over ~0.6s.
 */
@Composable
private fun ParticleBurst() {
    val particleCount = 24
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = LinearEasing),
        )
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        for (i in 0 until particleCount) {
            val angle = (Math.PI * 2 * i / particleCount).toFloat()
            val radiusPx = (80f + (i % 5) * 14f) * progress.value
            val px = center.x + cos(angle) * radiusPx
            val py = center.y + sin(angle) * radiusPx
            val alpha = (1f - progress.value).coerceIn(0f, 1f)
            drawCircle(
                color = Gold.copy(alpha = alpha),
                radius = (3f + (i % 3)) * (1f - 0.3f * progress.value),
                center = Offset(px, py),
            )
        }
    }
}

/** Helper removed — using inline `Modifier.graphicsLayer { ... }` at call sites. */

@Composable
private fun LanguageCard(
    lang: LanguageOption,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val cornerRadius = 12.dp
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .then(
                if (isSelected) {
                    Modifier.shadow(
                        elevation = 10.dp,
                        shape = shape,
                        ambientColor = Gold,
                        spotColor = Gold,
                    )
                } else Modifier,
            )
            .clip(shape)
            // iOS parity: ultraThinMaterial glass effect — approximated with a
            // semi-transparent dark base + subtle white sheen overlay.
            .background(
                if (isSelected) {
                    Brush.linearGradient(listOf(Color(0xFF2C2C40), Color(0xFF1A1A3A)))
                } else {
                    Brush.linearGradient(
                        listOf(NavySurface.copy(alpha = 0.78f), NavySurface.copy(alpha = 0.78f)),
                    )
                },
            )
            .border(
                width = if (isSelected) 1.5.dp else 0.5.dp,
                color = if (isSelected) Gold else Gold.copy(alpha = 0.15f),
                shape = shape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // White sheen depth layer (top → bottom) — matches iOS card depth gradient.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isSelected) 0.12f else 0.06f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        // Selection radial glow — iOS parity (LanguageSelectionView.swift:330-341).
        if (isSelected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Gold.copy(alpha = 0.2f), Color.Transparent),
                            radius = 120f,
                        ),
                    ),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(8.dp),
        ) {
            Text(
                text = lang.nativeName,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = CanelaFontFamily,
                color = if (isSelected) GoldLight else CreamText,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = lang.name,
                fontSize = 11.sp,
                color = if (isSelected) Gold else CreamDim,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}
