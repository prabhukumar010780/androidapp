package com.destinyai.astrology.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinyai.astrology.R
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import java.time.format.TextStyle as JavaTextStyle

val Gold = Color(0xFFD4AF37)
val GoldDim = Color(0xFF8A7638)
val GoldSoft = Color(0xFFF2D06B)
val GoldChampagne = Color(0xFFFFF8E1)
val GoldLight = Color(0xFFF2D06B)        // Issue 13: iOS goldLight (F2D06B)
val GoldDeep = Color(0xFF8B7226)         // Issue 13: iOS goldDeep
val TextOnGold = Color(0xFF1A1410)       // Issue 14: dark hue used over gold backgrounds
val PurpleAccent = Color(0xFF4A148C)     // Issue 15: decorative purple
val NavyDeep = Color(0xFF0B0F19)      // iOS mainBackground
val DarkNavyContrast = Color(0xFF0D0D1A) // iOS AppTheme.Colors.darkNavyContrast (FAB icon fill)
val NavySurface = Color(0xFF151A29)   // iOS cardBackground
val NavyVariant = Color(0xFF1C2235)   // iOS secondaryBackground
val NavyInput = Color(0xFF121620)     // iOS inputBackground
val CreamText = Color(0xFFFFFFFF)     // iOS textPrimary (white)
val CreamDim = Color(0xFFA0AEC0)      // iOS textSecondary (cool gray)
val TextTertiary = Color(0xFF718096)  // iOS textTertiary

// iOS Assets.xcassets color set parity (sRGB → hex)
val GoldAccent = Color(0xFFD4A84B)        // iOS GoldAccent (R=0.831 G=0.659 B=0.294)
val NavyPrimary = Color(0xFF263248)       // iOS NavyPrimary (R=0.149 G=0.196 B=0.282)
val BackgroundLight = Color(0xFFF5F5F5)   // iOS BackgroundLight (sRGB 0.961 cream/off-white)
val TextDark = Color(0xFF333333)          // iOS TextDark (sRGB 0.20)
val AccentColor = Gold                    // iOS AccentColor (empty universal — uses app gold tint)

// Status tokens (Issue 16)
val SuccessGreen = Color(0xFF34C759)
val WarningOrange = Color(0xFFFF9500)
val InfoBlue = Color(0xFF007AFF)

// Tab bar tokens (Issue 17)
val TabBarBackground = Color(0xFF0A0E1A)
val TabInactive = Color(0xFF6B7280)
val Separator = Color(0x33FFFFFF)
val SeparatorIos = Color(0x802D3748)     // Issue 16: iOS separator (2D3748 at 50%)

val GoldGradient = Brush.linearGradient(
    colors = listOf(Color(0xFFD4AF37), Color(0xFFF5D060), Color(0xFFD4AF37)),
    start = Offset(0f, 0f),
    end = Offset(300f, 0f),
)

val CanelaFontFamily = FontFamily(
    Font(R.font.canela_bold, FontWeight.Bold),
    Font(R.font.canela_roman, FontWeight.Normal),
    Font(R.font.canela_light, FontWeight.Light),
)

// Issue 18: graceful serif fallback mirroring iOS .system(serif).
// Compose has no API to chain a FontFamily after a custom one, so callers
// can use CanelaFontFamilyWithFallback when the Canela asset may be missing.
val CanelaFontFamilyWithFallback: FontFamily = runCatching { CanelaFontFamily }
    .getOrDefault(FontFamily.Serif)

val PlayfairFontFamily = FontFamily(
    Font(R.font.playfair_display_regular, FontWeight.Normal),
)

/**
 * Auth screen dimensions — mirrors iOS `AppTheme.Auth` exactly.
 * All values match `ios_app/Design/AppTheme.swift` so the Android
 * paywall renders pixel-equivalent to iOS.
 */
