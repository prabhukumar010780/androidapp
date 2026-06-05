package com.destinyai.astrology.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.destinyai.astrology.R
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun WaitlistPendingScreen(
    onSignedOut: () -> Unit,
    onAccessGranted: (hasBirthData: Boolean) -> Unit = {},
    viewModel: WaitlistPendingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val tallyUrlFormat = stringResource(R.string.waitlist_tally_url_format)
    val supportEmail = stringResource(R.string.waitlist_support_email)

    LaunchedEffect(Unit) {
        viewModel.loadEmail()
        // iOS parity (AppRootView.swift:83-85): poll /subscription/register on
        // appear so an off-screen waitlist approval forwards the user without
        // requiring an app restart.
        viewModel.startRecheckPolling()
    }
    // iOS parity (AppRootView.swift:138-162 .task recheckWaitlistStatus): also
    // re-poll on every app foreground so a server-side approval that lands
    // while the screen is still composed but the app is backgrounded still
    // forwards the user immediately on resume.
    androidx.compose.runtime.DisposableEffect(Unit) {
        val owner = androidx.lifecycle.ProcessLifecycleOwner.get()
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                scope.launch { viewModel.recheckOnce() }
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.isSignedOut) { if (state.isSignedOut) onSignedOut() }
    LaunchedEffect(state.accessState) {
        if (state.accessState != "waitlist_pending") onAccessGranted(state.hasBirthDataOnAccess)
    }

    CosmicBackground {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.waitlist_title),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CanelaFontFamily,
                    color = CreamText,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))

                if (state.userEmail.isNotEmpty()) {
                    Text(text = state.userEmail, fontSize = 14.sp, color = CreamDim)
                    Spacer(Modifier.height(8.dp))
                }

                Text(
                    text = stringResource(R.string.waitlist_body_1),
                    fontSize = 16.sp,
                    color = CreamDim,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.waitlist_body_2),
                    fontSize = 16.sp,
                    color = CreamDim,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp,
                )

                Spacer(Modifier.height(48.dp))

                // Outlined gold CTA
                OutlinedButton(
                    onClick = {
                        val encodedEmail = URLEncoder.encode(
                            state.userEmail,
                            StandardCharsets.UTF_8.name(),
                        )
                        val tallyUrl = tallyUrlFormat.format(encodedEmail)
                        val customTabsIntent = CustomTabsIntent.Builder().build()
                        customTabsIntent.launchUrl(context, Uri.parse(tallyUrl))
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(width = 1.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Gold),
                ) {
                    Text(
                        stringResource(R.string.waitlist_cta),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.waitlist_already_filled),
                    fontSize = 13.sp,
                    color = CreamDim,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.waitlist_support_prefix),
                    fontSize = 13.sp,
                    color = CreamDim,
                    textAlign = TextAlign.Center,
                )
                TextButton(
                    onClick = {
                        val mailIntent = Intent(
                            Intent.ACTION_SENDTO,
                            Uri.parse("mailto:$supportEmail"),
                        )
                        context.startActivity(mailIntent)
                    },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        text = supportEmail,
                        fontSize = 13.sp,
                        color = Gold,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.weight(1f))

                TextButton(
                    onClick = { viewModel.signOut() },
                    modifier = Modifier.padding(bottom = 32.dp),
                ) {
                    Text(
                        stringResource(R.string.waitlist_back_to_login),
                        fontSize = 15.sp,
                        color = CreamDim,
                    )
                }
            }
        }
    }
}
