package io.nativestarter.showcase.bluesky.designsystem.text

import android.content.Context
import android.graphics.Paint
import android.util.TypedValue
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import io.nativestarter.showcase.bluesky.R
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/** Integer font metrics, as `android.graphics.Paint.getFontMetricsInt` reports them (negative above the baseline). */
data class FontMetricsPx(val top: Int, val ascent: Int, val descent: Int, val bottom: Int)

/**
 * Vertical geometry of one React Native Android `Text` node, in device pixels.
 * A text of `n` lines is [height] tall; its first baseline is [firstBaseline] below the top; lines are [pitch] apart.
 */
data class RnLineBox(val pitch: Int, val firstBaseline: Int, val padTop: Int, val padBottom: Int) {
    fun height(lines: Int): Int = if (lines <= 0) 0 else padTop + lines * pitch + padBottom
}

/**
 * The React Native 0.86 Android text measurement rules, ported in one place (review A19: no per-screen offsets).
 * Source: `ReactAndroid/.../views/text/TextAttributes.kt`, `TextLayoutManager.kt` and
 * `internal/span/CustomLineHeightSpan.kt` in react-native 0.86.0.
 *
 * - Font size: `ceil(PixelUtil.toPixelFromSP(fontSize, maxFontSizeMultiplier))` (`effectiveFontSize`).
 * - Line height: `PixelUtil.toPixelFromSP(lineHeight, maxFontSizeMultiplier)`, then `ceil` in `CustomLineHeightSpan`.
 *   The span spreads the leading `L = lineHeight - (descent - ascent)` as `ascent -= ceil(L / 2)` and
 *   `descent += floor(L / 2)`, and sets the first line's top and the last line's bottom to them, so
 *   `includeFontPadding` has no effect.
 * - No line height: `StaticLayout` / `BoringLayout` with `setIncludePad(includeFontPadding)` (default true). Lines
 *   are `descent - ascent` apart; with padding the first line starts at `top` and the last line ends at `bottom`.
 * - The measured height is `layout.getLineBottom(lastLine)`.
 */
object RnTextRules {
    /** `PixelUtil.toPixelFromSP(value, maxFontScale)`. [scaledPx] is `TypedValue.applyDimension(SP, value)`. */
    fun pixelFromSp(value: Float, scaledPx: Float, density: Float, maxFontScale: Float?): Float =
        if (maxFontScale != null && maxFontScale >= 1f) min(scaledPx, value * density * maxFontScale) else scaledPx

    /** `TextAttributes.effectiveFontSize`. */
    fun fontSizePx(pixelFromSp: Float): Int = ceil(pixelFromSp.toDouble()).toInt()

    fun box(fm: FontMetricsPx, lineHeightPx: Float?, includeFontPadding: Boolean): RnLineBox {
        if (lineHeightPx != null && !lineHeightPx.isNaN()) {
            val lineHeight = ceil(lineHeightPx.toDouble()).toInt()
            val leading = lineHeight - ((-fm.ascent) + fm.descent)
            val ascent = fm.ascent - ceil(leading / 2.0f).toInt()
            return RnLineBox(pitch = lineHeight, firstBaseline = -ascent, padTop = 0, padBottom = 0)
        }
        val pitch = fm.descent - fm.ascent
        return if (includeFontPadding) {
            RnLineBox(pitch = pitch, firstBaseline = -fm.top, padTop = fm.ascent - fm.top, padBottom = fm.bottom - fm.descent)
        } else {
            RnLineBox(pitch = pitch, firstBaseline = -fm.ascent, padTop = 0, padBottom = 0)
        }
    }
}

/** `src/alf/fonts.ts` `applyFonts` on Android: each weight maps to one static Inter file. */
fun interFontRes(weight: FontWeight?): Int = when ((weight ?: FontWeight.Normal).weight) {
    in 0..449 -> R.font.inter_regular
    in 450..549 -> R.font.inter_medium
    in 550..649 -> R.font.inter_semibold
    else -> R.font.inter_bold
}

private data class MetricsKey(val fontRes: Int, val sizePx: Int)

private object FontMetricsCache {
    private val cache = HashMap<MetricsKey, FontMetricsPx>()

    @Synchronized
    fun get(context: Context, key: MetricsKey): FontMetricsPx = cache.getOrPut(key) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = ResourcesCompat.getFont(context, key.fontRes)
            textSize = key.sizePx.toFloat()
        }
        val fm = paint.fontMetricsInt
        FontMetricsPx(fm.top, fm.ascent, fm.descent, fm.bottom)
    }
}

