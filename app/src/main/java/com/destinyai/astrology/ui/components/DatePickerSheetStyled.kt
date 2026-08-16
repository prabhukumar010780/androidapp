package com.destinyai.astrology.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.R
import com.destinyai.astrology.ui.theme.CreamDim
import com.destinyai.astrology.ui.theme.CreamText
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavyDeep
import com.destinyai.astrology.ui.theme.Radius
import com.destinyai.astrology.ui.theme.Spacing
import com.destinyai.astrology.ui.theme.TouchMin
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerSheetStyled(
    initialYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    initialMonth: Int = Calendar.getInstance().get(Calendar.MONTH),
    initialDay: Int = Calendar.getInstance().get(Calendar.DAY_OF_MONTH),
    onDateSelected: (year: Int, month: Int, day: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden },
    )

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

    LaunchedEffect(daysInMonth) {
        if (selectedDay > daysInMonth) selectedDay = daysInMonth
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
                    text = stringResource(R.string.select_date),
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
                            onDateSelected(selectedYear, selectedMonth, selectedDay)
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
            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * Wheel-style picker column. Uses contentPadding (not spacer items) so
 * firstVisibleItemIndex always maps 1-to-1 to data indices. The selected
 * value is derived from layoutInfo — whichever item is closest to the
 * viewport centre — which is snapped + reported when scrolling stops.
 */
@Composable
internal fun WheelColumn(
    items: List<String>,
    selectedIndex: Int,
    onSelectionChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowHeight = 44.dp
    val visibleRows = 7
    val halfRows = visibleRows / 2

    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // The index of the item visually centred in the viewport right now.
    val centeredIndex by remember {
        derivedStateOf {
            val info = state.layoutInfo
            if (info.visibleItemsInfo.isEmpty()) return@derivedStateOf selectedIndex
            val vpCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            info.visibleItemsInfo
                .minByOrNull { abs(it.offset + it.size / 2f - vpCenter) }
                ?.index
                ?.coerceIn(0, items.lastIndex)
                ?: selectedIndex
        }
    }

    // Snap to nearest item centre and report selection when scroll settles.
    // drop(1) ignores the initial "settled" emission on open so we don't clobber
    // the incoming initial selection before it has been scrolled into place.
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }
            .filter { !it }
            .drop(1)
            .collect {
                val idx = centeredIndex
                onSelectionChanged(idx)
                val info = state.layoutInfo
                val vpCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                val item = info.visibleItemsInfo.firstOrNull { it.index == idx }
                if (item != null && abs(item.offset + item.size / 2f - vpCenter) > 1f) {
                    state.animateScrollToItem(idx)
                }
            }
    }

    // Sync when an external change drives selectedIndex.
    LaunchedEffect(selectedIndex) {
        if (!state.isScrollInProgress && centeredIndex != selectedIndex) {
            state.scrollToItem(selectedIndex)
        }
    }

    Box(modifier = modifier.height(rowHeight * visibleRows), contentAlignment = Alignment.Center) {
        // Gold tint on the centre row — no hairline dividers.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight)
                .background(Gold.copy(alpha = 0.10f)),
        )
        LazyColumn(
            state = state,
            contentPadding = PaddingValues(vertical = rowHeight * halfRows),
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            flingBehavior = ScrollableDefaults.flingBehavior(),
        ) {
            itemsIndexed(items) { index, item ->
                val isSelected = centeredIndex == index
                Box(
                    modifier = Modifier.fillMaxWidth().height(rowHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = item,
                        color = if (isSelected) Gold else CreamDim,
                        fontSize = if (isSelected) 18.sp else 15.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
