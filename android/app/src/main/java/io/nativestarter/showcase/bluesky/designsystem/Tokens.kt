package io.nativestarter.showcase.bluesky.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.nativestarter.showcase.bluesky.R

/**
 * Palette steps of the current ALF theme (light, dark or dim), read from [LocalAlf]. Kept for existing call sites;
 * new code reads semantic atoms from `LocalAlf.current` instead.
 */
object Palette {
    val white = Color(0xFFFFFFFF)
    val black = Color(0xFF000000)
    val pink = Color(0xFFEC4899)
    val like = pink

    val contrast_0: Color @Composable get() = LocalAlf.current.palette.contrast(0)
    val contrast_25: Color @Composable get() = LocalAlf.current.palette.contrast(25)
    val contrast_50: Color @Composable get() = LocalAlf.current.palette.contrast(50)
    val contrast_100: Color @Composable get() = LocalAlf.current.palette.contrast(100)
    val contrast_200: Color @Composable get() = LocalAlf.current.palette.contrast(200)
    val contrast_300: Color @Composable get() = LocalAlf.current.palette.contrast(300)
    val contrast_400: Color @Composable get() = LocalAlf.current.palette.contrast(400)
    val contrast_500: Color @Composable get() = LocalAlf.current.palette.contrast(500)
    val contrast_600: Color @Composable get() = LocalAlf.current.palette.contrast(600)
    val contrast_700: Color @Composable get() = LocalAlf.current.palette.contrast(700)
    val contrast_800: Color @Composable get() = LocalAlf.current.palette.contrast(800)
    val contrast_900: Color @Composable get() = LocalAlf.current.palette.contrast(900)
    val contrast_950: Color @Composable get() = LocalAlf.current.palette.contrast(950)
    val contrast_975: Color @Composable get() = LocalAlf.current.palette.contrast(975)
    val contrast_1000: Color @Composable get() = LocalAlf.current.palette.contrast(1000)

    val primary_25: Color @Composable get() = LocalAlf.current.palette.primary(25)
    val primary_50: Color @Composable get() = LocalAlf.current.palette.primary(50)
    val primary_100: Color @Composable get() = LocalAlf.current.palette.primary(100)
    val primary_500: Color @Composable get() = LocalAlf.current.palette.primary(500)
    val primary_600: Color @Composable get() = LocalAlf.current.palette.primary(600)

    val positive_500: Color @Composable get() = LocalAlf.current.palette.positive(500)
    val positive_600: Color @Composable get() = LocalAlf.current.palette.positive(600)
    val negative_500: Color @Composable get() = LocalAlf.current.palette.negative(500)

    /** Fallback avatar disc colour (the RN fallback avatar uses #0070FF). Theme independent. */
    val defaultAvatar = Color(0xFF0070FF)
    /** Brand blue for the placeholder header mark. A colour only; no Bluesky mark is drawn. */
    val brandBlue = Color(0xFF1185FE)
}

/** Semantic atoms of the current ALF theme ([AlfTheme]). */
object Theme {
    val text: Color @Composable get() = LocalAlf.current.text
    val textContrastLow: Color @Composable get() = LocalAlf.current.textContrastLow
    val textContrastMedium: Color @Composable get() = LocalAlf.current.textContrastMedium
    val textContrastHigh: Color @Composable get() = LocalAlf.current.textContrastHigh
    val textLink: Color @Composable get() = LocalAlf.current.textLink
    val bg: Color @Composable get() = LocalAlf.current.bg
    val bgContrast25: Color @Composable get() = LocalAlf.current.bgContrast25
    val bgContrast50: Color @Composable get() = LocalAlf.current.bgContrast50
    val borderContrastLow: Color @Composable get() = LocalAlf.current.borderContrastLow
    val borderContrastMedium: Color @Composable get() = LocalAlf.current.borderContrastMedium
}

object Space {
    val _2xs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val _2xl = 24.dp
    val _3xl = 28.dp
    val _4xl = 32.dp
    val _5xl = 40.dp
}

object Radius {
    val _2xs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val full = 999.dp
}

/** alf tokens.fontSize. RN on Android maps each weight to a static Inter file (src/alf/fonts.ts). */
object FontSize {
    val _2xs = 9.4.sp
    val xs = 11.3.sp
    val sm = 13.1.sp
    val md = 15.sp
    val lg = 16.9.sp
    val xl = 18.8.sp
    val _2xl = 20.6.sp
    val _3xl = 24.3.sp
    val _4xl = 30.sp
    val _5xl = 37.5.sp
}

object LineHeight {
    const val tight = 1.15f
    const val snug = 1.3f
    const val relaxed = 1.5f
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_bold, FontWeight.ExtraBold),
    Font(R.font.inter_bold, FontWeight.Black),
)

/**
 * Style of an ALF `Text` (src/components/Typography.tsx), which always applies `a.leading_snug` before the caller's
 * style, so every ALF text has a line height: `normalizeTextStyles` turns the multiplier into
 * `Math.round(fontSize * lineHeight)` dp. Inter at the requested weight, `fontVariant: no-contextual`, TRACKING 0.
 *
 * The style carries the RN inputs only: `lineHeight` is the RN lineHeight in sp, and `includeFontPadding` is the RN
 * prop. [io.nativestarter.showcase.bluesky.designsystem.text.RnText] turns them into the RN Android line box, in one
 * place (review A19).
 */
fun alfText(
    size: androidx.compose.ui.unit.TextUnit = FontSize.sm,
    weight: FontWeight = FontWeight.Normal,
    /** Unspecified resolves to the theme `text` atom in [Txt]. */
    color: Color = Color.Unspecified,
    leading: Float = LineHeight.snug,
    includeFontPadding: Boolean = true,
): TextStyle = rnTextStyle(size, weight, color, kotlin.math.round(size.value * leading).sp, includeFontPadding)

/**
 * Style of a legacy `Text` (src/view/com/util/text/Text.tsx, `type="md"`, `type="xl-medium"` and so on) or a plain RN
 * `Text` without a lineHeight: the line box comes from the font metrics, with `includeFontPadding` (RN default true).
 */
fun legacyText(
    size: androidx.compose.ui.unit.TextUnit,
    weight: FontWeight = FontWeight.Normal,
    color: Color = Color.Unspecified,
    includeFontPadding: Boolean = true,
): TextStyle = rnTextStyle(size, weight, color, androidx.compose.ui.unit.TextUnit.Unspecified, includeFontPadding)

private fun rnTextStyle(
    size: androidx.compose.ui.unit.TextUnit,
    weight: FontWeight,
    color: Color,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    includeFontPadding: Boolean,
): TextStyle = TextStyle(
    fontFamily = Inter,
    fontSize = size,
    fontWeight = weight,
    color = color,
    letterSpacing = 0.em,
    lineHeight = lineHeight,
    fontFeatureSettings = "calt 0",
    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = includeFontPadding),
)