object AuthDimens {
    // Logo & header
    val logoSize = 70.dp
    val logoOpticalOffsetX = 6.dp   // optical correction for 'D' shape
    val logoOpticalOffsetY = 0.dp
    val glowSize = 140.dp
    val glowBlur = 20.dp
    val ringSize = 110.dp
    val dotSize = 5.dp
    val titleSize = 28.sp
    val subtitleSize = 15.sp

    // Spacing
    val logoToTextSpacing = 20.dp
    val textPadding = 44.dp
    val contentTopPadding = 28.dp

    // Animations (durations in ms)
    const val entranceDurationMs = 700
    const val entranceDelayMs = 250
    const val logoSpringStiffness = 350f      // matches iOS spring(response:0.7, dampingFraction:0.65)
    const val logoSpringDampingRatio = 0.65f
    const val orbitRotationDurationMs = 30_000  // 30s full rotation
    const val bioRhythmBpm = 60                // logo pulse: 60 beats per minute

    // Buttons
    val buttonHeight = 54.dp
    val buttonCornerRadius = 14.dp
    val iconSize = 20.dp
}

/**
 * Birth-data screen dimensions — mirrors iOS `AppTheme.BirthData` exactly.
 * Issue 22: extended with formSpacing/inputHeight/cornerRadius/timeSection/soundToggle.
 */
object BirthDataDimens {
    val headerIconSize = 60.dp
    val headerGlowSize = 80.dp
    val headerGlowBlur = 25.dp
    val headerTitleSize = 24.sp
    val headerSubtitleSize = 14.sp

    // Form layout
    val formSpacing = 20.dp
    val labelSpacing = 8.dp
    val inputHeight = 54.dp
    val cornerRadius = 12.dp
    val inputCornerRadius = 12.dp
    val inputFontSize = 16.sp
    val labelFontSize = 13.sp
    val iconFontSize = 14.sp

    // Time section
    object TimeSection {
        val rowSpacing = 12.dp
        val pickerHeight = 200.dp
        val sheetDetentHeight = 350.dp
    }

    // Sound toggle
    object SoundToggle {
        val height = 44.dp
        val cornerRadius = 22.dp
        val iconSize = 18.dp
        val labelSize = 14.sp
    }
}

private val DestinyTypography = Typography(
    displayLarge = TextStyle(fontFamily = CanelaFontFamily, fontWeight = FontWeight.Bold, fontSize = 57.sp),
    displayMedium = TextStyle(fontFamily = CanelaFontFamily, fontWeight = FontWeight.Bold, fontSize = 45.sp),
    displaySmall = TextStyle(fontFamily = CanelaFontFamily, fontWeight = FontWeight.Bold, fontSize = 36.sp),
    headlineLarge = TextStyle(fontFamily = CanelaFontFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp),
    headlineMedium = TextStyle(fontFamily = CanelaFontFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp),
    headlineSmall = TextStyle(fontFamily = CanelaFontFamily, fontWeight = FontWeight.Normal, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = CanelaFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = CanelaFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = CanelaFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp),
    labelSmall = TextStyle(fontSize = 11.sp),
)

private val DarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = NavyDeep,
    primaryContainer = NavyVariant,
    onPrimaryContainer = Gold,
    secondary = GoldDim,
    background = NavyDeep,
    onBackground = CreamText,
    surface = NavySurface,
    onSurface = CreamText,
    surfaceVariant = NavyVariant,
    onSurfaceVariant = CreamDim,
    error = Color(0xFFFF5252),
    onError = Color.White,
)

@Composable
fun DestinyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = DestinyTypography,
        content = content,
    )
}

@Composable
fun CosmicBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F1422),
                        NavyDeep,
                        NavyDeep,
                    ),
                ),
            ),
    ) {
        content()
    }
}

// ---------------------------------------------------------------------------
// AppTheme — central namespace mirroring iOS `AppTheme`
// ---------------------------------------------------------------------------

object AppTheme {

