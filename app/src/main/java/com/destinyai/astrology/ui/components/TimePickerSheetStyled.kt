package com.destinyai.astrology.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.R
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavyDeep
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * iOS parity (SharedThemeComponents.swift PremiumDatePicker time variant).
 *
 * 2-column wheel inside a ModalBottomSheet (hour + minute) with cosmic background
 * and gold "Done" button. Replaces the previous AlertDialog stub.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerSheetStyled(
    initialHour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
    initialMinute: Int = Calendar.getInstance().get(Calendar.MINUTE),
    is24Hour: Boolean = true,
    onTimeSelected: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var selectedHour by rememberSaveable { mutableIntStateOf(initialHour) }
    var selectedMinute by rememberSaveable { mutableIntStateOf(initialMinute) }

    val hourRange = if (is24Hour) (0..23).toList() else (1..12).toList()
    val minuteRange = (0..59).toList()
    val hourLabels = hourRange.map { it.toString().padStart(2, '0') }
    val minuteLabels = minuteRange.map { it.toString().padStart(2, '0') }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NavyDeep,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Gold.copy(alpha = 0.4f)),
            )
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.select_time),
                    color = CreamText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.done),
                    color = Gold,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        scope.launch {
                            val outHour = if (is24Hour) selectedHour else selectedHour
                            onTimeSelected(outHour, selectedMinute)
                            sheetState.hide()
                        }
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().height(220.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WheelColumn(
                    items = hourLabels,
                    selectedIndex = hourRange.indexOf(selectedHour).coerceAtLeast(0),
                    onSelectionChanged = { selectedHour = hourRange[it] },
                    modifier = Modifier.weight(1f),
                )
                WheelColumn(
                    items = minuteLabels,
                    selectedIndex = minuteRange.indexOf(selectedMinute).coerceAtLeast(0),
                    onSelectionChanged = { selectedMinute = minuteRange[it] },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
