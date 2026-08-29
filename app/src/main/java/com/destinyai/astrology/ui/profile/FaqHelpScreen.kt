package com.destinyai.astrology.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import com.destinyai.astrology.R
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CosmicBackground
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface
import com.destinyai.astrology.ui.theme.Spacing

@Composable
fun FaqHelpScreen(onBack: () -> Unit) {
    val faqItems = listOf(
        stringResource(R.string.faq_q_1) to stringResource(R.string.faq_a_1),
        stringResource(R.string.faq_q_2) to stringResource(R.string.faq_a_2),
        stringResource(R.string.faq_q_3) to stringResource(R.string.faq_a_3),
        stringResource(R.string.faq_q_4) to stringResource(R.string.faq_a_4),
        stringResource(R.string.faq_q_5) to stringResource(R.string.faq_a_5),
        stringResource(R.string.faq_q_6) to stringResource(R.string.faq_a_6),
        stringResource(R.string.faq_q_7) to stringResource(R.string.faq_a_7),
        stringResource(R.string.faq_q_8) to stringResource(R.string.faq_a_8),
        stringResource(R.string.faq_q_9) to stringResource(R.string.faq_a_9),
        stringResource(R.string.faq_q_10) to stringResource(R.string.faq_a_10),
        stringResource(R.string.faq_q_11) to stringResource(R.string.faq_a_11),
        stringResource(R.string.faq_q_12) to stringResource(R.string.faq_a_12),
        stringResource(R.string.faq_q_13) to stringResource(R.string.faq_a_13),
        stringResource(R.string.faq_q_14) to stringResource(R.string.faq_a_14),
    )
    CosmicBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = CreamDim)
                }
                Text(
                    text = stringResource(R.string.faq_and_help),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CanelaFontFamily,
                    color = Gold,
                    modifier = Modifier.weight(1f),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    // Pushed full-screen route (no tab bar); reserve the gesture-nav
                    // inset so the last expandable FAQ row isn't clipped by the nav bar.
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.screenH),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.common_questions),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Gold.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(4.dp))

                faqItems.forEach { (question, answer) ->
                    FaqRow(question = question, answer = answer)
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun FaqRow(question: String, answer: String) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NavySurface)
            .border(0.5.dp, Gold.copy(alpha = if (expanded) 0.4f else 0.15f), RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = question,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (expanded) Gold else CreamText,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = CreamDim.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Text(
                text = answer,
                fontSize = 13.sp,
                color = CreamDim,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                lineHeight = 19.sp,
            )
        }
    }
}