    object colors {
        val gold = Gold
        val goldSoft = GoldSoft
        val goldDim = GoldDim
        val goldChampagne = GoldChampagne
        val goldLight = GoldLight
        val goldDeep = GoldDeep                  // Issue 13
        val textOnGold = TextOnGold              // Issue 14
        val purpleAccent = PurpleAccent          // Issue 15
        val mainBackground = NavyDeep
        val cardBackground = NavySurface
        val secondaryBackground = NavyVariant
        val inputBackground = NavyInput
        val darkNavyContrast = DarkNavyContrast
        val textPrimary = CreamText
        val textSecondary = CreamDim
        val textTertiary = TextTertiary
        val separator = Separator
        val separatorIos = SeparatorIos          // Issue 16

        // Issue 16
        val success = SuccessGreen
        val warning = WarningOrange
        val info = InfoBlue

        // Issue 17
        val tabBarBackground = TabBarBackground
        val tabInactive = TabInactive
    }

    object gradients {
        val gold = GoldGradient

        // Issue 18: 5-stop premium card gradient
        val premiumCardGradient = Brush.linearGradient(
            colors = listOf(
                Color(0xFF1C2235),
                Color(0xFF151A29),
                Color(0xFF121620),
                Color(0xFF151A29),
                Color(0xFF1C2235),
            ),
        )

        // Issue 18: radial tab glow
        fun tabGlowGradient(
            center: Offset = Offset.Zero,
            radius: Float = 200f,
        ): Brush = Brush.radialGradient(
            colors = listOf(
                Gold.copy(alpha = 0.35f),
                Gold.copy(alpha = 0.10f),
                Color.Transparent,
            ),
            center = center,
            radius = radius,
        )
    }

    /**
     * Issue 19: shared dimensional + stroke tokens.
     */
    object styles {
        val cornerRadius = 12.dp
        val inputHeight = 54.dp
        val cardCornerRadius = 16.dp

        data class Shadow(val elevation: Dp, val color: Color)
        data class BorderStroke(val width: Dp, val stroke: Color)

        val cardShadow = Shadow(elevation = 8.dp, color = Color(0x33000000))
        val goldBorder = BorderStroke(width = 1.dp, stroke = Gold.copy(alpha = 0.5f))
        val inputBorder = BorderStroke(width = 1.dp, stroke = Gold.copy(alpha = 0.30f))
    }

    /**
     * Issue 17: semantic typography helpers mirroring iOS AppTheme.Fonts.
     * These re-use CanelaFontFamily so they cooperate with the global serif fallback.
     */
    object typography {
        val soul = TextStyle(
            fontFamily = CanelaFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
        )
        val premiumDisplay = TextStyle(
            fontFamily = CanelaFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
        )
        val premiumTitle = TextStyle(
            fontFamily = CanelaFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
        )
        val premiumBody = TextStyle(
            fontFamily = CanelaFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
        )
        val caption = TextStyle(
            fontFamily = CanelaFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
        )
    }

    /**
     * Issue 19: animation timing constants for Splash / Onboarding / LanguageSelection
     * surfaced at AppTheme level (existing per-screen blocks below remain unchanged).
     */
    object Animation {
        // Splash entrance
        const val splashEntranceDurationMs = 700
        const val splashEntranceDelayMs = 250
        const val splashLogoSpringStiffness = 350f
        const val splashLogoSpringDampingRatio = 0.65f

        // Orbit
        const val orbitRotationDurationMs = 30_000

        // Bio-rhythm pulse (60 BPM)
        const val bioRhythmBpm = 60

        // Onboarding springs / float
        const val onboardingTiltSpringStiffness = 220f
        const val onboardingTiltSpringDamping = 0.7f
        const val onboardingFloatDurationMs = 4_000
        const val onboardingShimmerDurationMs = 2_400

        // Language selection
        const val languageGlassFadeMs = 600
        const val languageRowEntranceMs = 350
    }

