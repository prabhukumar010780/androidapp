package com.destinyai.astrology.ui.components.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

/**
 * Decorative rotating orbital rings for cosmic ambiance.
 * Pixel-equivalent port of iOS `OrbitalRingsView` from
 * `ios_app/Views/Components/SharedThemeComponents.swift`.
 *
 * Three concentric circles rotating at different rates:
 *   - inner (200dp)  rotates at  +1x
 *   - middle (300dp) rotates at  -0.5x
 *   - outer (400dp)  rotates at  +0.3x
 *
 * The [rotation] value (in degrees, 0..360) is provided by the caller,
 * typically a continuously-cycling Compose animation. The rings
 * themselves are stateless so the same instance can be reused on
 * Splash, Auth, and any other premium screen.
 */
@Composable
fun AuthOrbitalRings(
    rotation: Float,
    modifier: Modifier = Modifier,
) {
    val gold = Color(0xFFD4AF37)
    Box(modifier = modifier.size(400.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(400.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val strokeWidth = 1f.dp.toPx()

            // Outer ring (400dp) — slowest, +0.3x
            rotate(degrees = rotation * 0.3f, pivot = center) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.05f),
                    radius = 400f.dp.toPx() / 2f,
                    center = center,
                    style = Stroke(width = strokeWidth),
                )
            }
            // Middle ring (300dp) — counter-rotating at -0.5x
            rotate(degrees = -rotation * 0.5f, pivot = center) {
                drawCircle(
                    color = gold.copy(alpha = 0.10f),
                    radius = 300f.dp.toPx() / 2f,
                    center = center,
                    style = Stroke(width = strokeWidth),
                )
            }
            // Inner ring (200dp) — primary rotation, +1x
            rotate(degrees = rotation, pivot = center) {
                drawCircle(
                    color = gold.copy(alpha = 0.20f),
                    radius = 200f.dp.toPx() / 2f,
                    center = center,
                    style = Stroke(width = strokeWidth),
                )
            }
        }
    }
}