/**
 * Text laid out with the RN Android line box. [style] holds the RN inputs:
 * - `fontSize` and `lineHeight` in sp, as the RN style has them (`lineHeight` unspecified means no line height);
 * - `platformStyle.paragraphStyle.includeFontPadding` is RN `includeFontPadding` (true when not set, as in RN).
 *
 * Compose then renders at the RN pixel font size with a line pitch equal to the RN pitch, and a layout modifier moves
 * the text so its first baseline and its height are the RN values. Line breaking stays Compose's.
 */
@Composable
fun RnText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    maxFontScale: Float? = null,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val fontScale = LocalConfiguration.current.fontScale
    val includeFontPadding = style.platformStyle?.paragraphStyle?.includeFontPadding ?: true
    val geometry = remember(style.fontSize, style.lineHeight, style.fontWeight, includeFontPadding, maxFontScale, density, fontScale) {
        val metrics = context.resources.displayMetrics
        fun spToPx(value: Float) = RnTextRules.pixelFromSp(
            value,
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, metrics),
            density,
            maxFontScale,
        )
        val sizePx = RnTextRules.fontSizePx(spToPx(style.fontSize.value))
        val lineHeightPx = if (style.lineHeight.isSpecified) spToPx(style.lineHeight.value) else null
        val fm = FontMetricsCache.get(context, MetricsKey(interFontRes(style.fontWeight), sizePx))
        val box = RnTextRules.box(fm, lineHeightPx, includeFontPadding)
        // Rendered under a density with font scale 1, so sp here are exact pixels / density. The pitch is
        // lowered by 0.02 px so the span's ceil() gives the integer pitch despite float rounding.
        val composeStyle = style.copy(
            fontSize = (sizePx / density).sp,
            lineHeight = ((box.pitch - 0.02f) / density).sp,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Proportional, LineHeightStyle.Trim.None),
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        )
        composeStyle to box
    }
    val (composeStyle, box) = geometry
    CompositionLocalProvider(LocalDensity provides Density(density, 1f)) {
        BasicText(
            text = text,
            style = composeStyle,
            modifier = modifier.rnLineBox(box),
            maxLines = maxLines,
            overflow = overflow,
            onTextLayout = onTextLayout,
        )
    }
}

/** Sets the height and first baseline of a text to the RN line box. Must come before pointer or link handling. */
private fun Modifier.rnLineBox(box: RnLineBox): Modifier = this.then(RnLineBoxModifier(box))

/**
 * Also answers intrinsic heights with the RN box: feed rows use `IntrinsicSize.Min`, and a plain layout modifier would
 * report Compose's own height there. Compose applies no line height to a `maxLines = 1` text, so the line count comes
 * from the baselines, not from the Compose height.
 */
private data class RnLineBoxModifier(val box: RnLineBox) : androidx.compose.ui.layout.LayoutModifier {
    override fun androidx.compose.ui.layout.MeasureScope.measure(
        measurable: androidx.compose.ui.layout.Measurable,
        constraints: androidx.compose.ui.unit.Constraints,
    ): androidx.compose.ui.layout.MeasureResult {
        val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
        val first = placeable[FirstBaseline]
        val last = placeable[LastBaseline]
        if (placeable.height == 0 || first == androidx.compose.ui.layout.AlignmentLine.Unspecified) {
            return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
        val lines = if (last == androidx.compose.ui.layout.AlignmentLine.Unspecified) 1 else ((last - first).toFloat() / box.pitch).roundToInt() + 1
        val height = box.height(lines).coerceIn(constraints.minHeight, constraints.maxHeight)
        return layout(placeable.width, height, mapOf(FirstBaseline to box.firstBaseline, LastBaseline to box.firstBaseline + (lines - 1) * box.pitch)) {
            placeable.place(0, box.firstBaseline - first)
        }
    }

    private fun rnHeight(composeHeight: Int): Int =
        if (composeHeight == 0) 0 else box.height((composeHeight.toFloat() / box.pitch).roundToInt().coerceAtLeast(1))

    override fun androidx.compose.ui.layout.IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: androidx.compose.ui.layout.IntrinsicMeasurable,
        width: Int,
    ): Int = rnHeight(measurable.minIntrinsicHeight(width))

    override fun androidx.compose.ui.layout.IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: androidx.compose.ui.layout.IntrinsicMeasurable,
        width: Int,
    ): Int = rnHeight(measurable.maxIntrinsicHeight(width))

    override fun androidx.compose.ui.layout.IntrinsicMeasureScope.minIntrinsicWidth(
        measurable: androidx.compose.ui.layout.IntrinsicMeasurable,
        height: Int,
    ): Int = measurable.minIntrinsicWidth(height)

    override fun androidx.compose.ui.layout.IntrinsicMeasureScope.maxIntrinsicWidth(
        measurable: androidx.compose.ui.layout.IntrinsicMeasurable,
        height: Int,
    ): Int = measurable.maxIntrinsicWidth(height)
}
