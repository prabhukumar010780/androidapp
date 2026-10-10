package com.destinyai.astrology.ui.compatibility

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
import kotlinx.coroutines.withTimeoutOrNull

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
 * Max time to wait for ONE off-screen capture attempt before giving up and
 * retrying. Deliberately generous: a cold 1080x1080 Compose warmup plus the
 * gold-logo drawable decode can take a few seconds on a low-end device, and a
 * slow-but-successful render must never be mistaken for a hang.
 */
private const val CAPTURE_TIMEOUT_MS = 10_000L

/** How many times to attempt the capture before surfacing failure to the caller. */
private const val CAPTURE_MAX_ATTEMPTS = 2

/**
 * Render [content] off-screen to a bitmap.
 *
 * A detached ComposeView has no LifecycleOwner, so immediate measure/layout/draw
 * produces an empty PNG. Attach to the activity window as a fully laid-out
 * (but transparent) child, wait until pre-draw, then draw into the bitmap.
 *
 * Hardened so a capture failure is NEVER silent — the share flow must not degrade
 * to a text-only send (see CompatibilityResultScreen/FullReportScreen, which abort
 * with a visible error when this returns null via their `runCatching`):
 *  - each attempt is bounded by [CAPTURE_TIMEOUT_MS], so a window that never
 *    schedules a frame cannot hang the share forever;
 *  - a drawn bitmap that is fully transparent (nothing was painted) is rejected
 *    instead of being exported as a blank PNG;
 *  - up to [CAPTURE_MAX_ATTEMPTS] attempts, then the last error propagates.
 *
 * The whole retry loop runs on the main dispatcher, so each attempt's cleanup
 * (removeView) and its cancellation handler are main-thread confined — a
 * timed-out attempt is fully torn down before the next attempt attaches a view.
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
        var lastError: Throwable? = null
        repeat(CAPTURE_MAX_ATTEMPTS) {
            val bitmap = withTimeoutOrNull(CAPTURE_TIMEOUT_MS) {
                runCatching { captureOnce(activity, widthPx, heightPx, content) }
                    .onFailure { lastError = it }
                    .getOrNull()
            }
            if (bitmap != null) return@withContext bitmap
        }
        throw lastError
            ?: IllegalStateException("Share capture produced no bitmap after $CAPTURE_MAX_ATTEMPTS attempts")
    }
}

/** A single capture attempt. Must be invoked on the main dispatcher. */
private suspend fun captureOnce(
    activity: Activity,
    widthPx: Int,
    heightPx: Int,
    content: @Composable () -> Unit,
): Bitmap = suspendCancellableCoroutine { cont ->
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
                    when {
                        !cont.isActive -> bitmap.recycle()
                        isBitmapBlank(bitmap) -> {
                            bitmap.recycle()
                            cont.resumeWithException(
                                IllegalStateException("Share capture produced a blank bitmap"),
                            )
                        }
                        else -> cont.resume(bitmap)
                    }
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

/**
 * A correctly rendered share card is never fully transparent — ShareCardView fills
 * the whole surface with an opaque navy gradient. If every sampled interior pixel
 * (including the centre, which is always painted) has zero alpha, the draw produced
 * nothing and the capture must be treated as a failure rather than exported as a
 * blank PNG. Samples stay in the interior quartiles to avoid the clipped rounded
 * corners, which are legitimately transparent even on a good render.
 */
private fun isBitmapBlank(bitmap: Bitmap): Boolean {
    val w = bitmap.width
    val h = bitmap.height
    if (w == 0 || h == 0) return true
    val xs = intArrayOf(w / 4, w / 2, (w * 3) / 4)
    val ys = intArrayOf(h / 4, h / 2, (h * 3) / 4)
    for (x in xs) {
        for (y in ys) {
            if (Color.alpha(bitmap.getPixel(x, y)) != 0) return false
        }
    }
    return true
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
