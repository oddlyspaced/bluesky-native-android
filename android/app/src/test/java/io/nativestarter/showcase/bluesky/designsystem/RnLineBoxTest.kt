package io.nativestarter.showcase.bluesky.designsystem

import io.nativestarter.showcase.bluesky.designsystem.text.FontMetricsPx
import io.nativestarter.showcase.bluesky.designsystem.text.RnFontScale
import io.nativestarter.showcase.bluesky.designsystem.text.RnTextRules
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.roundToInt

/**
 * RN Android text boxes (react-native 0.86 `CustomLineHeightSpan`, `StaticLayout` with includeFontPadding).
 * Expected heights are the text view bounds in the RN Android app on emulator-5554 (density 3), read with
 * `uiautomator dump` on the seeded home, thread and profile screens. Font metrics come from the Inter files:
 * unitsPerEm 2048, ascender 1984, descender -494; head yMax/yMin 2269/-660 (Regular), 2275/-677 (SemiBold);
 * `Paint.getFontMetricsInt` rounds each value.
 */
class RnLineBoxTest {
    private fun metrics(sizePx: Int, yMax: Int, yMin: Int) = FontMetricsPx(
        top = (-sizePx * yMax / 2048.0).roundToInt(),
        ascent = (-sizePx * 1984 / 2048.0).roundToInt(),
        descent = (sizePx * 494 / 2048.0).roundToInt(),
        bottom = (-sizePx * yMin / 2048.0).roundToInt(),
    )

    private val regular45 = metrics(45, 2269, -660)
    private val semibold51 = metrics(51, 2275, -677)

    @Test
    fun fontSizeIsCeiledToPixels() {
        // text_sm 13.1 sp at density 3 is 39.3 px; RN paints it at 40 px.
        assertEquals(40, RnTextRules.fontSizePx(RnTextRules.pixelFromSp(13.1f, 39.3f, 3f, null)))
        assertEquals(45, RnTextRules.fontSizePx(45f))
    }

    @Test
    fun maxFontSizeMultiplierCapsScaledPixels() {
        // 16.9 sp at font scale 2.0 (linear 101.4 px) with maxFontSizeMultiplier 2 stays under 16.9 * 3 * 2.
        assertEquals(101.4f, RnTextRules.pixelFromSp(16.9f, 120f, 3f, 2f), 0.001f)
        assertEquals(80f, RnTextRules.pixelFromSp(16.9f, 80f, 3f, 2f), 0.001f)
    }

    @Test
    fun explicitLineHeightMakesTheBoxTheLineHeight() {
        // PostMeta name, text_md leading_tight: round(15 * 1.15) = 17 dp = 51 px. RN bounds: 51 px.
        val tight = RnTextRules.box(regular45, 51f, includeFontPadding = true)
        assertEquals(51, tight.height(1))
        // Leading 51 - 55 = -4: ascent -44 - ceil(-2) = -42.
        assertEquals(42, tight.firstBaseline)
        // Post text, text_md leading_snug: round(19.5) = 20 dp = 60 px. RN bounds: 60 px.
        val snug = RnTextRules.box(regular45, 60f, includeFontPadding = true)
        assertEquals(60, snug.height(1))
        // Leading 5: ascent -44 - ceil(2.5) = -47.
        assertEquals(47, snug.firstBaseline)
        assertEquals(snug, RnTextRules.box(regular45, 60f, includeFontPadding = false))
        assertEquals(180, snug.height(3))
    }

    @Test
    fun naturalLineHeightWithFontPadding() {
        // Discover notice, legacy Text md (15 sp, no line height), two lines. RN bounds: 120 px.
        val box = RnTextRules.box(regular45, null, includeFontPadding = true)
        assertEquals(55, box.pitch)
        assertEquals(120, box.height(2))
        assertEquals(50, box.firstBaseline)
        // End-of-feed message, legacy Text xl-medium (17 sp semibold), two lines. RN bounds: 135 px.
        assertEquals(135, RnTextRules.box(semibold51, null, includeFontPadding = true).height(2))
    }

    @Test
    fun naturalLineHeightWithoutFontPadding() {
        val box = RnTextRules.box(regular45, null, includeFontPadding = false)
        assertEquals(55, box.height(1))
        assertEquals(44, box.firstBaseline)
    }