    /**
     * Issue 20: Splash sub-theme.
     */
    object splash {
        val logoSize = 96.dp
        val ringInnerSize = 130.dp
        val ringMiddleSize = 180.dp
        val ringOuterSize = 230.dp
        val loaderDotSize = 6.dp
        val titleSize = 32.sp
        val subtitleSize = 14.sp

        const val logoAnimationDurationMs = 800
        const val logoSpringDampingRatio = 0.6f
        const val ringRotationDurationMs = 24_000
        const val shimmerDurationMs = 2_500
        const val splashHoldDurationMs = 1_400
    }

    /**
     * Issue 21: language selection screen tokens.
     */
    object LanguageSelection {
        val cardHeight = 72.dp
        val cardCornerRadius = 16.dp
        const val glassOpacity = 0.10f
        const val particleCount = 24
        val iconSize = 32.dp
        val rowSpacing = 12.dp
        val sectionSpacing = 24.dp
        val horizontalPadding = 20.dp
    }

    /**
     * Issue 23: onboarding tokens.
     */
    object Onboarding {
        // Centralised star count — mirrors iOS `AppTheme.Onboarding.starCount`.
        const val starCount = 30

        object Nebula {
            val size = 320.dp
            val blur = 40.dp
            const val opacity = 0.45f
            const val rotationDurationMs = 32_000
        }

        object Tilt {
            const val maxDegrees = 8f
            const val springStiffness = 220f
            const val springDamping = 0.7f
        }

        object FloatAnim {
            val amplitude = 12.dp
            const val durationMs = 4_000
        }

        object Shimmer {
            const val durationMs = 2_400
            const val angleDegrees = 20f
        }

        object Parallax {
            const val foregroundFactor = 0.20f
            const val midgroundFactor = 0.10f
            const val backgroundFactor = 0.04f
        }

        object Icon {
            val size = 88.dp
            val glowSize = 140.dp
            val glowBlur = 24.dp
        }

        object Typography {
            val titleSize = 28.sp
            val bodySize = 16.sp
            val captionSize = 13.sp
        }

        object Spacing {
            val sectionGap = 24.dp
            val iconToTitle = 16.dp
            val titleToBody = 12.dp
            val pageHorizontal = 24.dp
        }
    }

    /**
     * Issue 25: Visionary token block.
     */
    object Visionary {
        object BentoGrid {
            val gap = 12.dp
            val cornerRadius = 20.dp
            val tileMinHeight = 120.dp
            val sectionPadding = 16.dp
        }

        object GlassButton {
            val height = 54.dp
            val cornerRadius = 16.dp
            const val backgroundOpacity = 0.18f
            val borderWidth = 1.dp
            val iconSize = 20.dp
        }

        object Typewriter {
            const val charDurationMs = 32
            const val cursorBlinkMs = 500
        }

        object GlassCard {
            val cornerRadius = 20.dp
            const val backgroundOpacity = 0.12f
            val borderWidth = 1.dp
            val padding = 16.dp
            val blurRadius = 18.dp
        }
    }
}

/**
 * Issue 24: cosmic radial gradients.
 */
object CosmicGradients {
    fun nebulaGold(
        center: Offset = Offset.Zero,
        radius: Float = 600f,
    ): Brush = Brush.radialGradient(
        colors = listOf(
            Gold.copy(alpha = 0.45f),
            GoldSoft.copy(alpha = 0.20f),
            Color.Transparent,
        ),
        center = center,
        radius = radius,
    )

    fun nebulaPurple(
        center: Offset = Offset.Zero,
        radius: Float = 600f,
    ): Brush = Brush.radialGradient(
        colors = listOf(
            Color(0xFF6B5BFF).copy(alpha = 0.35f),
            Color(0xFF3D2A8C).copy(alpha = 0.20f),
            Color.Transparent,
        ),
        center = center,
        radius = radius,
    )

