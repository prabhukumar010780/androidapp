package com.destinyai.astrology.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.R
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.NavyVariant

@Composable
fun AuthScreen(
    onNavigateToMain: () -> Unit,
    onNavigateToBirthData: () -> Unit,
    onNavigateToWaitlist: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isAuthenticated) {
        if (state.isAuthenticated) onNavigateToMain()
    }

    LaunchedEffect(state.forceLogout) {
        if (state.forceLogout) viewModel.logout()
    }

    CosmicBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Logo circle with real logo image
            Box(
                modifier = Modifier.size(96.dp),
                contentAlignment = Alignment.Center,
            ) {
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
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.logo_gold),
                        contentDescription = "Destiny Logo",
                        modifier = Modifier.size(56.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // "Welcome to Destiny" — Canela font
            Text(
                text = stringResource(R.string.welcome_to_destiny),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CanelaFontFamily,
                color = Gold,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            // "Sign in to save your birth chart and chats"
            Text(
                text = stringResource(R.string.sign_in_save),
                fontSize = 16.sp,
                color = CreamDim,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(48.dp))

            if (state.error != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3A1A1A)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = state.error ?: "",
                        color = Color(0xFFFF8A80),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // Button 1: Continue with Apple (gold filled)
            Button(
                onClick = { /* Apple Sign In — launches intent */ },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = !state.isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Gold,
                    contentColor = Color(0xFF0D0D1A),
                ),
            ) {
                Text(
                    text = "  " + stringResource(R.string.continue_with_apple),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
            }

            Spacer(Modifier.height(12.dp))

            // Button 2: Continue with Google (glass/outlined style)
            OutlinedButton(
                onClick = { /* Google sign-in launches activity */ },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = !state.isLoading,
                border = ButtonDefaults.outlinedButtonBorder(enabled = !state.isLoading).copy(width = 1.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = CreamText,
                    containerColor = NavySurface.copy(alpha = 0.6f),
                ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.Image(
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

            Spacer(Modifier.height(24.dp))

            // "or" divider with gold fade lines
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Gold.copy(alpha = 0.4f))
                            )
                        )
                )
                Text(
                    text = "  or  ",
                    color = Color(0xFF718096),
                    fontSize = 14.sp,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Gold.copy(alpha = 0.4f), Color.Transparent)
                            )
                        )
                )
            }

            Spacer(Modifier.height(20.dp))

            // "Continue as Guest"
            TextButton(
                onClick = { viewModel.continueAsGuest() },
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Gold,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.continue_as_guest),
                        color = Gold,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // Terms
            Text(
                text = buildAnnotatedString {
                    append("By continuing, you agree to our ")
                    withStyle(SpanStyle(color = Gold)) { append("Terms of Service") }
                    append(" and ")
                    withStyle(SpanStyle(color = Gold)) { append("Privacy Policy") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF718096),
                textAlign = TextAlign.Center,
            )
        }

        // Loading overlay
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NavySurface),
                    modifier = Modifier.padding(32.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(color = Gold, strokeWidth = 2.dp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.signing_in),
                            color = CreamText,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}
