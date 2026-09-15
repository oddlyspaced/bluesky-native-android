package io.nativestarter.showcase.bluesky.designsystem

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
 * ALF light theme tokens, copied from @bsky.app/alf (DEFAULT_PALETTE, tokens.js) as used by
 * bluesky-social-app src/alf/themes.ts and tokens.ts. Values are exact; do not round.
 */
object Palette {
    val white = Color(0xFFFFFFFF)
    val black = Color(0xFF000000)
    val pink = Color(0xFFEC4899)
    val like = pink

    val contrast_0 = Color(0xFFFFFFFF)
    val contrast_25 = Color(0xFFF9FAFB)
    val contrast_50 = Color(0xFFEFF2F6)
    val contrast_100 = Color(0xFFDCE2EA)
    val contrast_200 = Color(0xFFC0CAD8)
    val contrast_300 = Color(0xFFA5B2C5)
    val contrast_400 = Color(0xFF8798B0)
    val contrast_500 = Color(0xFF667B99)
    val contrast_600 = Color(0xFF526580)
    val contrast_700 = Color(0xFF405168)
    val contrast_800 = Color(0xFF313F54)
    val contrast_900 = Color(0xFF232E3E)
    val contrast_950 = Color(0xFF19222E)
    val contrast_975 = Color(0xFF111822)
    val contrast_1000 = Color(0xFF000000)

    val primary_25 = Color(0xFFF5F9FF)
    val primary_50 = Color(0xFFE5F0FF)
    val primary_100 = Color(0xFFCCE1FF)
    val primary_500 = Color(0xFF006AFF)
    val primary_600 = Color(0xFF0059D6)

    val positive_600 = Color(0xFF04904A)
    val negative_500 = Color(0xFFE91646)

    /** Fallback avatar disc colour (the RN fallback avatar uses #0070FF). */
    val defaultAvatar = Color(0xFF0070FF)
    /** Brand blue for the placeholder header mark. A colour only; no Bluesky mark is drawn. */
    val brandBlue = Color(0xFF1185FE)
}

/** Semantic atoms from alf themes.js (light). */
object Theme {
    val text = Palette.contrast_1000
    val textContrastLow = Palette.contrast_400
    val textContrastMedium = Palette.contrast_700
    val textContrastHigh = Palette.contrast_900
    val bg = Palette.contrast_0
    val bgContrast25 = Palette.contrast_25
    val bgContrast50 = Palette.contrast_50
    val borderContrastLow = Palette.contrast_100
    val borderContrastMedium = Palette.contrast_200
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
 * Builds a text style the way ALF does on Android (src/alf/typography.tsx `normalizeTextStyles`):
 * - fontSize token; a lineHeight multiplier becomes `Math.round(fontSize * lineHeight)`;
 *   without a multiplier no lineHeight is set on native (natural font metrics).
 * - Inter at the requested weight, `fontVariant: no-contextual` (fonts.ts), letterSpacing TRACKING = 0.
 * - Line box without an explicit lineHeight: [RN_NATURAL_LINE_HEIGHT_EM]. It is the one place that
 *   compensates for how React Native Android lays out Inter; screens never add tuned offsets (review A19).
 */
fun alfText(
    size: androidx.compose.ui.unit.TextUnit = FontSize.sm,
    weight: FontWeight = FontWeight.Normal,
    color: Color = Theme.text,
    leading: Float? = null,
): TextStyle = TextStyle(
    fontFamily = Inter,
    fontSize = size,
    fontWeight = weight,
    color = color,
    letterSpacing = 0.em,
    lineHeight = if (leading != null) kotlin.math.round(size.value * leading).sp else (size.value * RN_NATURAL_LINE_HEIGHT_EM).sp,
    fontFeatureSettings = "calt 0",
    // Compose trims the line height on the first and last line by default (Trim.Both), so a one-line text would
    // get the font's natural height instead of its line height. RN Android makes the line height the line box.
    lineHeightStyle = if (leading == null) RnNaturalLineHeightStyle else RnExplicitLineHeightStyle,
)

/** Marks a style that uses the RN natural line box. [Txt] adds [RN_TEXT_BLOCK_PADDING] around such text. */
val RnNaturalLineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
    alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
    trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.None,
)

/**
 * Style for an explicit line height (ALF `leading_*`, or a numeric lineHeight): the line box equals the line height,
 * with no RN text-block padding. It must differ from [RnNaturalLineHeightStyle] so [Txt] can tell them apart.
 */
val RnExplicitLineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
    alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Proportional,
    trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.None,
)

/**
 * RN Android `Text` keeps `includeFontPadding = true`: the font padding is added once per text block, above the
 * first line and below the last line, while lines inside the block keep the metric pitch
 * ([RN_NATURAL_LINE_HEIGHT_EM]). Measured in the RN Android baseline: each feed post row, which has one natural
 * single-line text, is 2 px (0.667 dp at density 3) taller than the same row built from the line pitch alone.
 */
val RN_TEXT_BLOCK_PADDING = 0.667.dp

/** The RN font padding for a natural-line-height text block, split above and below. */
fun Modifier.rnTextBlockPadding(): Modifier = this.padding(vertical = RN_TEXT_BLOCK_PADDING / 2)

/**
 * Line height of React Native Android `Text` with Inter when no lineHeight is set, as a multiple of the font size.
 * Measured in the RN Android baseline (emulator-5554, density 3): the two lines of the 15 sp Discover notice are
 * 55 px apart, so one line box is 18.333 dp, and 18.333 / 15 = 1.2222. RN sets no lineHeight on native
 * (src/alf/typography.tsx `normalizeTextStyles`), so this applies to ALF and legacy text alike.
 * Rejected alternatives, measured: `includeFontPadding = true` on every text (rows drifted +1.17 dp each) and
 * 1.255 derived from post-row spacing (row pitch matched, but two-line text was 2 px too tall).
 */
const val RN_NATURAL_LINE_HEIGHT_EM = 1.2222f
