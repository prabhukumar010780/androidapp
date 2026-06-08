package com.destinyai.astrology.ui.compatibility

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.R
import com.destinyai.astrology.ui.theme.Gold
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import java.util.Locale

private val GoldColor = Color(0xFFD4B03A)

// Pure helpers — unit testable
internal fun shareCardStarCount(isRecommended: Boolean, percentage: Double): Int {
    if (!isRecommended) return 1
    val pct = percentage * 100
    return when {
        pct >= 90 -> 5
        pct >= 75 -> 4
        pct >= 60 -> 3
        pct >= 50 -> 2
        else -> 1
    }
}

/**
 * Returns the string-resource id for the rating label so the caller can resolve
 * it via `stringResource()` in a Composable scope. Mirrors iOS `ratingText`
 * which routes through `*.localized` keys.
 */
internal fun shareCardRatingTextResId(isRecommended: Boolean, percentage: Double): Int {
    if (!isRecommended) return R.string.not_recommended
    val pct = percentage * 100
    return when {
        pct >= 90 -> R.string.excellent
        pct >= 75 -> R.string.very_good
        pct >= 60 -> R.string.good
        pct >= 50 -> R.string.average
        else -> R.string.not_recommended
    }
}

/**
 * Non-localized rating-label helper retained for unit tests / non-Composable
 * call sites. The composable should prefer [shareCardRatingTextResId] +
 * `stringResource(...)` for true localization parity with iOS.
 */
internal fun shareCardRatingText(isRecommended: Boolean, percentage: Double): String {
    if (!isRecommended) return "Not Recommended"
    val pct = percentage * 100
    return when {
        pct >= 90 -> "Excellent"
        pct >= 75 -> "Very Good"
        pct >= 60 -> "Good"
        pct >= 50 -> "Average"
        else -> "Not Recommended"
    }
}

