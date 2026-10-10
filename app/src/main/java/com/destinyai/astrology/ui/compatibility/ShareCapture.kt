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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Density
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
 * The design dimension (in dp) ShareCardView is authored against. The off-screen
 * capture pins LocalDensity so this many dp maps exactly onto the pixel buffer,
 * so the card lays out identically on every device regardless of its real density.
 */
private const val CARD_DESIGN_DP = 1080f

/**
 * Pins [LocalDensity] so [CARD_DESIGN_DP] dp of design surface maps 1:1 onto a
 * [widthPx]-wide pixel buffer. The share card is authored in dp (a 1080dp x 1080dp
 * surface matching the iOS 1080x1080 export); the off-screen capture measures the
 * ComposeView in PX. Without this pin, on a high-density device 1080px == ~360dp of
 * layout space, so the card overflows and its bottom (score fraction, stars, rating,
 * footer) is clipped off the exported PNG. See ShareCardCaptureTest.
 */
@Composable
internal fun CaptureAtDesignDensity(widthPx: Int, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalDensity provides Density(density = widthPx / CARD_DESIGN_DP, fontScale = 1f),
    ) {
        content()
    }
}

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
                setContent { CaptureAtDesignDensity(widthPx) { content() } }
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
