package com.destinyai.astrology.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.R
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavyDeep
import com.destinyai.astrology.ui.theme.Radius
import com.destinyai.astrology.ui.theme.Spacing
import com.destinyai.astrology.ui.theme.TouchMin
import java.text.DateFormatSymbols
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerSheetStyled(
    initialHour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
    initialMinute: Int = Calendar.getInstance().get(Calendar.MINUTE),
    is24Hour: Boolean = true,
    onTimeSelected: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden },
    )

    // In 12-hour mode the hour wheel holds a 1..12 display value and a separate
    // AM/PM wheel carries the period. `initialHour` is always 24-hour (0..23);
    // onTimeSelected also returns 24-hour, so the 12h↔24h conversion is confined
    // to this sheet and the call site never changes.
    val amPmLabels = remember { DateFormatSymbols.getInstance().amPmStrings.toList() }

    var selectedHour by rememberSaveable {
        mutableIntStateOf(if (is24Hour) initialHour else ((initialHour + 11) % 12) + 1)
    }
    var selectedMinute by rememberSaveable { mutableIntStateOf(initialMinute) }
    // 0 = AM, 1 = PM (unused in 24-hour mode).
    var selectedPeriod by rememberSaveable { mutableIntStateOf(if (initialHour >= 12) 1 else 0) }

    val hourRange = if (is24Hour) (0..23).toList() else (1..12).toList()
    val minuteRange = (0..59).toList()
    val hourLabels = hourRange.map { it.toString().padStart(2, '0') }
    val minuteLabels = minuteRange.map { it.toString().padStart(2, '0') }

    // Resolve the selected wheel values back to a 24-hour hour.
    fun resolvedHour24(): Int =
        if (is24Hour) {
            selectedHour
        } else {
            val h = selectedHour % 12 // 12 -> 0
            if (selectedPeriod == 1) h + 12 else h
        }

    // Prevent wheel scroll from leaking up to the ModalBottomSheet and dismissing it.
    val blockSheetScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset = available

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                available
        }
    }

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
            BackHandler { onDismiss() }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.select_time),
                    color = CreamText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.done),
                    color = Gold,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.button))
                        .clickable {
                            onTimeSelected(resolvedHour24(), selectedMinute)
                        }
                        .heightIn(min = TouchMin)
                        .padding(horizontal = Spacing.md),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(308.dp)
                    .nestedScroll(blockSheetScroll),
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
                if (!is24Hour) {
                    WheelColumn(
                        items = amPmLabels,
                        selectedIndex = selectedPeriod.coerceIn(0, amPmLabels.lastIndex),
                        onSelectionChanged = { selectedPeriod = it },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
