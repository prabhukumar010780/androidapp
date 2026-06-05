package com.destinyai.astrology.ui.auth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.BuildConfig
import com.destinyai.astrology.R
import com.destinyai.astrology.services.HapticManager
import com.destinyai.astrology.services.SoundManager
import com.destinyai.astrology.ui.components.GoldGradientText
import com.destinyai.astrology.ui.components.auth.AuthLogo
import com.destinyai.astrology.ui.components.auth.AuthOrbitalRings
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
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.launch

private const val TERMS_OF_SERVICE_URL = "https://www.destinyaiastrology.com/terms-of-service/"
private const val PRIVACY_POLICY_URL = "https://www.destinyaiastrology.com/privacy-policy/"

/**
 * Extract the `sub` claim from a Google ID JWT (header.payload.signature).
 * Used to obtain Google's stable per-user identifier for the backend's
 * `google_id` lookup. Returns null when the token is malformed — caller
 * falls back to GoogleIdTokenCredential.id (email) so sign-in still proceeds.
 */
private fun decodeJwtSub(idToken: String): String? = runCatching {
    val parts = idToken.split('.')
    if (parts.size < 2) return@runCatching null
    val payloadBytes = android.util.Base64.decode(
        parts[1],
        android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP,
    )
    val payload = String(payloadBytes, Charsets.UTF_8)
    val root = com.google.gson.JsonParser.parseString(payload)
    if (!root.isJsonObject) return@runCatching null
    val sub = root.asJsonObject.get("sub") ?: return@runCatching null
    if (sub.isJsonPrimitive) sub.asString else null
}.getOrNull()

