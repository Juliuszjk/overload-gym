package com.apollo.overload.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.apollo.overload.R

/**
 * Tokens from mockup v4 (mockup/build-v4/p1_css_a.css). Monochrome ink UI; the heat ramp h1..h4
 * (amber -> orange -> red -> crimson) is reserved for load, fatigue and volume, h2 also marks "current".
 */
@Immutable
data class Palette(
    val bg: Color, val raise: Color, val sunk: Color, val line: Color, val line2: Color,
    val ink: Color, val ink2: Color, val mute: Color, val onInk: Color,
    val body: Color, val bodyInert: Color, val sil: Color,
    val h1: Color, val h2: Color, val h3: Color, val h4: Color,
    val warn: Color, val warnBg: Color, val good: Color, val goodBg: Color,
    val dark: Boolean,
) {
    /** Heat colour for t in 0..1 (0 = untrained grey body, then amber -> crimson). */
    fun heat(t: Float): Color {
        if (t <= 0f) return body
        val stops = listOf(h1, h2, h3, h4)
        val x = (t.coerceIn(0f, 1f) * (stops.size - 1))
        val i = x.toInt().coerceAtMost(stops.size - 2)
        return lerp(stops[i], stops[i + 1], x - i)
    }
}

val DarkPalette = Palette(
    bg = Color(0xFF141311), raise = Color(0xFF1E1C19), sunk = Color(0xFF0F0E0C), line = Color(0xFF2B2824), line2 = Color(0xFF3D3933),
    ink = Color(0xFFF3EFE7), ink2 = Color(0xFFC2BBAF), mute = Color(0xFF8B8478), onInk = Color(0xFF141311),
    body = Color(0xFF2A2723), bodyInert = Color(0xFF1F1D1A), sil = Color(0xFF1A1816),
    h1 = Color(0xFFF4C64B), h2 = Color(0xFFF08B2D), h3 = Color(0xFFE2492B), h4 = Color(0xFFB11D35),
    warn = Color(0xFFF2B23E), warnBg = Color(0xFF2A2216), good = Color(0xFF86D394), goodBg = Color(0xFF1A2A1D), dark = true,
)

val LightPalette = Palette(
    bg = Color(0xFFF6F5F1), raise = Color(0xFFFFFFFF), sunk = Color(0xFFEBE9E4), line = Color(0xFFE2DFD8), line2 = Color(0xFFCBC7BE),
    ink = Color(0xFF161513), ink2 = Color(0xFF47433D), mute = Color(0xFF7A746A), onInk = Color(0xFFF6F5F1),
    body = Color(0xFFDEDAD2), bodyInert = Color(0xFFE9E6E0), sil = Color(0xFFEFEDE8),
    h1 = Color(0xFFEFB92F), h2 = Color(0xFFEC7A1F), h3 = Color(0xFFD63C22), h4 = Color(0xFF9E1530),
    warn = Color(0xFFA86A00), warnBg = Color(0xFFF7ECD6), good = Color(0xFF2E8B4A), goodBg = Color(0xFFE2F0E4), dark = false,
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

/** Archivo is a variable font (wght 100..900, wdth 62..125). CSS font-stretch % maps 1:1 to wdth. */
@OptIn(ExperimentalTextApi::class)
fun archivo(weight: Int, width: Float = 100f): FontFamily = FontFamily(
    Font(
        R.font.archivo,
        weight = FontWeight(weight.coerceAtMost(900)),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.coerceAtMost(900)), FontVariation.width(width)),
    )
)

val Figtree = FontFamily(
    Font(R.font.figtree, FontWeight.Normal),
    Font(R.font.figtree, FontWeight.Medium),
    Font(R.font.figtree, FontWeight.SemiBold),
    Font(R.font.figtree, FontWeight.Bold),
)

/** Text styles matching the mockup classes. Colours are left unspecified; set them at the call site. */
object Type {
    fun display(size: TextUnit, weight: Int = 800, width: Float = 100f, tracking: Float = 0f, lineHeight: TextUnit = TextUnit.Unspecified) =
        TextStyle(fontFamily = archivo(weight, width), fontWeight = FontWeight(weight.coerceAtMost(900)), fontSize = size,
            letterSpacing = tracking.em, lineHeight = lineHeight, fontFeatureSettings = "tnum")

    val eyebrow = TextStyle(fontFamily = archivo(600, 92f), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.12.em)
    val title = display(30.sp, 800, 112f, -0.02f, 30.sp)
    val exerciseName = display(36.sp, 800, 100f, -0.025f, 34.sp)
    val bigNumber = display(68.sp, 850, 80f, -0.03f, 62.sp)
    val brand = display(19.sp, 850, 125f, -0.02f)
    val body = TextStyle(fontFamily = Figtree, fontSize = 15.sp, lineHeight = 21.sp)
    val small = TextStyle(fontFamily = Figtree, fontSize = 13.sp, lineHeight = 18.sp)
    val label = display(13.sp, 700, 95f)
}

@Composable
fun OverloadTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) darkColorScheme(
        primary = p.ink, onPrimary = p.onInk, background = p.bg, onBackground = p.ink, surface = p.bg, onSurface = p.ink,
        surfaceVariant = p.raise, onSurfaceVariant = p.ink2, outline = p.line2, outlineVariant = p.line,
        surfaceContainer = p.raise, surfaceContainerHigh = p.raise, surfaceContainerLow = p.sunk, secondary = p.h2,
    ) else lightColorScheme(
        primary = p.ink, onPrimary = p.onInk, background = p.bg, onBackground = p.ink, surface = p.bg, onSurface = p.ink,
        surfaceVariant = p.raise, onSurfaceVariant = p.ink2, outline = p.line2, outlineVariant = p.line,
        surfaceContainer = p.raise, surfaceContainerHigh = p.raise, surfaceContainerLow = p.sunk, secondary = p.h2,
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography.let {
            it.copy(bodyLarge = Type.body, bodyMedium = Type.small, labelLarge = Type.label)
        }, content = content)
    }
}
