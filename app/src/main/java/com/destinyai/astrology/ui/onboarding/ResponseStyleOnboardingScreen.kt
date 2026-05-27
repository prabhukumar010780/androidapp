package com.destinyai.astrology.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface

private data class ResponseStyleOption(
    val key: String,
    val label: String,
    val tagline: String,
    val exampleResponse: String,
)

private val styleOptions = listOf(
    ResponseStyleOption(
        key = "brief",
        label = "Brief",
        tagline = "Short, direct answers. Get to the point without extra context.",
        exampleResponse = "Saturn in 7H delays commitment. Marriage timing: 2026-27.",
    ),
    ResponseStyleOption(
        key = "balanced",
        label = "Balanced",
        tagline = "Clear explanations with key insights and practical guidance.",
        exampleResponse = "Saturn in your 7th house suggests you may experience delays in marriage or partnerships. This is a karmic placement that often leads to meaningful, long-lasting bonds once the timing is right — typically mid-to-late 2026.",
    ),
    ResponseStyleOption(
        key = "detailed",
        label = "Detailed",
        tagline = "In-depth analysis with full astrological reasoning and remedies.",
        exampleResponse = "Saturn's placement in your 7th house of partnerships indicates a pattern of karmic lessons through relationships. As the natural significator of discipline and delay, Saturn here often manifests as postponed marriage, but the relationships that do form tend to be enduring. Your current Dasha period suggests 2026-27 is a favorable window.",
    ),
)

@Composable
fun ResponseStyleOnboardingScreen(
    isSettingsMode: Boolean = false,
    onContinue: () -> Unit,
    onBack: () -> Unit = {},
) {
    var selected by remember { mutableStateOf("balanced") }

    CosmicBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isSettingsMode) {
                // Settings mode: back button header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CreamDim)
                    }
                    Text(
                        text = "Response Style",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CanelaFontFamily,
                        color = Gold,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                // Onboarding mode: capsule step dots
                Spacer(Modifier.statusBarsPadding())
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    repeat(4) { i ->
                        val isActive = i == 2
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .height(8.dp)
                                .width(if (isActive) 24.dp else 8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isActive) Gold else CreamDim.copy(alpha = 0.3f)),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))

                // Title
                Text(
                    text = buildAnnotatedString {
                        append("How do you like your ")
                        withStyle(SpanStyle(color = Gold, fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold)) {
                            append("insights?")
                        }
                    },
                    fontSize = 24.sp,
                    fontFamily = CanelaFontFamily,
                    color = CreamText,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (isSettingsMode) "Change your preferred response depth anytime." else "Choose how Destiny delivers your readings.",
                    fontSize = 14.sp,
                    color = CreamDim,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(28.dp))

                // Style cards
                styleOptions.forEach { option ->
                    ResponseStyleCard(
                        option = option,
                        isSelected = selected == option.key,
                        onSelect = { selected = option.key },
                    )
                    Spacer(Modifier.height(14.dp))
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Gold,
                        contentColor = Color(0xFF0D0D1A),
                    ),
                ) {
                    Text(
                        if (isSettingsMode) "Save" else "Continue",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun ResponseStyleCard(
    option: ResponseStyleOption,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) Gold.copy(alpha = 0.06f) else NavySurface)
            .border(
                width = 1.5.dp,
                color = if (isSelected) Gold.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(16.dp),
            )
            .clickable { onSelect() }
            .padding(20.dp),
    ) {
        Column {
            // Title row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = option.label,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = CanelaFontFamily,
                    color = CreamText,
                    modifier = Modifier.weight(1f),
                )
                // Radio indicator
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .border(
                            width = 1.5.dp,
                            color = if (isSelected) Gold else CreamDim.copy(alpha = 0.5f),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Gold),
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = option.tagline,
                fontSize = 13.sp,
                color = CreamDim,
                lineHeight = 18.sp,
            )

            Spacer(Modifier.height(16.dp))

            // Example box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.25f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(10.dp))
                    .padding(14.dp),
            ) {
                Column {
                    Text(
                        text = "EXAMPLE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CreamDim.copy(alpha = 0.5f),
                        letterSpacing = 1.2.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "\"How is my career this year?\"",
                        fontSize = 11.sp,
                        fontStyle = FontStyle.Italic,
                        color = CreamDim.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = option.exampleResponse,
                        fontSize = 12.sp,
                        color = CreamText.copy(alpha = 0.75f),
                        lineHeight = 17.sp,
                    )
                }
            }
        }
    }
}