    fun iconGlow(
        center: Offset = Offset.Zero,
        radius: Float = 140f,
    ): Brush = Brush.radialGradient(
        colors = listOf(
            Gold.copy(alpha = 0.55f),
            Gold.copy(alpha = 0.18f),
            Color.Transparent,
        ),
        center = center,
        radius = radius,
    )
}

/**
 * Issues 26 + 27: feature flags mirroring iOS `Features`.
 * Wire these into composables to gate visibility identically.
 */
object Features {
    // iOS parity: AppTheme.Features.showSoundToggle = false in iOS AppTheme.swift:55
    const val showSoundToggle: Boolean = false
    const val showAstrologySettings: Boolean = true
    const val multiPartnerComparison: Boolean = true
    const val allowMatchScreenUserEdit: Boolean = true
}

// ---------------------------------------------------------------------------
// Premium UI Components — Issues 1..15
// ---------------------------------------------------------------------------

/**
 * Issue 1: 3 concentric counter-rotating gold rings (Splash + Auth ambiance).
 */
@Composable
fun OrbitalRings(
    modifier: Modifier = Modifier,
    innerSize: Dp = AppTheme.splash.ringInnerSize,
    middleSize: Dp = AppTheme.splash.ringMiddleSize,
    outerSize: Dp = AppTheme.splash.ringOuterSize,
    rotationDurationMs: Int = AppTheme.splash.ringRotationDurationMs,
) {
    val transition = rememberInfiniteTransition(label = "orbital-rings")

    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(rotationDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )

    Box(
        modifier = modifier.size(outerSize),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .size(innerSize)
                .rotate(rotation),
        ) {
            drawCircle(
                color = Gold.copy(alpha = 0.20f),
                radius = size.minDimension / 2f,
                style = Stroke(width = 1f),
            )
        }

        Canvas(
            modifier = Modifier
                .size(middleSize)
                .rotate(-rotation * 0.5f),
        ) {
            drawCircle(
                color = Gold.copy(alpha = 0.10f),
                radius = size.minDimension / 2f,
                style = Stroke(width = 1f),
            )
        }

        Canvas(
            modifier = Modifier
                .size(outerSize)
                .rotate(rotation * 0.3f),
        ) {
            drawCircle(
                color = Color.White.copy(alpha = 0.05f),
                radius = size.minDimension / 2f,
                style = Stroke(width = 1f),
            )
        }
    }
}

