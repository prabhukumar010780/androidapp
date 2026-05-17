package com.destinyai.astrology.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinyai.astrology.R
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.NavyVariant
import com.destinyai.astrology.ui.theme.CanelaFontFamily

@Composable
fun LanguageSelectionScreen(
    onNavigateNext: () -> Unit,
    viewModel: LanguageSelectionViewModel = hiltViewModel(),
) {
    val selectedCode by viewModel.selectedCode.collectAsStateWithLifecycle()

    CosmicBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(64.dp))

            // Globe icon
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF2C2C4E), Color(0xFF1A1A2E)),
                        )
                    )
                    .border(1.dp, Gold.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "🌏", fontSize = 32.sp)
            }

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

            // 3-column grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(viewModel.languages) { lang ->
                    val isSelected = lang.code == selectedCode
                    LanguageCard(
                        lang = lang,
                        isSelected = isSelected,
                        onClick = { viewModel.selectLanguage(lang.code) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Continue button
            val selectedLang = viewModel.languages.find { it.code == selectedCode }
            val buttonText = if (selectedLang != null) {
                "Continue in ${selectedLang.nativeName}"
            } else {
                stringResource(R.string.select_a_language)
            }

            Button(
                onClick = {
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
    }
}

@Composable
private fun LanguageCard(
    lang: LanguageOption,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) {
                    Brush.linearGradient(listOf(Color(0xFF2C2C40), Color(0xFF1A1A3A)))
                } else {
                    Brush.linearGradient(listOf(NavySurface, NavySurface))
                }
            )
            .border(
                width = if (isSelected) 1.5.dp else 0.5.dp,
                color = if (isSelected) Gold else Gold.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
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
                color = if (isSelected) Gold else CreamText,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = lang.name,
                fontSize = 11.sp,
                color = CreamDim,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}