@Composable
fun AuthScreen(
    onNavigateToMain: () -> Unit,
    onNavigateToBirthData: () -> Unit,
    onNavigateToWaitlist: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
    // iOS parity: backend-driven guest CTA visibility. Caller may override
    // (e.g. tests). null = read from AuthViewModel state (the production path).
    allowGuest: Boolean? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val effectiveAllowGuest = allowGuest ?: state.allowGuest

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val haptic = remember { HapticManager(context) }

    // Legacy GoogleSignIn launcher — used as fallback when CredentialManager
    // throws NoCredentialException (emulator / device with no Google account
    // pre-configured). Opens Google's web-based account chooser, which works
    // without a device account and returns an ID token via onActivityResult.
    val legacySignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        android.util.Log.i("AuthScreen", "Legacy launcher result: resultCode=${result.resultCode} data=${result.data}")
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                android.util.Log.i("AuthScreen", "Legacy account: email=${account.email} id=${account.id} idToken=${account.idToken?.take(20)} authCode=${account.serverAuthCode?.take(20)}")
                val email = account.email
                val googleId = account.id
                val name = account.displayName
                val idToken = account.idToken
                if (!email.isNullOrBlank() && !googleId.isNullOrBlank()) {
                    viewModel.signInWithGoogle(
                        email = email,
                        googleId = googleId,
                        name = name,
                        idToken = idToken,
                    )
                } else {
                    viewModel.reportGoogleSignInError(
                        context.getString(R.string.google_sign_in_unavailable)
                    )
                }
            } catch (e: ApiException) {
                android.util.Log.e("AuthScreen", "Legacy GoogleSignIn ApiException code=${e.statusCode} msg=${e.message}", e)
                if (e.statusCode == com.google.android.gms.common.api.CommonStatusCodes.CANCELED) {
                    // User cancelled — silent
                } else {
                    viewModel.reportGoogleSignInError(
                        context.getString(R.string.google_sign_in_failed_generic)
                    )
                }
            }
        } else {
            android.util.Log.w("AuthScreen", "Legacy launcher non-OK result: resultCode=${result.resultCode}")
            // Try to extract error from intent anyway
            result.data?.let { intent ->
                val task = GoogleSignIn.getSignedInAccountFromIntent(intent)
                try {
                    task.getResult(ApiException::class.java)
                } catch (e: ApiException) {
                    android.util.Log.e("AuthScreen", "Legacy non-OK ApiException code=${e.statusCode}", e)
                }
            }
        }
    }

    // Modern Google Sign-In via Jetpack Credential Manager + Sign-in-with-Google.
    // Replaces the deprecated com.google.android.gms.auth.api.signin.* flow.
    // Returns a GoogleIdTokenCredential whose .idToken is exchanged with the
    // backend exactly like the legacy account.idToken.
    suspend fun signInWithGoogle() {
        if (BuildConfig.GOOGLE_SERVER_CLIENT_ID.isBlank()) {
            viewModel.reportGoogleSignInError(
                context.getString(R.string.google_sign_in_unavailable)
            )
            return
        }
        val activity = context as? Activity
        if (activity == null) {
            viewModel.reportGoogleSignInError(
                context.getString(R.string.google_sign_in_unavailable)
            )
            return
        }
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.GOOGLE_SERVER_CLIENT_ID)
            // First-time UX: don't restrict to previously-authorized accounts so
            // the chooser appears for users who haven't signed in before.
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        try {
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            android.util.Log.i("AuthScreen", "Got credential type=${credential.type} class=${credential.javaClass.simpleName}")
            if (credential is androidx.credentials.CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                try {
                    val googleIdTokenCredential =
                        GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken
                    android.util.Log.i("AuthScreen", "Got idToken len=${idToken.length} preview=${idToken.take(20)}...")
                    // iOS parity (ProfileService.registerUser): backend
                    // /subscription/register requires `email` and looks up users
                    // by `google_id`. Decode the JWT `sub` claim for the stable
                    // Google account id; fall back to credential.id when the
                    // token cannot be parsed.
                    val sub = decodeJwtSub(idToken)
                    val email = googleIdTokenCredential.id
                    val name = googleIdTokenCredential.displayName
                    val googleId = sub ?: googleIdTokenCredential.id
                    if (email.isBlank() || googleId.isBlank()) {
                        viewModel.reportGoogleSignInError(
                            context.getString(R.string.google_sign_in_unavailable)
                        )
                    } else {
                        viewModel.signInWithGoogle(
                            email = email,
                            googleId = googleId,
                            name = name,
                            idToken = idToken,
                        )
                    }
                } catch (e: GoogleIdTokenParsingException) {
                    android.util.Log.e("AuthScreen", "GoogleIdTokenParsingException", e)
                    viewModel.reportGoogleSignInError(
                        context.getString(R.string.google_sign_in_unavailable)
                    )
                }
            } else {
                android.util.Log.e("AuthScreen", "Wrong credential type: ${credential.type}")
                viewModel.reportGoogleSignInError(
                    context.getString(R.string.google_sign_in_unavailable)
                )
            }
        } catch (e: GetCredentialCancellationException) {
            android.util.Log.i("AuthScreen", "User cancelled Google sign-in")
            // User cancelled — silent (matches iOS user-cancel behavior).
        } catch (e: NoCredentialException) {
            android.util.Log.w("AuthScreen", "NoCredentialException — falling back to legacy GoogleSignIn web flow")
            // No Google account on device (common on emulators). Fall back to the
            // legacy GoogleSignIn flow which opens a web-based account chooser.
            if (BuildConfig.GOOGLE_SERVER_CLIENT_ID.isBlank()) {
                viewModel.reportGoogleSignInError(
                    context.getString(R.string.google_sign_in_unavailable)
                )
                return@signInWithGoogle
            }
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(BuildConfig.GOOGLE_SERVER_CLIENT_ID)
                .requestEmail()
                .requestProfile()
                .build()
            val client = GoogleSignIn.getClient(context, gso)
            client.signOut()
            legacySignInLauncher.launch(client.signInIntent)
        } catch (e: GetCredentialException) {
            android.util.Log.e("AuthScreen", "GetCredentialException type=${e.type} msg=${e.message}", e)
            viewModel.reportGoogleSignInError(
                context.getString(R.string.google_sign_in_failed_generic)
            )
        } catch (e: Exception) {
            android.util.Log.e("AuthScreen", "Unexpected exception in signInWithGoogle", e)
            viewModel.reportGoogleSignInError(
                context.getString(R.string.google_sign_in_failed_generic)
            )
        }
    }

    LaunchedEffect(state.isAuthenticated) {
        // iOS parity (AppRootView post-auth routing): branch on whether the
        // signed-in user already has birth data. Registered sign-ins fetch the
        // server profile and mirror locally; guests rely on local prefs alone.
        // See AuthViewModel.resolveNeedsBirthData for the decision.
        if (state.isAuthenticated) {
            if (state.needsBirthData) onNavigateToBirthData() else onNavigateToMain()
        }
    }

    LaunchedEffect(state.pendingWaitlist) {
        // iOS parity (AuthViewModel.swift:261): waitlist_pending users go to
        // WaitlistPendingScreen instead of the main tab.
        if (state.pendingWaitlist) onNavigateToWaitlist()
    }

    LaunchedEffect(state.forceLogout) {
        if (state.forceLogout) viewModel.logout()
    }

    LaunchedEffect(state.error) {
        // Ignore user-cancel errors. iOS parity uses ASAuthorizationError 1000/1001;
        // on Android the primary detection is Google's locale-independent statusCode,
        // and this string-match is a secondary defense for the OAuth/CustomTabs path
        // where only an exception message is available.
        val err = state.error ?: return@LaunchedEffect
        if (err.contains("cancelled", ignoreCase = true) ||
            err.contains("canceled", ignoreCase = true) ||
            err.contains("user_cancel", ignoreCase = true) ||
            err.contains("1000") ||
            err.contains("1001")
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

    // Continuous orbital ring rotation — 30s per revolution. Mirrors iOS
    // `AuthView.startAnimations()` which animates `orbitRotation` 0->360
    // linearly forever, feeding `OrbitalRingsView`.
    val orbitTransition = rememberInfiniteTransition(label = "auth-orbital-rings")
    val orbitRotation by orbitTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 30_000,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbit-rotation",
    )

    CosmicBackground {
        Box(modifier = Modifier.fillMaxSize()) {
        // Layer 2: Orbital rings (ambient decoration, behind content).
        // Mirrors iOS `OrbitalRingsView(rotation: orbitRotation).opacity(0.25)`.
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            AuthOrbitalRings(rotation = orbitRotation)
        }
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

            // Continue with Google (glass/outlined style)
            OutlinedButton(
                onClick = {
                    // iOS parity (AuthView.swift:380-384): haptic + sound on
                    // every auth-button tap.
                    haptic.playButtonTap()
                    viewModel.playButtonTapSound()
                    coroutineScope.launch { signInWithGoogle() }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("auth_google_button"),
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

            Spacer(Modifier.height(8.dp))

            // iOS parity gap (AuthView.swift:188-196): iOS shows a primary gold
            // "Continue with Apple" button via ASAuthorizationController. Apple's
            // Sign-In flow has no native Android UI and we no longer launch the
            // OAuth web fallback, so we surface a visible inline notice instead
            // of silently omitting the option. Users who originally signed up
            // with Apple need to know they should use the iOS app.
            Text(
                text = stringResource(R.string.apple_unavailable_inline_notice),
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .testTag("auth_apple_unavailable_notice"),
            )

            // iOS parity (AuthView.swift:222-229): the auth error is rendered
            // BELOW the auth buttons (not above), as small caption-sized text
            // in the error color. We mirror that hierarchy here.
            if (state.error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = state.error ?: "",
                    color = Color(0xFFFF8A80),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_error_text"),
                )
            }

            Spacer(Modifier.height(24.dp))

            if (effectiveAllowGuest) {
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
                    onClick = {
                        // iOS parity (AuthView.swift:268-271): haptic + sound
                        // on continue-as-guest tap.
                        haptic.playButtonTap()
                        viewModel.playButtonTapSound()
                        viewModel.continueAsGuest()
                    },
                    enabled = !state.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_continue_as_guest"),
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
                    modifier = Modifier
                        .padding(32.dp)
                        .border(
                            width = 1.dp,
                            color = Gold.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(16.dp),
                        ),
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
            onCheckedChange = {
                // iOS parity (AuthView.swift:111-112): light haptic before
                // SoundManager.toggleSound() so the user feels the tap even
                // when audio is off.
                haptic.light()
                viewModel.toggleSound()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 8.dp, top = 4.dp)
                .testTag("auth_sound_toggle"),
        ) {
            Icon(
                imageVector = if (state.isSoundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                contentDescription = if (state.isSoundEnabled) stringResource(R.string.sound_on_a11y) else stringResource(R.string.sound_off_a11y),
                tint = Gold.copy(alpha = 0.8f),
            )
        }
        } // end wrapping Box
    }
}