/**
 * Issue 2: PremiumInputField — labeled input with gold leading icon, custom placeholder,
 * focus binding, gold border. Mirrors iOS `PremiumInputField`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumInputField(
    label: String,
    icon: @Composable () -> Unit,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(BirthDataDimens.labelSpacing),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BirthDataDimens.labelSpacing),
        ) {
            Box(modifier = Modifier.size(AuthDimens.iconSize)) {
                icon()
            }
            Text(
                text = label,
                color = AppTheme.colors.textSecondary,
                fontSize = BirthDataDimens.labelFontSize,
            )
        }

        val fieldModifier = Modifier
            .fillMaxWidth()
            .height(BirthDataDimens.inputHeight)
            .semantics { contentDescription = "premium_input_field" }
            .let { m -> if (focusRequester != null) m.focusRequester(focusRequester) else m }
            .let { m ->
                if (onFocusChanged != null) {
                    m.onFocusChanged { state -> onFocusChanged(state.isFocused) }
                } else {
                    m
                }
            }

        val focusManager = LocalFocusManager.current

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = placeholder,
                    color = AppTheme.colors.textTertiary,
                    fontSize = BirthDataDimens.inputFontSize,
                )
            },
            singleLine = true,
            // Issue 2: ImeAction.Done with focus clear
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() },
            ),
            textStyle = TextStyle(
                color = AppTheme.colors.textPrimary,
                fontSize = BirthDataDimens.inputFontSize,
            ),
            shape = RoundedCornerShape(BirthDataDimens.inputCornerRadius),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppTheme.styles.inputBorder.stroke,
                unfocusedBorderColor = AppTheme.styles.inputBorder.stroke,
                focusedContainerColor = AppTheme.colors.inputBackground,
                unfocusedContainerColor = AppTheme.colors.inputBackground,
                cursorColor = AppTheme.colors.gold,
            ),
            modifier = fieldModifier,
        )
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.then(
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        ),
    )
}

/**
 * Issue 3: multi-component wheel picker with white centered text.
 * Issue 11: writes the selected row index back into `selections` per component.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PremiumWheelPicker(
    data: List<List<String>>,
    selections: List<Int>,
    onSelectionsChange: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
    rowHeight: Dp = 36.dp,
    visibleRows: Int = 5,
) {
    val totalHeight = rowHeight * visibleRows
    val centerOffset = visibleRows / 2

    // Issue 10: force dark style equivalent (white content on dark surface) for the picker
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Row(
            modifier = modifier
                .height(totalHeight)
                .background(AppTheme.colors.mainBackground),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            data.forEachIndexed { component, items ->
                // Issue 11: re-key the picker on the underlying data list reference so the
                // LazyColumn fully recomposes when the data identity changes.
                key(items) {
                    val listState = rememberLazyListState(
                        initialFirstVisibleItemIndex = selections.getOrElse(component) { 0 },
                    )

                    // Issue 11: feed selected row index back upstream
                    LaunchedEffect(listState) {
                        snapshotFlow { listState.firstVisibleItemIndex }
                            .distinctUntilChanged()
                            .collect { idx ->
                                if (selections.getOrNull(component) != idx) {
                                    val updated = selections.toMutableList().apply {
                                        while (size <= component) add(0)
                                        this[component] = idx
                                    }
                                    onSelectionsChange(updated)
                                }
                            }
                    }

                    // Issue 12: mirror UIPickerView selectRow — sync to externally
                    // changed selection prop by animating to the requested index.
                    val externalSelectedIndex = selections.getOrElse(component) { 0 }
                    LaunchedEffect(externalSelectedIndex) {
                        if (listState.firstVisibleItemIndex != externalSelectedIndex) {
                            runCatching {
                                listState.animateScrollToItem(externalSelectedIndex)
                            }
                        }
                    }

                    LazyColumn(
                        state = listState,
                        flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .semantics { contentDescription = "premium_wheel_picker_component" },
                    ) {
                        items(centerOffset) { Spacer(Modifier.height(rowHeight)) }

                        items(items) { label ->
                            Box(
                                modifier = Modifier
                                    .height(rowHeight)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }

                        items(centerOffset) { Spacer(Modifier.height(rowHeight)) }
                    }
                }
            }
        }
    }
}

/**
 * Issue 4 / 12 / 13 / 14 / 15: composite Date/Time picker.
 *  - Modes: Date (Month/Day/Year), HourAndMinute (Hour/Minute/AM-PM)
 *  - Seeds wheel selections from initial values on first composition (Issue 14)
 *  - Recomputes LocalDate from wheels (Issue 12)
 *  - Recomputes LocalTime with 12h→24h conversion (Issues 13 + 15)
 */
enum class PremiumDatePickerMode {
    Date,
    HourAndMinute,
}

private fun hourTo24(hour12: Int, isPm: Boolean): Int {
    return when {
        !isPm && hour12 == 12 -> 0
        isPm && hour12 != 12 -> hour12 + 12
        else -> hour12
    }
}

