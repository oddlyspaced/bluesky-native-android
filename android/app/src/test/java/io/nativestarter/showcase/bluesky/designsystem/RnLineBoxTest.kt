package io.nativestarter.showcase.bluesky.designsystem

import io.nativestarter.showcase.bluesky.designsystem.text.FontMetricsPx
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
}
