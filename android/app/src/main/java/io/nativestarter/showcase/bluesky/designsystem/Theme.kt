package io.nativestarter.showcase.bluesky.designsystem

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * ALF palettes from @bsky.app/alf dist/palette.js. `DEFAULT_PALETTE` builds light and dark; `DEFAULT_SUBDUED_PALETTE`
 * builds dim. Values are exact hex copies.
 */
data class AlfPalette(
    val contrast: Map<Int, Color>,
    val primary: Map<Int, Color>,
    val positive: Map<Int, Color>,
    val negative: Map<Int, Color>,
) {
    val white = Color(0xFFFFFFFF)
    val black = Color(0xFF000000)
    val pink = Color(0xFFEC4899)
    val yellow = Color(0xFFFFC404)
    val like = pink

    fun contrast(step: Int): Color = contrast.getValue(step)
    fun primary(step: Int): Color = primary.getValue(step)
    fun positive(step: Int): Color = positive.getValue(step)
    fun negative(step: Int): Color = negative.getValue(step)

    /** Port of `invertPalette`: contrast 0↔1000, 25↔975 … 500↔500; primary/positive/negative 25↔975 … 500↔500. */
    fun inverted(): AlfPalette = AlfPalette(
        contrast = contrast.mapValues { (step, _) -> contrast.getValue(1000 - step) },
        primary = primary.mapValues { (step, _) -> primary.getValue(1000 - step) },
        positive = positive.mapValues { (step, _) -> positive.getValue(1000 - step) },
        negative = negative.mapValues { (step, _) -> negative.getValue(1000 - step) },
    )

    companion object {
        private fun ramp(vararg pairs: Pair<Int, Long>) = pairs.associate { (k, v) -> k to Color(v) }

        val DEFAULT = AlfPalette(
            contrast = ramp(
                0 to 0xFFFFFFFF, 25 to 0xFFF9FAFB, 50 to 0xFFEFF2F6, 100 to 0xFFDCE2EA, 200 to 0xFFC0CAD8,
                300 to 0xFFA5B2C5, 400 to 0xFF8798B0, 500 to 0xFF667B99, 600 to 0xFF526580, 700 to 0xFF405168,
                800 to 0xFF313F54, 900 to 0xFF232E3E, 950 to 0xFF19222E, 975 to 0xFF111822, 1000 to 0xFF000000,
            ),
            primary = ramp(
                25 to 0xFFF5F9FF, 50 to 0xFFE5F0FF, 100 to 0xFFCCE1FF, 200 to 0xFFA8CCFF, 300 to 0xFF75AFFF,
                400 to 0xFF4291FF, 500 to 0xFF006AFF, 600 to 0xFF0059D6, 700 to 0xFF0048AD, 800 to 0xFF00398A,
                900 to 0xFF002861, 950 to 0xFF001E47, 975 to 0xFF001533,
            ),
            positive = ramp(
                25 to 0xFFECFEF5, 50 to 0xFFD3FDE8, 100 to 0xFFA3FACF, 200 to 0xFF6AF6B0, 300 to 0xFF2CF28F,
                400 to 0xFF0DD370, 500 to 0xFF09B35E, 600 to 0xFF04904A, 700 to 0xFF036D38, 800 to 0xFF04522B,
                900 to 0xFF033F21, 950 to 0xFF032A17, 975 to 0xFF021D0F,
            ),
            negative = ramp(
                25 to 0xFFFFF5F7, 50 to 0xFFFEE7EC, 100 to 0xFFFDD3DD, 200 to 0xFFFBBBCA, 300 to 0xFFF891A9,
                400 to 0xFFF65A7F, 500 to 0xFFE91646, 600 to 0xFFCA123D, 700 to 0xFFA71134, 800 to 0xFF7F0B26,
                900 to 0xFF5F071C, 950 to 0xFF430413, 975 to 0xFF30030D,
            ),
        )

        val SUBDUED = AlfPalette(
            contrast = ramp(
                0 to 0xFFFFFFFF, 25 to 0xFFF9FAFB, 50 to 0xFFF2F4F8, 100 to 0xFFE2E7EE, 200 to 0xFFC3CDDA,
                300 to 0xFFABB8C9, 400 to 0xFF8D9DB4, 500 to 0xFF6F839F, 600 to 0xFF586C89, 700 to 0xFF485B75,
                800 to 0xFF394960, 900 to 0xFF2C3A4E, 950 to 0xFF222E3F, 975 to 0xFF1C2736, 1000 to 0xFF151D28,
            ),
            primary = ramp(
                25 to 0xFFF5F9FF, 50 to 0xFFEBF3FF, 100 to 0xFFD6E7FF, 200 to 0xFFADCFFF, 300 to 0xFF80B5FF,
                400 to 0xFF4D97FF, 500 to 0xFF0F73FF, 600 to 0xFF0661E0, 700 to 0xFF0A52B8, 800 to 0xFF0E4490,
                900 to 0xFF123464, 950 to 0xFF122949, 975 to 0xFF122136,
            ),
            positive = ramp(
                25 to 0xFFECFEF5, 50 to 0xFFD8FDEB, 100 to 0xFFA8FAD1, 200 to 0xFF6FF6B3, 300 to 0xFF31F291,
                400 to 0xFF0EDD75, 500 to 0xFF0AC266, 600 to 0xFF049F52, 700 to 0xFF038142, 800 to 0xFF056636,
                900 to 0xFF04522B, 950 to 0xFF053D21, 975 to 0xFF052917,
            ),
            negative = ramp(
                25 to 0xFFFFF5F7, 50 to 0xFFFEEBEF, 100 to 0xFFFDD8E1, 200 to 0xFFFCC0CE, 300 to 0xFFF99AB0,
                400 to 0xFFF76486, 500 to 0xFFEB2452, 600 to 0xFFD81341, 700 to 0xFFBA1239, 800 to 0xFF910D2C,
                900 to 0xFF6F0B22, 950 to 0xFF500B1C, 975 to 0xFF3E0915,
            ),
        )
    }
}

