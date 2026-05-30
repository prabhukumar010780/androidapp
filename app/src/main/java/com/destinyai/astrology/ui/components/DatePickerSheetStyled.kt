package com.destinyai.astrology.ui.components

import android.app.DatePickerDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.destinyai.astrology.ui.theme.Gold
import com.destinyai.astrology.ui.theme.NavyDeep
import java.util.Calendar

/**
 * STUB — Wrapper around [DatePickerDialog] with gold/cosmic theme applied.
 *
 * TODO (R3): Replace with Compose Material3 DatePicker sheet once the
 *  Material3 date picker API stabilises in the project's compose-bom version.
 *
 * @param initialYear   Year shown when the dialog opens.
 * @param initialMonth  Month (0-based) shown when the dialog opens.
 * @param initialDay    Day shown when the dialog opens.
 * @param onDateSelected  Called with (year, month0based, day) when confirmed.
 * @param onDismiss  Called when the dialog is dismissed without a selection.
 */
@Composable
fun DatePickerSheetStyled(
    initialYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    initialMonth: Int = Calendar.getInstance().get(Calendar.MONTH),
    initialDay: Int = Calendar.getInstance().get(Calendar.DAY_OF_MONTH),
    onDateSelected: (year: Int, month: Int, day: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    val dialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, day -> onDateSelected(year, month, day) },
            initialYear,
            initialMonth,
            initialDay,
        ).apply {
            setOnDismissListener { onDismiss() }
            // Theme accent colours via window decorations — best-effort without
            // a full custom theme XML; a proper theme is tracked in TODO above.
            window?.decorView?.setBackgroundColor(
                android.graphics.Color.argb(255, 21, 26, 41), // NavySurface
            )
        }
    }

    LaunchedEffect(Unit) {
        dialog.show()
    }
}
