package com.destinyai.astrology.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold

@Composable
fun WaitlistPendingScreen(
    onSignedOut: () -> Unit,
    viewModel: WaitlistPendingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.loadEmail() }
    LaunchedEffect(state.isSignedOut) { if (state.isSignedOut) onSignedOut() }

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

                Text(text = "📋", fontSize = 56.sp)
                Spacer(Modifier.height(24.dp))

                Text(
                    text = "You're on the list",
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
                    text = "Destiny is in early access. We'll let you know as soon as you're approved.",
                    fontSize = 16.sp,
                    color = CreamDim,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp,
                )

                Spacer(Modifier.height(48.dp))

                // Outlined gold CTA
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://tally.so/r/destinyai"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(width = 1.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Gold),
                ) {
                    Text("Fill out this form", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Already filled it out? Hang tight, your turn is coming.",
                    fontSize = 13.sp,
                    color = CreamDim,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.weight(1f))

                TextButton(
                    onClick = { viewModel.signOut() },
                    modifier = Modifier.padding(bottom = 32.dp),
                ) {
                    Text("Back to Login", fontSize = 15.sp, color = CreamDim)
                }
            }
        }
    }
}
