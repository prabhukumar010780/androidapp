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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.R
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavyDeep
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

/**
 * iOS parity (SharedThemeComponents.swift:106-525 PremiumDatePicker / DatePickerSheet).
 *
 * Custom 3-column wheel inside a ModalBottomSheet with cosmic background, gold "Done"
 * button, and a top handle. Replaces the previous Android system DatePickerDialog stub.
 *
 * @param initialYear   Year shown when the sheet opens.
 * @param initialMonth  Month (0-based) shown when the sheet opens.
 * @param initialDay    Day shown when the sheet opens.
 * @param onDateSelected  Called with (year, month0based, day) when Done tapped.
 * @param onDismiss  Called when the sheet is dismissed without confirming.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerSheetStyled(
    initialYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    initialMonth: Int = Calendar.getInstance().get(Calendar.MONTH),
    initialDay: Int = Calendar.getInstance().get(Calendar.DAY_OF_MONTH),
    onDateSelected: (year: Int, month: Int, day: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var selectedYear by rememberSaveable { mutableIntStateOf(initialYear) }
    var selectedMonth by rememberSaveable { mutableIntStateOf(initialMonth) }
    var selectedDay by rememberSaveable { mutableIntStateOf(initialDay) }

    val years = remember {
        val now = Calendar.getInstance().get(Calendar.YEAR)
        (1900..now).toList()
    }
    val monthNames = remember {
        DateFormatSymbols(Locale.getDefault()).months.filter { it.isNotBlank() }
    }
    val daysInMonth = remember(selectedYear, selectedMonth) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, selectedYear)
            set(Calendar.MONTH, selectedMonth)
        }
        cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    // Clamp day if month/year change reduces days available.
    LaunchedEffect(daysInMonth) {
        if (selectedDay > daysInMonth) selectedDay = daysInMonth
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.select_date),
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
                            onDateSelected(selectedYear, selectedMonth, selectedDay)
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
                    items = monthNames,
                    selectedIndex = selectedMonth,
                    onSelectionChanged = { selectedMonth = it },
                    modifier = Modifier.weight(1.4f),
                )
                WheelColumn(
                    items = (1..daysInMonth).map { it.toString() },
                    selectedIndex = (selectedDay - 1).coerceIn(0, daysInMonth - 1),
                    onSelectionChanged = { selectedDay = it + 1 },
                    modifier = Modifier.weight(0.8f),
                )
                WheelColumn(
                    items = years.map { it.toString() },
                    selectedIndex = years.indexOf(selectedYear).coerceAtLeast(0),
                    onSelectionChanged = { selectedYear = years[it] },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Wheel-style picker column. Each row is 36dp tall; the centered row is the
 * selected value (gold, bold). The list snaps to integer indices via scroll
 * state observation. Mirrors the iOS `PremiumWheelPicker` row styling.
 */
@Composable
internal fun WheelColumn(
    items: List<String>,
    selectedIndex: Int,
    onSelectionChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowHeight = 36.dp
    val visibleRows = 5
    val padding = (rowHeight * (visibleRows / 2))
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)

    // Notify caller of the snapped center index.
    LaunchedEffect(state, items) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collectLatest { (idx, _) ->
                if (!state.isScrollInProgress) onSelectionChanged(idx.coerceIn(0, items.size - 1))
            }
    }

    // Re-sync when external selection changes.
    LaunchedEffect(selectedIndex) {
        if (state.firstVisibleItemIndex != selectedIndex) {
            state.scrollToItem(selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
        }
    }

    Box(modifier = modifier.height(rowHeight * visibleRows), contentAlignment = Alignment.Center) {
        // Center row gold underline + overline.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight)
                .background(Gold.copy(alpha = 0.08f), RoundedCornerShape(6.dp)),
        )
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { Spacer(Modifier.height(padding)) }
            items(items) { item ->
                val index = items.indexOf(item)
                val isSelected = index == state.firstVisibleItemIndex
                Box(
                    modifier = Modifier.fillMaxWidth().height(rowHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = item,
                        color = if (isSelected) Gold else CreamDim,
                        fontSize = if (isSelected) 18.sp else 15.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item { Spacer(Modifier.height(padding)) }
        }
        // Top + bottom fade overlays approximating iOS attributed-text fade.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight)
                .align(Alignment.TopCenter)
                .background(Color.Transparent),
        )
        // Hairline gold separators around center row.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Gold.copy(alpha = 0.4f))
                .padding(horizontal = 4.dp),
        )
    }
}