enum class ThemeName { Light, Dark, Dim }

/** Semantic atoms from @bsky.app/alf dist/themes.js `createTheme`. Features read these, not raw palette steps. */
class AlfTheme(val name: ThemeName, val palette: AlfPalette) {
    val isDark: Boolean get() = name != ThemeName.Light

    val text = palette.contrast(1000)
    val textContrastLow = palette.contrast(400)
    val textContrastMedium = palette.contrast(700)
    val textContrastHigh = palette.contrast(900)
    val textInverted = palette.contrast(0)
    /** `text_link`: `scheme === 'dark' ? primary_600 : primary_500`. */
    val textLink = if (isDark) palette.primary(600) else palette.primary(500)
    val bg = palette.contrast(0)
    val bgContrast25 = palette.contrast(25)
    val bgContrast50 = palette.contrast(50)
    val bgContrast100 = palette.contrast(100)
    val borderContrastLow = palette.contrast(100)
    val borderContrastMedium = palette.contrast(200)
    val borderContrastHigh = palette.contrast(300)

    /** Post control icons and counts (`PostControlButton`: `t.palette.contrast_500`). */
    val controlIcon = palette.contrast(500)
    /** `t.palette.pink`, the same in every theme. */
    val like = palette.pink
    /** `RepostButton` `activeColor={t.palette.positive_500}`. */
    val repostActive = palette.positive(500)
    val primary = palette.primary(500)
    /** Reply and thread lines: `select(t.name, {light: contrast_100, dim: contrast_200, dark: contrast_200})`. */
    val replyLine = if (isDark) palette.contrast(200) else palette.contrast(100)
    /** Loading placeholder blocks: `t.palette.contrast_50`. */
    val placeholder = palette.contrast(50)
    /** `secondary_inverted` button background (not disabled): contrast_900. */
    val invertedButtonBg = palette.contrast(900)
    /** Error banner (`ErrorMessage`): `t.palette.pink` background with white text. */
    val errorBanner = palette.pink
}

object AlfThemes {
    val light = AlfTheme(ThemeName.Light, AlfPalette.DEFAULT)
    val dark = AlfTheme(ThemeName.Dark, AlfPalette.DEFAULT.inverted())
    val dim = AlfTheme(ThemeName.Dim, AlfPalette.SUBDUED.inverted())

    /**
     * Port of `useColorModeTheme` (src/alf/util/useColorModeTheme.ts) with the persisted defaults
     * `colorMode: 'system'` and `darkTheme: 'dim'`: light when the system is light, otherwise the dark theme preference.
     */
    fun forSystem(systemDark: Boolean, darkTheme: ThemeName = ThemeName.Dim): AlfTheme =
        if (!systemDark) light else if (darkTheme == ThemeName.Dark) dark else dim
}

val LocalAlf = staticCompositionLocalOf { AlfThemes.light }

/**
 * Provides the theme for the system color mode and sets the system bar icon color like RN `src/view/shell/index.tsx`:
 * dark icons on the light theme, light icons otherwise.
 */
@Composable
fun ProvideAlfTheme(theme: AlfTheme = AlfThemes.forSystem(isSystemInDarkTheme()), content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !theme.isDark
            controller.isAppearanceLightNavigationBars = !theme.isDark
        }
    }
    CompositionLocalProvider(LocalAlf provides theme, content = content)
}
