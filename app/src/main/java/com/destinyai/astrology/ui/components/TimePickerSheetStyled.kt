package com.destinyai.astrology.ui.components

import android.app.TimePickerDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar

/**
 * STUB — Wrapper around [TimePickerDialog] with gold/cosmic theme applied.
 *
 * TODO (R3): Replace with Compose Material3 TimePicker sheet once the
 *  Material3 time picker API stabilises in the project's compose-bom version.
 *
 * @param initialHour    Hour (0-23) shown when the dialog opens.
 * @param initialMinute  Minute (0-59) shown when the dialog opens.
 * @param is24Hour  Whether to show a 24-hour clock (default true).
 * @param onTimeSelected  Called with (hour, minute) when confirmed.
 * @param onDismiss  Called when the dialog is dismissed without a selection.
 */
@Composable
fun TimePickerSheetStyled(
    initialHour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
    initialMinute: Int = Calendar.getInstance().get(Calendar.MINUTE),
    is24Hour: Boolean = true,
    onTimeSelected: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    val dialog = remember {
        TimePickerDialog(
            context,
            { _, hour, minute -> onTimeSelected(hour, minute) },
            initialHour,
            initialMinute,
            is24Hour,
        ).apply {
            setOnDismissListener { onDismiss() }
            window?.decorView?.setBackgroundColor(
                android.graphics.Color.argb(255, 21, 26, 41), // NavySurface
            )
        }
    }

    LaunchedEffect(Unit) {
        dialog.show()
    }
}
