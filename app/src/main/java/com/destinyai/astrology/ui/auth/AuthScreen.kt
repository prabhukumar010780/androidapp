package com.destinyai.astrology.ui.auth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.destinyai.astrology.BuildConfig
import com.destinyai.astrology.R
import com.destinyai.astrology.ui.components.GoldGradientText
import com.destinyai.astrology.ui.components.ShimmerButton
import com.destinyai.astrology.ui.components.auth.AuthLogo
import com.destinyai.astrology.ui.theme.AuthDimens
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.NavyVariant
import com.destinyai.astrology.ui.theme.TextTertiary
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

private const val TERMS_OF_SERVICE_URL = "https://www.destinyaiastrology.com/terms-of-service/"
private const val PRIVACY_POLICY_URL = "https://www.destinyaiastrology.com/privacy-policy/"

@Composable
fun AuthScreen(
    onNavigateToMain: () -> Unit,
    onNavigateToBirthData: () -> Unit,
    onNavigateToWaitlist: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
    allowGuest: Boolean = true,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
                account.idToken?.let { viewModel.signInWithGoogle(it) }
            } catch (_: ApiException) {
                // sign-in cancelled or failed — ViewModel error state not updated here
            }
        }
    }
    val googleSignInOptions = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(BuildConfig.GOOGLE_SERVER_CLIENT_ID)
            .requestEmail()
            .build()
    }

    LaunchedEffect(state.isAuthenticated) {
        if (state.isAuthenticated) onNavigateToMain()
    }

    LaunchedEffect(state.forceLogout) {
        if (state.forceLogout) viewModel.logout()
    }

    LaunchedEffect(state.error) {
        // R2-A9: ignore user-cancel errors
        val err = state.error ?: return@LaunchedEffect
        if (err.contains("cancelled", ignoreCase = true) ||
            err.contains("canceled", ignoreCase = true) ||
            err.contains("user_cancel", ignoreCase = true)
        ) {
            viewModel.clearError()
        }
    }

    // Logo entrance spring (scale 0.6 -> 1.0 once at first composition).
    // Mirrors iOS `AppTheme.Auth.logoSpring` (response: 0.7, dampingFraction: 0.65).
    val logoScaleAnim = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) {
        logoScaleAnim.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = AuthDimens.logoSpringDampingRatio,
                stiffness = AuthDimens.logoSpringStiffness,
            ),
        )
    }

    CosmicBackground {
        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Animated logo (radial glow + rotating ring + orbiting dot
            // + bioRhythm-pulsing logo image). 1:1 with iOS AuthView
            // `logoSection`.
            AuthLogo(
                entranceScale = logoScaleAnim.value,
                bioRhythmActive = !state.isLoading,
            )

            Spacer(Modifier.height(AuthDimens.logoToTextSpacing))

            // "Welcome to Destiny" — gold gradient title (Canela font),
            // matches iOS `goldGradient()` text style.
            GoldGradientText(
                text = stringResource(R.string.welcome_to_destiny),
                fontSize = AuthDimens.titleSize,
            )

            Spacer(Modifier.height(8.dp))

            // "Sign in to save your birth chart and chats"
            Text(
                text = stringResource(R.string.sign_in_save),
                fontSize = AuthDimens.subtitleSize,
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

            // Button 1: Continue with Apple — R2-A8 ShimmerButton
            ShimmerButton(
                text = stringResource(R.string.continue_with_apple),
                onClick = { /* Apple Sign In — launches intent */ },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                enabled = !state.isLoading,
            )

            Spacer(Modifier.height(12.dp))

            // Button 2: Continue with Google (glass/outlined style)
            OutlinedButton(
                onClick = {
                    val client = GoogleSignIn.getClient(context, googleSignInOptions)
                    googleSignInLauncher.launch(client.signInIntent)
                },
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

            if (allowGuest) {
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
                        text = "  ${stringResource(R.string.or)}  ",
                        color = TextTertiary,
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
            }

            Spacer(Modifier.height(32.dp))

            // Terms + Privacy footer — fully localized, with both links
            // rendered as separate clickable TextButtons so each opens its
            // own URL via Intent.ACTION_VIEW. Mirrors iOS AuthView terms
            // section (AuthView.swift:283-314).
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.by_continuing),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    textAlign = TextAlign.Center,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(TERMS_OF_SERVICE_URL))
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.terms_of_service),
                            color = Gold,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                        )
                    }
                    Text(
                        text = stringResource(R.string.and),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                    )
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.privacy_policy),
                            color = Gold,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
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

        // R2-A1: Sound toggle — top-right corner overlay
        IconToggleButton(
            checked = state.isSoundEnabled,
            onCheckedChange = { viewModel.toggleSound() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 8.dp, top = 4.dp),
        ) {
            Icon(
                imageVector = if (state.isSoundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                contentDescription = if (state.isSoundEnabled) "Mute sound" else "Unmute sound",
                tint = Gold.copy(alpha = 0.8f),
            )
        }
        } // end wrapping Box
    }
}
