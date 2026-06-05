package com.destinyai.astrology.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavySurface

/**
 * iOS parity (SharedThemeComponents.swift:442-469 PremiumMenuRow).
 *
 * Themed Menu wrapper: a row with gold icon + label header and a dropdown that
 * reveals options anchored beneath the field, mirroring the iOS Menu wrapper.
 */
@Composable
fun PremiumMenuRow(
    label: String,
    icon: ImageVector,
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val displayValue = options.getOrNull(selectedIndex).orEmpty()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Gold,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                color = CreamDim,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.size(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val y = size.height
                    drawLine(
                        color = Gold.copy(alpha = 0.4f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                }
                .padding(bottom = 6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = displayValue,
                    color = CreamText,
                    fontSize = 16.sp,
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(NavySurface),
            ) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option,
                                color = if (index == selectedIndex) Gold else CreamText,
                                fontWeight = if (index == selectedIndex) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                        trailingIcon = {
                            if (index == selectedIndex) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Gold,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        },
                        onClick = {
                            onSelected(index)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
