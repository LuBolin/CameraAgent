package com.bolin.photohelper.ui

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.bolin.photohelper.R

// ── Brand palette ──────────────────────────────────────────────────

val Mango = Color(0xFFFFB347)
val MangoMuted = Color(0xFFE5A03F)
/**
 * Mango only reads on a dark ground: on charcoal it clears 4.5:1, on cream it lands
 * near 1.6:1. This is the version light mode uses wherever the accent becomes text.
 */
val MangoDeep = Color(0xFF8A5200)
val Charcoal = Color(0xFF36454F)
val CharcoalLight = Color(0xFF4A5B66)
val SoftCream = Color(0xFFFFF5EB)
val SoftCreamDim = Color(0xFFF5E8D8)
val Coral = Color(0xFFE8887A)
val CoralMuted = Color(0xFFD4796D)
val Sage = Color(0xFF8FBF9C)
val SageMuted = Color(0xFF7AAB87)

val ErrorRed = Color(0xFFD4746A)
val ErrorRedDark = Color(0xFFE8897F)
val WarningAmber = Color(0xFFE5A03F)
val SuccessGreen = Color(0xFF7AAB87)

// ── Jarvis gradient (Coral → Mango → Sage) ─────────────────────────

val JarvisGradient = Brush.horizontalGradient(
    colors = listOf(Coral, Mango, Sage),
)

/**
 * The Jarvis gradient sampled at one point. [confidence] runs 0f (uncertain, coral)
 * through 0.5f (working, mango) to 1f (decided, sage). Continuous rather than stepped:
 * callers animate the float and read the colour back every frame.
 */
fun jarvisColor(confidence: Float): Color {
    val t = confidence.coerceIn(0f, 1f)
    return if (t <= 0.5f) lerp(Coral, Mango, t * 2f) else lerp(Mango, Sage, (t - 0.5f) * 2f)
}

// ── Overlay tokens ─────────────────────────────────────────────────

/**
 * Colours for chrome floating over the viewfinder. The viewfinder is always dark, so
 * these stay charcoal-based in both themes and text on them is always cream.
 */
@Immutable
data class OverlayColors(
    val scrimLight: Color = Charcoal.copy(alpha = 0.35f),
    val scrim: Color = Charcoal.copy(alpha = 0.55f),
    val scrimHeavy: Color = Charcoal.copy(alpha = 0.75f),
    val scrimOpaque: Color = Charcoal.copy(alpha = 0.92f),
    /** Frosted glass fill for secondary chrome buttons (Flip, Help, Menu). */
    val frostedGlass: Color = Charcoal.copy(alpha = 0.75f),
    /**
     * Fill for primary chrome buttons (Talk, Improve). Heavier than [frostedGlass]
     * because a primary button tints its icon with [accentOnOverlay], and Mango needs
     * a darker ground to clear 3:1 against a white wall - on the 0.75 fill it drops to
     * 2.7:1, on this one it holds 3.9:1. The extra opacity doubles as hierarchy: the
     * two controls that drive the app read as more present than the ones that don't.
     */
    val frostedGlassStrong: Color = Charcoal.copy(alpha = 0.88f),
    /** Hairline that gives frosted glass its edge. */
    val frostedGlassBorder: Color = SoftCream.copy(alpha = 0.25f),
    /** Ring that marks a primary overlay action apart from neutral chrome. */
    val accentOverlayBorder: Color = Mango.copy(alpha = 0.55f),
    /** Mirror bar and decision cards sit one step heavier than the buttons. */
    val mirrorBar: Color = Charcoal.copy(alpha = 0.80f),
    val mirrorBarBorder: Color = SoftCream.copy(alpha = 0.30f),
    /** Text and icons drawn on any of the above. */
    val onOverlay: Color = SoftCream,
    /**
     * Accent for primary overlay icons. Only legal over [frostedGlassStrong]: Mango is
     * a light colour (luminance 0.54) and vanishes on a bare bright viewfinder, so it
     * always needs a known dark ground underneath it.
     */
    val accentOnOverlay: Color = Mango,
    /**
     * Disabled overlay content. Material's default disabled alpha (0.38) is tuned for
     * a known surface and disappears over a camera scene; this holds ~2.6:1 on the
     * fill, which reads as present-but-inactive rather than absent.
     */
    val onOverlayDisabled: Color = SoftCream.copy(alpha = 0.5f),
)

val LocalOverlayColors = staticCompositionLocalOf { OverlayColors() }

// ── Reduced motion ─────────────────────────────────────────────────