    /**
     * Android 16 `FontScaleConverterFactory` tables (sources android-36.1) and the `FontScaleConverterImpl`
     * interpolation, for the sizes below. JVM tests have no `TypedValue`, so the test holds the platform data.
     */
    private val fromSp = floatArrayOf(8f, 10f, 12f, 14f, 18f, 20f, 24f, 30f, 100f)
    private val toDp = mapOf(
        1.3f to floatArrayOf(10.4f, 13f, 15.6f, 18.8f, 21.6f, 23.6f, 26.4f, 30f, 100f),
        2.0f to floatArrayOf(16f, 20f, 24f, 26f, 30f, 34f, 36f, 38f, 100f),
    )

    private fun scaledPx(sp: Float, fontScale: Float, density: Float = 3f): Float {
        val table = toDp[fontScale] ?: return sp * fontScale * density
        val i = fromSp.indexOfLast { it <= sp }
        if (fromSp[i] == sp) return table[i] * density
        val t = (sp - fromSp[i]) / (fromSp[i + 1] - fromSp[i])
        return (table[i] + t * (table[i + 1] - table[i])) * density
    }

    private fun sizePx(sp: Float, scale: Float) = RnTextRules.fontSizePx(RnTextRules.pixelFromSp(sp, scaledPx(sp, scale), 3f, null))
    private fun lineHeightPx(sp: Float, scale: Float) = RnTextRules.pixelFromSp(sp, scaledPx(sp, scale), 3f, null)

    @Test
    fun postTextLineBoxAtFontScales() {
        // text_md 15 sp, leading_snug: lineHeight round(19.5) = 20 dp. Both go through the SP conversion.
        assertEquals(45, sizePx(15f, 1.0f))
        assertEquals(60, RnTextRules.box(regular45, lineHeightPx(20f, 1.0f), true).height(1))
        // 1.3: 15 sp -> 19.5 dp -> 58.5 px -> 59 px; 20 sp -> 23.6 dp -> 70.8 px -> 71 px.
        assertEquals(59, sizePx(15f, 1.3f))
        val box13 = RnTextRules.box(metrics(59, 2269, -660), lineHeightPx(20f, 1.3f), true)
        assertEquals(71, box13.height(1))
        assertEquals(213, box13.height(3))
        // 2.0: 15 sp -> 27 dp -> 81 px; 20 sp -> 34 dp -> 102 px. Linear scaling would give 90 and 120.
        assertEquals(81, sizePx(15f, 2.0f))
        val box20 = RnTextRules.box(metrics(81, 2269, -660), lineHeightPx(20f, 2.0f), true)
        assertEquals(102, box20.height(1))
        // Leading 102 - (78 + 20) = 4: ascent -78 - 2 = -80.
        assertEquals(80, box20.firstBaseline)
    }

    @Test
    fun metaAndLegacyLineBoxesAtFontScale2() {
        // PostMeta, text_md leading_tight: 17 dp -> 29 dp at 2.0 -> 87 px.
        assertEquals(87, RnTextRules.box(metrics(81, 2269, -660), lineHeightPx(17f, 2.0f), true).height(1))
        // Legacy Text md (no line height) at 81 px: pitch 98, padding 12 above and 6 below.
        val legacy = RnTextRules.box(metrics(81, 2269, -660), null, includeFontPadding = true)
        assertEquals(98, legacy.pitch)
        assertEquals(214, legacy.height(2))
    }

    @Test
    fun maxFontSizeMultiplierCapsTheScaledLineBox() {
        // Bottom bar badge, 12 sp with maxFontSizeMultiplier 1.5: 12 sp -> 24 dp at 2.0, capped at 12 * 3 * 1.5 = 54 px.
        assertEquals(54, RnTextRules.fontSizePx(RnTextRules.pixelFromSp(12f, scaledPx(12f, 2.0f), 3f, 1.5f)))
    }

    @Test
    fun jsHeaderOffsetExceedsTheRealTabTextBox() {
        // useHeaderOffset: 20 * fontScale dp. The tab text line box: 20 sp through the converter.
        assertEquals(0f, RnFontScale.jsLineHeightExcessPx(20f, scaledPx(20f, 1.0f), 3f, 1.0f), 0.01f)
        // 1.3: 78 px predicted, 71 px real.
        assertEquals(7f, RnFontScale.jsLineHeightExcessPx(20f, scaledPx(20f, 1.3f), 3f, 1.3f), 0.01f)
        // 2.0: 120 px predicted, 102 px real. Measured on emulator-5554: RN composer prompt border at y 675,
        // native (without this rule) at y 657, with the tab bar border at y 471 in both.
        assertEquals(18f, RnFontScale.jsLineHeightExcessPx(20f, scaledPx(20f, 2.0f), 3f, 2.0f), 0.01f)
    }
}
