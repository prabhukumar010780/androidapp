package com.destinyai.astrology.ui.auth

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.R
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.components.auth.AuthOrbitalRings
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface

@Composable
fun GuestSignInPromptScreen(
    message: String = "Sign in to access this feature",
    onSignIn: () -> Unit,
    onBack: () -> Unit,
    provider: String? = null,
) {
    var isSigningIn by remember { mutableStateOf(false) }

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
                        contentDescription = "Destiny Logo",
                        modifier = Modifier.size(50.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = "Sign In Required",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CanelaFontFamily,
                color = Gold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                fontSize = 16.sp,
                color = CreamDim,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
            )

            Spacer(Modifier.weight(1f))

            // R2-A11: Provider filter — hide button if provider doesn't match
            val showApple = provider == null || provider.equals("apple", ignoreCase = true)
            val showGoogle = provider == null || provider.equals("google", ignoreCase = true)

            // Continue with Apple (goldSlab — gradient fill)
            if (showApple) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFFD4AF37), Color(0xFFF5D060), Color(0xFFD4AF37)),
                            start = Offset(0f, 0f),
                            end = Offset(900f, 0f),
                        )
                    )
                    .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                TextButton(
                    onClick = onSignIn,
                    modifier = Modifier.fillMaxSize(),
                    enabled = !isSigningIn,
                ) {
                    Text(
                        text = "Continue with Apple",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = Color(0xFF0D0D1A),
                    )
                }
            }
            } // end showApple

            if (showApple && showGoogle) Spacer(Modifier.height(12.dp))

            // Continue with Google (glassSlab — outlined)
            if (showGoogle) {
            OutlinedButton(
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth().height(54.dp),
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
                        text = "Continue with Google",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                }
            }
            } // end showGoogle

            Spacer(Modifier.height(24.dp))

            // Back button — gold with chevron (matching iOS)
            TextButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Back",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Gold,
                )
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
                            text = "Signing in...",
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