/**
 * True when animations are switched off system-wide (developer options, or the
 * accessibility "remove animations" setting). Every animation checks this and falls
 * back to an instant state change.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
private fun systemReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        runCatching {
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

// ── Theme mode ─────────────────────────────────────────────────────

enum class ThemeMode { SYSTEM, LIGHT, DARK }

// ── Quicksand font (bundled) ───────────────────────────────────────
//
// Bundled rather than fetched through the Play Services downloadable-font
// provider. The provider authenticates callers against a certificate array; the
// previous setup passed `certificates = emptyList()`, so every request was
// rejected and Compose fell back to the system face without reporting anything.
// The brand face is not something to leave to a runtime network call.

val QuicksandFontFamily = FontFamily(
    Font(R.font.quicksand_light, FontWeight.Light),
    Font(R.font.quicksand_regular, FontWeight.Normal),
    Font(R.font.quicksand_medium, FontWeight.Medium),
    Font(R.font.quicksand_semibold, FontWeight.SemiBold),
    Font(R.font.quicksand_bold, FontWeight.Bold),
)

// ── Typography ─────────────────────────────────────────────────────
//
// 16sp floor for elderly readability. Quicksand stays on display/headline/title
// for brand presence; body and label text uses the system font (Roboto) for
// maximum legibility at smaller sizes. Minimum weight is Medium.

private fun quicksand(size: Int, lineHeight: Int, weight: FontWeight) = TextStyle(
    fontFamily = QuicksandFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
)

private fun body(size: Int, lineHeight: Int, weight: FontWeight) = TextStyle(
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
)

private val PhotoHelperTypography = Typography(
    displayLarge = quicksand(34, 40, FontWeight.Medium),
    displayMedium = quicksand(30, 36, FontWeight.Medium),
    displaySmall = quicksand(28, 34, FontWeight.Medium),
    headlineLarge = quicksand(28, 34, FontWeight.SemiBold),
    headlineMedium = quicksand(24, 30, FontWeight.SemiBold),
    headlineSmall = quicksand(22, 28, FontWeight.SemiBold),
    titleLarge = quicksand(22, 28, FontWeight.SemiBold),
    titleMedium = quicksand(20, 26, FontWeight.SemiBold),
    titleSmall = quicksand(18, 24, FontWeight.SemiBold),
    bodyLarge = body(20, 28, FontWeight.Medium),
    bodyMedium = body(18, 25, FontWeight.Medium),
    bodySmall = body(16, 22, FontWeight.Medium),
    labelLarge = body(18, 22, FontWeight.SemiBold),
    labelMedium = body(16, 20, FontWeight.Medium),
    labelSmall = body(16, 20, FontWeight.Medium),
)

// ── Dark color scheme (Charcoal background, Cream AI) ──────────────

private val DarkColors = darkColorScheme(
    primary = Mango,
    onPrimary = Charcoal,
    primaryContainer = MangoMuted,
    onPrimaryContainer = SoftCream,
    secondary = Coral,
    onSecondary = Color.White,
    secondaryContainer = CoralMuted,
    onSecondaryContainer = SoftCream,
    tertiary = Sage,
    onTertiary = Charcoal,
    tertiaryContainer = SageMuted,
    onTertiaryContainer = SoftCream,
    surface = Charcoal,
    onSurface = SoftCream,
    surfaceVariant = CharcoalLight,
    onSurfaceVariant = SoftCreamDim,
    background = Charcoal,
    onBackground = SoftCream,
    error = ErrorRedDark,
    onError = Charcoal,
    outline = SoftCreamDim.copy(alpha = 0.5f),
    outlineVariant = SoftCreamDim.copy(alpha = 0.25f),
)

// ── Light color scheme (Cream background, Charcoal AI) ─────────────

private val LightColors = lightColorScheme(
    primary = MangoDeep,
    onPrimary = Color.White,
    primaryContainer = Mango.copy(alpha = 0.2f),
    onPrimaryContainer = Charcoal,
    secondary = CoralMuted,
    onSecondary = Color.White,
    secondaryContainer = Coral.copy(alpha = 0.15f),
    onSecondaryContainer = Charcoal,
    tertiary = SageMuted,
    onTertiary = Color.White,
    tertiaryContainer = Sage.copy(alpha = 0.15f),
    onTertiaryContainer = Charcoal,
    surface = SoftCream,
    onSurface = Charcoal,
    surfaceVariant = SoftCreamDim,
    onSurfaceVariant = CharcoalLight,
    background = SoftCream,
    onBackground = Charcoal,
    error = ErrorRed,
    onError = Color.White,
    outline = Charcoal.copy(alpha = 0.3f),
    outlineVariant = Charcoal.copy(alpha = 0.12f),
)

// ── Theme entry point ──────────────────────────────────────────────

@Composable
fun PhotoHelperTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(
        LocalOverlayColors provides OverlayColors(),
        LocalReducedMotion provides systemReducedMotion(),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = PhotoHelperTypography,
            content = content,
        )
    }
}
