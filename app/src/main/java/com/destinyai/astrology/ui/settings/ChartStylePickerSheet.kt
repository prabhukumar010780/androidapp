package com.destinyai.astrology.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.R
import com.destinyai.astrology.services.HapticManager
import com.destinyai.astrology.ui.theme.CanelaFontFamily
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.TextTertiary

private data class ChartStyleOption(
    val key: String,
    val labelRes: Int,
    val subtitleRes: Int,
)

private val chartStyleOptions = listOf(
    ChartStyleOption(
        key = "north",
        labelRes = R.string.north_indian,
        subtitleRes = R.string.chart_style_north_subtitle,
    ),
    ChartStyleOption(
        key = "south",
        labelRes = R.string.south_indian,
        subtitleRes = R.string.chart_style_south_subtitle,
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartStylePickerSheet(
    currentStyle: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = remember { HapticManager(context) }

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
            // iOS parity (ChartStylePickerSheet.swift:83): explicit Done button to dismiss.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.chart_style_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CanelaFontFamily,
                    color = Gold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        haptic.light()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("chart_style_sheet_done"),
                ) {
                    Text(
                        text = stringResource(R.string.done_action),
                        color = Gold,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            HorizontalDivider(color = Gold.copy(alpha = 0.1f), thickness = 0.5.dp)

            // iOS parity (ChartStylePickerSheet.swift:67): section header above the picker list.
            Text(
                text = stringResource(R.string.select_chart_style),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Gold,
                modifier = Modifier
                    .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
            )

            chartStyleOptions.forEach { option ->
                val isSelected = currentStyle == option.key
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptic.light()
                            onSelect(option.key)
                        }
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .testTag("chart_style_${option.key}"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(option.labelRes),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) Gold else CreamText,
                        )
                        Text(
                            text = stringResource(option.subtitleRes),
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

            // iOS parity (ChartStylePickerSheet.swift:71): footer caption below the chart style list.
            Text(
                text = stringResource(R.string.chart_style_note),
                fontSize = 13.sp,
                color = TextTertiary,
                modifier = Modifier
                    .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp),
            )
        }
    }
}
