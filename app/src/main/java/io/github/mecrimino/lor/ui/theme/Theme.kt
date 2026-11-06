package io.github.mecrimino.lor.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import io.github.mecrimino.lor.R
import io.github.mecrimino.lor.data.ThemeMode

/** Design tokens from the mockup, verbatim. */
@Immutable
data class LorColors(
    val bg: Color, val surface: Color, val ink: Color, val muted: Color, val line: Color, val inv: Color,
    val tintAlpha: Float, val isDark: Boolean,
) {
    fun tint(c: Color) = c.copy(alpha = tintAlpha)
}

val Accent = Color(0xFFFF4F1A)
val PriorityHigh = Accent
val PriorityMedium = Color(0xFFE8A317)
val PriorityLow = Color(0xFF5B6472)

private val LightLor = LorColors(
    bg = Color(0xFFF7F7F4), surface = Color.White, ink = Color(0xFF131313), muted = Color(0xFF66665F),
    line = Color(0xFFE5E5DF), inv = Color.White, tintAlpha = 0x1A / 255f, isDark = false,
)
private val DarkLor = LorColors(
    bg = Color(0xFF0E0E0F), surface = Color(0xFF18181A), ink = Color(0xFFF2F2EF), muted = Color(0xFF9A9A96),
    line = Color(0xFF2A2A2E), inv = Color(0xFF0E0E0F), tintAlpha = 0x33 / 255f, isDark = true,
)

val LocalLor = staticCompositionLocalOf { LightLor }

private fun weighted(res: Int, vararg weights: Int) = FontFamily(
    weights.map { Font(res, FontWeight(it), variationSettings = FontVariation.Settings(FontVariation.weight(it))) }
)

val Inter = weighted(R.font.inter, 400, 500, 600)
val SpaceGrotesk = weighted(R.font.space_grotesk, 500, 600, 700)

object LorType {
    private fun display(size: Int, weight: Int, tracking: Float) = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight(weight), fontSize = size.sp, letterSpacing = tracking.em,
    )
    val wordmark = display(26, 700, -0.04f)
    val h1 = display(40, 600, -0.035f).copy(lineHeight = 40.sp)
    val month = display(30, 600, -0.035f)
    val h2 = display(18, 600, -0.02f)
    val ringValue = display(17, 600, -0.02f)
    fun body(size: Int, weight: Int = 400) = TextStyle(fontFamily = Inter, fontWeight = FontWeight(weight), fontSize = size.sp)
    val label = TextStyle(fontFamily = Inter, fontWeight = FontWeight(600), fontSize = 11.sp, letterSpacing = 0.08.em)
}

@Composable
fun LorTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val c = if (dark) DarkLor else LightLor
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = c.ink, onPrimary = c.inv, secondary = c.ink, onSecondary = c.inv, tertiary = Accent,
        background = c.bg, onBackground = c.ink, surface = c.surface, onSurface = c.ink,
        surfaceVariant = c.surface, onSurfaceVariant = c.muted, outline = c.line, outlineVariant = c.line,
        surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.surface,
        inverseSurface = c.ink, inverseOnSurface = c.inv, inversePrimary = Accent,
    )
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(LocalLor provides c) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(
                bodyLarge = LorType.body(15), bodyMedium = LorType.body(14), bodySmall = LorType.body(12),
                titleLarge = LorType.h2, labelLarge = LorType.body(14, 500),
            ),
            content = content,
        )
    }
}
