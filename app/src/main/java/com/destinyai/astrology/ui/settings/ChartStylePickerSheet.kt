package com.destinyai.astrology.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold

private data class ChartStyleOption(
    val key: String,
    val label: String,
    val subtitle: String,
)

private val chartStyleOptions = listOf(
    ChartStyleOption(
        key = "north",
        label = "North Indian",
        subtitle = "Diamond layout. Houses are fixed; planets move.",
    ),
    ChartStyleOption(
        key = "south",
        label = "South Indian",
        subtitle = "Square layout. Signs are fixed; planets move.",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartStylePickerSheet(
    currentStyle: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Chart Style",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CanelaFontFamily,
                color = Gold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )

            HorizontalDivider(color = Gold.copy(alpha = 0.1f), thickness = 0.5.dp)

            chartStyleOptions.forEach { option ->
                val isSelected = currentStyle == option.key
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(option.key)
                            onDismiss()
                        }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = option.label,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) Gold else CreamText,
                        )
                        Text(
                            text = option.subtitle,
                            fontSize = 13.sp,
                            color = CreamDim,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    if (isSelected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (option.key != chartStyleOptions.last().key) {
                    HorizontalDivider(
                        color = Gold.copy(alpha = 0.08f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
        }
    }
}
