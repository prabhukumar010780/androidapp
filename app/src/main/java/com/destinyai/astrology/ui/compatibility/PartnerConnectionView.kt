package com.destinyai.astrology.ui.compatibility

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold

// Pure helper — unit testable without Compose
internal fun partnerInitial(name: String): String =
    if (name.isEmpty()) "" else name.first().uppercaseChar().toString()

@Composable
fun PartnerConnectionView(
    boyName: String,
    girlName: String,
    modifier: Modifier = Modifier,
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(
        modifier = modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        PartnerAvatarPill(name = boyName, alignRight = !isRtl, modifier = Modifier.weight(1f))

        // Connection symbol
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Gold.copy(alpha = 0.1f))
                    .blur(4.dp),
            )
            // iOS parity: stroked gold ring with scaleEffect(1.2) + opacity(0.5) glow
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .scale(1.2f)
                    .alpha(0.5f)
                    .clip(CircleShape)
                    .border(1.dp, Gold.copy(alpha = 0.3f), CircleShape),
            )
            Icon(
                imageVector = Icons.Default.Link,
                contentDescription = null,
                tint = Gold,
                modifier = Modifier
                    .size(14.dp)
                    .rotate(45f),
            )
        }

        PartnerAvatarPill(name = girlName, alignRight = isRtl, modifier = Modifier.weight(1f))
    }
}

@Composable
fun PartnerAvatarPill(
    name: String,
    alignRight: Boolean,
    modifier: Modifier = Modifier,
) {
    val initial = partnerInitial(name)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (alignRight) Arrangement.End else Arrangement.Start,
    ) {
        if (alignRight) {
            Text(
                text = name,
                fontSize = 15.sp,
                color = CreamDim,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(12.dp))
            AvatarCircle(initial = initial)
        } else {
            AvatarCircle(initial = initial)
            Spacer(Modifier.width(12.dp))
            Text(
                text = name,
                fontSize = 15.sp,
                color = CreamDim,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

@Composable
private fun AvatarCircle(initial: String) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.05f))
            .border(
                1.dp,
                Brush.linearGradient(listOf(Gold.copy(alpha = 0.6f), Gold.copy(alpha = 0.1f))),
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Gold,
        )
    }
}