@Composable
fun ShareCardView(
    boyName: String,
    girlName: String,
    totalScore: Int,
    maxScore: Int,
    percentage: Double,
    isRecommended: Boolean = true,
    adjustedScore: Int? = null,
    modifier: Modifier = Modifier,
    forSharing: Boolean = false,
) {
    val displayScore = adjustedScore ?: totalScore
    val displayPercentage = if (maxScore > 0) displayScore.toDouble() / maxScore else 0.0
    val starCount = shareCardStarCount(isRecommended, percentage)
    val ratingText = stringResource(shareCardRatingTextResId(isRecommended, percentage))

    // iOS parity: when used for export, the card is rendered at a fixed
    // 1080dp x 1080dp surface so the off-screen bitmap matches the iOS
    // 1080x1080 social-share dimensions exactly. In-app previews continue to
    // honor the caller-provided modifier (square via aspectRatio).
    val sizeModifier = if (forSharing) {
        Modifier.size(1080.dp)
    } else {
        modifier.aspectRatio(1f)
    }

    Box(
        modifier = sizeModifier
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF0B0F19),
                        Color(0xFF141A2E),
                        Color(0xFF0B0F19),
                    ),
                ),
            )
            .drawBehind {
                val cornerSize = 20.dp.toPx()
                val strokeW = 1.5.dp.toPx()
                val inset = 12.dp.toPx()
                val color = GoldColor.copy(alpha = 0.4f)
                val stroke = Stroke(width = strokeW)
                // Top-left
                drawLine(color, Offset(inset, inset + cornerSize), Offset(inset, inset), strokeW)
                drawLine(color, Offset(inset, inset), Offset(inset + cornerSize, inset), strokeW)
                // Top-right
                drawLine(color, Offset(size.width - inset - cornerSize, inset), Offset(size.width - inset, inset), strokeW)
                drawLine(color, Offset(size.width - inset, inset), Offset(size.width - inset, inset + cornerSize), strokeW)
                // Bottom-left
                drawLine(color, Offset(inset, size.height - inset - cornerSize), Offset(inset, size.height - inset), strokeW)
                drawLine(color, Offset(inset, size.height - inset), Offset(inset + cornerSize, size.height - inset), strokeW)
                // Bottom-right
                drawLine(color, Offset(size.width - inset - cornerSize, size.height - inset), Offset(size.width - inset, size.height - inset), strokeW)
                drawLine(color, Offset(size.width - inset, size.height - inset - cornerSize), Offset(size.width - inset, size.height - inset), strokeW)
            },
        contentAlignment = Alignment.Center,
    ) {
        // Radial glow behind score
        Box(
            modifier = Modifier
                .size(260.dp)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(GoldColor.copy(alpha = 0.12f), Color.Transparent),
                        ),
                    )
                },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))

            // Brand logo (iOS parity: logo_gold rendered above the wordmark, 80dp tall)
            Image(
                painter = painterResource(id = R.drawable.logo_gold),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(80.dp),
            )

            Spacer(Modifier.height(8.dp))

            // Brand wordmark
            Text(
                stringResource(id = R.string.destiny_ai_astrology_brand),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = GoldColor,
                letterSpacing = 4.sp,
            )

            Spacer(Modifier.height(24.dp))

            // Names
            Text(
                boyName.uppercase(Locale.getDefault()),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(vertical = 6.dp),
            ) {
                GoldDividerLine(modifier = Modifier.weight(1f))
                Text("♡", fontSize = 14.sp, color = GoldColor)
                GoldDividerLine(modifier = Modifier.weight(1f))
            }

            Text(
                girlName.uppercase(Locale.getDefault()),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(20.dp))

            // Score circle
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .drawBehind {
                        val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                        val sweepAngle = (displayPercentage * 360).toFloat()
                        // Track
                        drawArc(
                            color = GoldColor.copy(alpha = 0.2f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = stroke,
                        )
                        // Progress
                        drawArc(
                            brush = Brush.sweepGradient(listOf(GoldColor, Color(0xFFF5D060))),
                            startAngle = -90f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            style = stroke,
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$displayScore",
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        lineHeight = 42.sp,
                    )
                    Text("/$maxScore", fontSize = 14.sp, color = GoldColor)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Stars
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until 5) {
                    Icon(
                        imageVector = if (i < starCount) Icons.Default.Star else Icons.Outlined.StarOutline,
                        contentDescription = null,
                        tint = GoldColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Text(
                ratingText.uppercase(Locale.getDefault()),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (!isRecommended) Color(0xFFFC8181) else GoldColor,
                letterSpacing = 3.sp,
                modifier = Modifier.padding(top = 6.dp),
            )

            // Transparency: always render a score caption (iOS parity). Use the
            // adjusted-score format when an override is in effect, otherwise
            // fall back to the plain raw-score format.
            val scoreCaption = if (adjustedScore != null && adjustedScore != totalScore) {
                stringResource(
                    id = R.string.ashtakoot_adjusted_score_format,
                    totalScore,
                    maxScore,
                    adjustedScore,
                    maxScore,
                )
            } else {
                stringResource(id = R.string.ashtakoot_score_format, totalScore, maxScore)
            }
            Text(
                scoreCaption,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.padding(top = 4.dp),
            )

            if (!isRecommended && adjustedScore != null && adjustedScore != totalScore) {
                Text(
                    stringResource(id = R.string.overridden_due_to_dosha),
                    fontSize = 9.sp,
                    color = Color(0xFFFC8181).copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Spacer(Modifier.weight(1f))

            // Footer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, GoldColor.copy(alpha = 0.5f), Color.Transparent),
                        ),
                    ),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "destinyaiastrology.com",
                fontSize = 12.sp,
                color = GoldColor.copy(alpha = 0.6f),
                letterSpacing = 2.sp,
            )
        }
    }
}

@Composable
private fun GoldDividerLine(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, GoldColor.copy(alpha = 0.6f), Color.Transparent),
                ),
            ),
    )
}

/**
 * Pure helper used by unit tests to assert that the dosha-override caption is
 * shown when (and only when) an override actually fired (not recommended AND
 * the adjusted score differs from the raw total). Returns the string-resource
 * id (or `null` when no caption should be shown) so the Composable caller can
 * resolve via `stringResource(...)` for true localization parity with iOS.
 * Kept as a side-effect-free utility so test coverage doesn't depend on
 * Composable rendering.
 */
internal fun shareCardDoshaOverrideTextResId(
    isRecommended: Boolean,
    adjustedScore: Int,
    totalScore: Int,
): Int? = if (!isRecommended && adjustedScore != totalScore) {
    R.string.overridden_due_to_dosha
} else {
    null
}

/**
 * Backwards-compatible wrapper retained so existing unit tests can assert
 * blank vs non-blank without a Composable scope. Returns a sentinel
 * non-blank string when the caption would be shown — callers that need
 * the actual localized text must use [shareCardDoshaOverrideTextResId] +
 * `stringResource(...)`.
 */
internal fun shareCardDoshaOverrideText(
    isRecommended: Boolean,
    adjustedScore: Int,
    totalScore: Int,
): String = if (shareCardDoshaOverrideTextResId(isRecommended, adjustedScore, totalScore) != null) {
    "overridden_due_to_dosha"
} else {
    ""
}
