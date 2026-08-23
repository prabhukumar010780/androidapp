package com.destinyai.astrology.ui.compatibility

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
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
 * A detached ComposeView has no LifecycleOwner, so immediate measure/layout/draw
 * produces an empty PNG. Attach to the activity window as a fully laid-out
 * (but transparent) child, wait until pre-draw, then draw into the bitmap.
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
                // INVISIBLE skips drawing; keep VISIBLE with alpha 0 so Compose still paints.
                alpha = 0f
                visibility = View.VISIBLE
                setContent(content)
            }
            val parent = activity.window.decorView as ViewGroup
            composeView.layoutParams = FrameLayout.LayoutParams(widthPx, heightPx)
            parent.addView(composeView)

            fun cleanup() {
                (composeView.parent as? ViewGroup)?.removeView(composeView)
            }

            fun finishWithBitmap() {
                try {
                    composeView.measure(
                        View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
                    )
                    composeView.layout(0, 0, widthPx, heightPx)
                    composeView.post {
                        try {
                            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                            composeView.draw(Canvas(bitmap))
                            if (cont.isActive) cont.resume(bitmap)
                        } catch (t: Throwable) {
                            if (cont.isActive) cont.resumeWithException(t)
                        } finally {
                            cleanup()
                        }
                    }
                } catch (t: Throwable) {
                    cleanup()
                    if (cont.isActive) cont.resumeWithException(t)
                }
            }

            cont.invokeOnCancellation { cleanup() }

            val observer = composeView.viewTreeObserver
            val listener = object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (observer.isAlive) observer.removeOnPreDrawListener(this)
                    composeView.post { finishWithBitmap() }
                    return true
                }
            }
            observer.addOnPreDrawListener(listener)
            composeView.invalidate()
        }
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
