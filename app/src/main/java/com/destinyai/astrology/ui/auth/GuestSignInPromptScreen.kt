package com.destinyai.astrology.ui.auth

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.R
import com.destinyai.astrology.services.HapticManager
import com.destinyai.astrology.services.SoundManager
import com.destinyai.astrology.ui.theme.AuthDimens
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.components.auth.AuthOrbitalRings
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@Composable
fun GuestSignInPromptScreen(
    message: String? = null,
    onSignIn: () -> Unit,
    onBack: (() -> Unit)? = null,
    provider: String? = null,
) {
    val context = LocalContext.current
    val haptic = remember { HapticManager(context) }
    // iOS parity (GuestSignInPromptView.swift:261): SoundManager.shared.playButtonTap()
    // is invoked alongside haptics on every sign-in / back tap. Pull the singleton
    // SoundManager via the Hilt EntryPoint so this composable doesn't need a
    // ViewModel injection.
    val soundManager = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            GuestPromptSoundEntryPoint::class.java,
        ).soundManager()
    }
    // If the original account was created with Apple Sign-In (iOS-only path), the
    // user cannot complete sign-in here — Apple has no native Android UI and we no
    // longer launch the OAuth web fallback. Surface a contact-support message so
    // the prompt remains coherent for cross-platform users.
    val isAppleOnlyAccount = provider?.equals("apple", ignoreCase = true) == true
    val resolvedMessage = message
        ?: if (isAppleOnlyAccount) {
            stringResource(R.string.apple_account_requires_ios)
        } else {
            stringResource(R.string.sign_in_to_access_feature)
        }
    var isSigningIn by remember { mutableStateOf(false) }
    // iOS parity (GuestSignInPromptView.swift:259,286): isSigningIn flips to true
    // at the start of sign-in. Caller's onSignIn is responsible for resetting it
    // (or unmounting this screen) when the async flow completes.
    val handleGoogle = {
        haptic.playButtonTap()
        soundManager.playButtonTap()
        isSigningIn = true
        onSignIn()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "orbital")
    val orbitRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(30000, easing = LinearEasing),
        ),
        label = "orbit",
    )
    val logoRingRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
        ),
        label = "logoRing",
    )
    // iOS parity (GuestSignInPromptView.swift:133): bioRhythm pulse — bpm=60
    // intensity=1.05, paused while signing in. Period 1s, scale 1.0 -> 1.05 -> 1.0.
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isSigningIn) 1.0f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "logoPulse",
    )

    // iOS parity (GuestSignInPromptView.swift:46-66, 246-249): content section fades
    // in (opacity 0->1) and slides up (offset 20->0) on first composition. Driven by
    // a one-shot LaunchedEffect that flips a target state to trigger animateFloatAsState.
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { contentVisible = true }
    val contentOpacity by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = AuthDimens.entranceDurationMs,
            delayMillis = AuthDimens.entranceDelayMs,
            easing = FastOutSlowInEasing,
        ),
        label = "contentOpacity",
    )
    val contentOffsetY by animateFloatAsState(
        targetValue = if (contentVisible) 0f else 20f,
        animationSpec = tween(
            durationMillis = AuthDimens.entranceDurationMs,
            delayMillis = AuthDimens.entranceDelayMs,
            easing = FastOutSlowInEasing,
        ),
        label = "contentOffset",
    )

    CosmicBackground {
        // Ambient orbital rings overlay (0.25 opacity like iOS)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = (-60).dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            AuthOrbitalRings(rotation = orbitRotation, modifier = Modifier.size(400.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.weight(1f))

            // Animated logo section (matching AuthScreen)
            Box(
                modifier = Modifier.size(140.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Gold.copy(alpha = 0.2f), Color.Transparent),
                                radius = 200f,
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .rotate(logoRingRotation),
                ) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .border(1.dp, Gold.copy(alpha = 0.3f), CircleShape),
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .align(Alignment.TopCenter)
                            .offset(y = (-3).dp)
                            .clip(CircleShape)
                            .background(Gold.copy(alpha = 0.9f)),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF2C2C4E), Color(0xFF1A1A2E)),
                            )
                        )
                        .border(1.5.dp, Gold.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.logo_gold),
                        contentDescription = stringResource(R.string.destiny_logo),
                        modifier = Modifier.size(50.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = stringResource(R.string.sign_in_required),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CanelaFontFamily,
                color = Gold,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    alpha = contentOpacity
                    translationY = contentOffsetY
                },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = resolvedMessage,
                fontSize = 16.sp,
                color = CreamDim,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = contentOpacity
                    translationY = contentOffsetY
                },
            )

            Spacer(Modifier.weight(1f))

            // Apple-created accounts cannot sign in on Android — the prompt above
            // tells the user to use the iOS app. Otherwise show the Google button.
            val showGoogle = !isAppleOnlyAccount &&
                (provider == null || provider.equals("google", ignoreCase = true))

            // Continue with Google (glassSlab — outlined)
            if (showGoogle) {
            OutlinedButton(
                onClick = handleGoogle,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .graphicsLayer {
                        alpha = contentOpacity
                        translationY = contentOffsetY
                    }
                    .testTag("guest_prompt_google_button")
                    .semantics { contentDescription = "guest_prompt_google_button" },
                shape = RoundedCornerShape(14.dp),
                enabled = !isSigningIn,
                border = ButtonDefaults.outlinedButtonBorder(enabled = !isSigningIn).copy(
                    width = 1.dp,
                    brush = androidx.compose.ui.graphics.SolidColor(Gold.copy(alpha = 0.4f)),
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = CreamText,
                    containerColor = NavySurface.copy(alpha = 0.6f),
                ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.google_logo),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        contentScale = ContentScale.Fit,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.continue_with_google),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                }
            }
            } // end showGoogle

            // iOS parity gap (GuestSignInPromptView.swift:156-166): when the
            // matching account is Apple-only, iOS still renders an actionable
            // gold AuthButton calling signInWithApple(). On Android Apple has
            // no native flow, so we surface a Contact Support button (mailto)
            // so the user has a recovery path instead of a dead-end message.
            if (isAppleOnlyAccount) {
                val supportLabel = stringResource(R.string.contact_support)
                OutlinedButton(
                    onClick = {
                        haptic.playButtonTap()
                        soundManager.playButtonTap()
                        val mailto = "mailto:support@destinyaiastrology.com" +
                            "?subject=" + Uri.encode("Apple account access from Android") +
                            "&body=" + Uri.encode(
                                "I created my account with Apple Sign-In on iOS and " +
                                    "cannot sign in from Android. Please assist."
                            )
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SENDTO, Uri.parse(mailto)),
                                supportLabel,
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .graphicsLayer {
                            alpha = contentOpacity
                            translationY = contentOffsetY
                        }
                        .testTag("guest_prompt_apple_support_button")
                        .semantics { contentDescription = "guest_prompt_apple_support_button" },
                    shape = RoundedCornerShape(14.dp),
                    enabled = !isSigningIn,
                    border = ButtonDefaults.outlinedButtonBorder(enabled = !isSigningIn).copy(
                        width = 1.dp,
                        brush = androidx.compose.ui.graphics.SolidColor(Gold.copy(alpha = 0.4f)),
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = CreamText,
                        containerColor = NavySurface.copy(alpha = 0.6f),
                    ),
                ) {
                    Text(
                        text = supportLabel,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = Gold,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // iOS parity (GuestSignInPromptView.swift:62-65): back button is rendered
            // only when onBack is non-nil. Opacity animates with the rest of the
            // content (no offset on iOS — matched here).
            if (onBack != null) {
                TextButton(
                    onClick = {
                        haptic.light()
                        soundManager.playButtonTap()
                        onBack()
                    },
                    modifier = Modifier
                        .graphicsLayer { alpha = contentOpacity }
                        .testTag("guest_prompt_back")
                        .semantics { contentDescription = "guest_prompt_back" },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = Gold,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.action_back),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Gold,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }

        // Loading overlay with gold border
        if (isSigningIn) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(NavySurface)
                        .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(30.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Gold, strokeWidth = 2.dp)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.signing_in),
                            color = CreamText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

// Hilt EntryPoint that exposes the application-scoped SoundManager to this
// non-Hilt composable. Mirrors the HomeSoundEntryPoint pattern in HomeScreen.kt
// — keeps SoundManager out of the BirthDataViewModel / MainScreen call chain.
@EntryPoint
@InstallIn(SingletonComponent::class)
interface GuestPromptSoundEntryPoint {
    fun soundManager(): SoundManager
}