@Composable
fun PremiumDatePicker(
    mode: PremiumDatePickerMode,
    initialDate: LocalDate,
    initialTime: LocalTime,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val months = remember {
        (1..12).map { m ->
            java.time.Month.of(m).getDisplayName(JavaTextStyle.FULL, Locale.getDefault())
        }
    }
    val currentYear = remember { LocalDate.now().year }
    val years = remember(currentYear) { ((currentYear - 100)..currentYear).reversed().toList() }
    val days = remember { (1..31).toList() }
    val hours = remember { (1..12).toList() }
    val minutes = remember { (0..59).toList() }
    val periods = remember { listOf("AM", "PM") }

    var dateSelections by remember { mutableStateOf(listOf(0, 0, 0)) }
    var timeSelections by remember { mutableStateOf(listOf(0, 0, 0)) }

    // Issue 14: seed from initial values on first composition
    LaunchedEffect(Unit) {
        val month = initialDate.monthValue - 1
        val day = initialDate.dayOfMonth - 1
        val yearIdx = years.indexOf(initialDate.year).coerceAtLeast(0)
        dateSelections = listOf(month, day, yearIdx)

        val hour24 = initialTime.hour
        val periodIdx = if (hour24 >= 12) 1 else 0
        var hour12 = if (hour24 > 12) hour24 - 12 else hour24
        if (hour12 == 0) hour12 = 12
        val hourIdx = hours.indexOf(hour12).coerceAtLeast(0)
        val minuteIdx = minutes.indexOf(initialTime.minute).coerceAtLeast(0)
        timeSelections = listOf(hourIdx, minuteIdx, periodIdx)
    }

    // Issue 12: rebuild LocalDate
    LaunchedEffect(dateSelections) {
        if (mode != PremiumDatePickerMode.Date) return@LaunchedEffect
        val month = dateSelections[0] + 1
        val day = days.getOrNull(dateSelections[1]) ?: 1
        val year = years.getOrNull(dateSelections[2]) ?: currentYear
        runCatching { LocalDate.of(year, month, day) }
            .getOrNull()
            ?.let(onDateChange)
    }

    // Issue 13 + 15: rebuild LocalTime via 12h→24h
    LaunchedEffect(timeSelections) {
        if (mode != PremiumDatePickerMode.HourAndMinute) return@LaunchedEffect
        val hour12 = hours.getOrNull(timeSelections[0]) ?: 12
        val minute = minutes.getOrNull(timeSelections[1]) ?: 0
        val isPm = timeSelections[2] == 1
        onTimeChange(LocalTime.of(hourTo24(hour12, isPm), minute))
    }

    Box(
        modifier = modifier.height(BirthDataDimens.TimeSection.pickerHeight),
        contentAlignment = Alignment.Center,
    ) {
        when (mode) {
            PremiumDatePickerMode.Date -> PremiumWheelPicker(
                data = listOf(
                    months,
                    days.map { it.toString() },
                    years.map { it.toString() },
                ),
                selections = dateSelections,
                onSelectionsChange = { dateSelections = it },
            )
            PremiumDatePickerMode.HourAndMinute -> PremiumWheelPicker(
                data = listOf(
                    hours.map { it.toString() },
                    minutes.map { String.format(Locale.US, "%02d", it) },
                    periods,
                ),
                selections = timeSelections,
                onSelectionsChange = { timeSelections = it },
            )
        }
    }
}

/**
 * Issue 5 + 9: ModalBottomSheet selection sheet — option tap performs onSelect, light haptic, dismiss.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumSelectionSheet(
    title: String,
    selectedValue: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val haptics = LocalHapticFeedback.current

    // Issue 9: dynamic content height = options.size*60 + 80, capped at "medium" (~480dp).
    val computedHeightDp = (options.size * 60 + 80).coerceAtMost(480).dp

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppTheme.colors.mainBackground,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(
                        color = Color.White.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(2.dp),
                    ),
            )
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(computedHeightDp)            // Issue 9
                .semantics { contentDescription = "premium_selection_sheet" }
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F1422),
                            NavyDeep,
                        ),
                    ),
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textPrimary,
                    fontFamily = CanelaFontFamily,
                )

                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(options) { entry ->
                        val (value, label) = entry
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickableNoRipple {
                                    // Issue 9: set value + light haptic + dismiss
                                    onSelect(value)
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDismiss()
                                }
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = label,
                                fontSize = 18.sp,
                                color = AppTheme.colors.textPrimary,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.weight(1f))
                            if (selectedValue == value) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = AppTheme.colors.gold,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .size(16.dp),
                                )
                            }
                        }

                        if (value != options.last().first) {
                            HorizontalDivider(
                                color = AppTheme.colors.separator,
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Issue 6: PremiumSelectionRow — icon + title + value + chevron-down.
 * Supports placeholder and disabled states with iOS-equivalent 0.6 alpha gating.
 */
