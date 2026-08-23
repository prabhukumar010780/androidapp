package com.destinyai.astrology.ui.compatibility

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Render [content] off-screen to a bitmap.
 *
 * A detached ComposeView has no LifecycleOwner, so `measure/layout/draw`
 * immediately produces an empty PNG. Attach to the activity window (invisible),
 * wait one frame, then draw — matching iOS ImageRenderer of ShareCardView.
 */
internal suspend fun captureComposableAsBitmap(
    context: Context,
    widthPx: Int,
    heightPx: Int,
    content: @Composable () -> Unit,
): Bitmap {
    val activity = context.findActivity()
        ?: error("Share capture requires an Activity context")
    return withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val composeView = ComposeView(activity).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                (activity as? LifecycleOwner)?.let { setViewTreeLifecycleOwner(it) }
                (activity as? SavedStateRegistryOwner)?.let { setViewTreeSavedStateRegistryOwner(it) }
                (activity as? ViewModelStoreOwner)?.let { setViewTreeViewModelStoreOwner(it) }
                setContent(content)
            }
            val parent = activity.window.decorView as ViewGroup
            composeView.layoutParams = FrameLayout.LayoutParams(widthPx, heightPx)
            composeView.visibility = View.INVISIBLE
            parent.addView(composeView)

            fun cleanup() {
                (composeView.parent as? ViewGroup)?.removeView(composeView)
            }

            cont.invokeOnCancellation { cleanup() }

            composeView.post {
                try {
                    composeView.measure(
                        View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
                    )
                    composeView.layout(0, 0, widthPx, heightPx)
                    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                    composeView.draw(Canvas(bitmap))
                    cont.resume(bitmap)
                } catch (t: Throwable) {
                    if (cont.isActive) cont.resumeWithException(t)
                } finally {
                    cleanup()
                }
            }
        }
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
