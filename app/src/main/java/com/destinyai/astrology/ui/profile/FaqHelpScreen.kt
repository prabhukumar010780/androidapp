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

private val faqItems = listOf(
    "How accurate are Destiny's insights?" to
        "By combining the precision of our proprietary AI algorithm with traditional astrological expertise, Destiny's insights have been highly accurate.",
    "How is AI astrology different from consulting a human astrologer?" to
        "AI astrology provides objective insights quickly using sophisticated algorithms and extensive databases. In contrast, traditional astrologers offer personalized interpretations based on their experience and manual analysis of astrological charts.",
    "How do I update my birth details?" to
        "Go to Profile → Birth Details. You can edit your name and gender directly. For date, time, or place changes, please contact support as these affect all your readings.",
    "What information is required to start using Destiny?" to
        "To use Destiny, you'll need to provide your birth date, time, and place. This information allows us to provide highly accurate and personalized astrological advice.",
    "What astrological systems are supported?" to
        "We use Vedic (Jyotish) astrology with Lahiri Ayanamsa and Whole Sign house system for accurate calculations.",
    "What's the difference between chart styles?" to
        "North Indian style uses a diamond layout where houses are fixed and signs rotate. South Indian style uses a grid layout where signs are fixed and houses rotate.",
    "Is my data safe with Destiny?" to
        "Absolutely. Destiny employs robust security measures to ensure that your personal information is protected and kept confidential.",
    "Can I ask any question on Destiny?" to
        "Yes, Destiny is equipped to handle a broad range of questions, whether they are about personal relationships, career choices, or daily life decisions.",
    "Why should I consider astrology as a decision-making tool?" to
        "Astrology provides valuable insights into personality traits and life patterns, helping you to better prepare for future opportunities and challenges.",
    "How often is the astrological data updated?" to
        "The astrological data used by the AI Astrologer is regularly updated to reflect current cosmic movements and planetary alignments, ensuring that your readings are always up-to-date and relevant.",
    "Can astrology predict my future?" to
        "While astrology does not provide definitive predictions, it offers insights into potential life trends and upcoming opportunities, assisting you in making proactive and informed decisions.",
    "Are the astrological insights provided in real-time?" to
        "Yes, Destiny delivers astrological insights in real-time, enabling you to make informed decisions swiftly based on the latest astrological conditions.",
    "How do I cancel my subscription?" to
        "You can manage your subscription through Google Play. Go to Play Store → Account → Subscriptions on your device.",
    "Are there any terms and conditions I should be aware of?" to
        "Prior to utilizing Destiny AI Astrology services, please ensure you have reviewed our Privacy Policy and Terms of Service.",
)

@Composable
fun FaqHelpScreen(onBack: () -> Unit) {
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CreamDim)
                }
                Text(
                    text = "Help & FAQ",
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
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Common Questions",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
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
                fontWeight = FontWeight.SemiBold,
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