@Composable
fun PremiumSelectionRow(
    icon: @Composable () -> Unit,
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDisabled: Boolean = false,
    isPlaceholder: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isDisabled) 0.6f else 1f)         // Issue 6: 0.6 disabled opacity
            .semantics { contentDescription = "premium_selection_row" }
            .clickableNoRipple {
                if (!isDisabled) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.size(14.dp)) { icon() }
            Text(
                text = title,
                color = AppTheme.colors.textSecondary,
                fontSize = 13.sp,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = AppTheme.colors.inputBackground,
                    shape = RoundedCornerShape(12.dp),
                )
                .border(
                    width = AppTheme.styles.inputBorder.width,
                    color = AppTheme.styles.inputBorder.stroke,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value,
                color = if (isDisabled || isPlaceholder) {
                    AppTheme.colors.textTertiary
                } else {
                    AppTheme.colors.textPrimary
                },
                fontSize = 16.sp,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = AppTheme.colors.textTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * Issue 7 + 10: PremiumMenuRow — exposed dropdown with placeholder + light haptic on select.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumMenuRow(
    icon: @Composable () -> Unit,
    title: String,
    selection: String,
    options: List<Pair<String, String>>,
    onSelectionChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Select",
) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.size(14.dp)) { icon() }
            Text(
                text = title,
                color = AppTheme.colors.textSecondary,
                fontSize = 13.sp,
            )
        }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            val displayLabel = options.firstOrNull { it.first == selection }?.second
                ?: placeholder

            OutlinedTextField(
                value = displayLabel,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                textStyle = TextStyle(
                    color = if (selection.isEmpty()) {
                        AppTheme.colors.textTertiary
                    } else {
                        AppTheme.colors.textPrimary
                    },
                    fontSize = 16.sp,
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppTheme.styles.inputBorder.stroke,
                    unfocusedBorderColor = AppTheme.styles.inputBorder.stroke,
                    focusedContainerColor = AppTheme.colors.inputBackground,
                    unfocusedContainerColor = AppTheme.colors.inputBackground,
                ),
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { entry ->
                    val (value, label) = entry
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            // Issue 10: set selection + light haptic
                            onSelectionChange(value)
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/**
 * Issue 8: DatePickerSheet — header capsule, centered title, top-right gold Done,
 * fixed 350dp ModalBottomSheet on cosmic background.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerSheet(
    title: String,
    mode: PremiumDatePickerMode,
    initialDate: LocalDate,
    initialTime: LocalTime,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
    doneLabel: String? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHapticFeedback.current
    // Issue 8: localized Done label fallback via stringResource(R.string.done)
    val resolvedDoneLabel = doneLabel ?: stringResource(R.string.done)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppTheme.colors.mainBackground,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(
                        color = Color.White.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(2.dp),
                    ),
            )
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BirthDataDimens.TimeSection.sheetDetentHeight)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F1422), NavyDeep),
                    ),
                ),
        ) {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary,
                fontFamily = CanelaFontFamily,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp),
            )

            TextButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDismiss()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 20.dp, top = 20.dp)
                    .semantics { contentDescription = "date_picker_done_button" },
            ) {
                Text(
                    text = resolvedDoneLabel,
                    color = AppTheme.colors.gold,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                )
            }

            PremiumDatePicker(
                mode = mode,
                initialDate = initialDate,
                initialTime = initialTime,
                onDateChange = onDateChange,
                onTimeChange = onTimeChange,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 16.dp),
            )
        }
    }
}
