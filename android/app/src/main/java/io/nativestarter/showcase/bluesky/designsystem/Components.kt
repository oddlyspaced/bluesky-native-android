package io.nativestarter.showcase.bluesky.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import kotlinx.coroutines.delay
import java.time.Instant

/** Current time for relative timestamps, re-read every minute like RN `useTickEveryMinute`. */
val LocalNow = compositionLocalOf { Instant.now() }

@Composable
fun rememberMinuteTick(): Instant {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = Instant.now()
        }
    }
    return now
}

/** Icon from a VectorDrawable ported from RN assets/icons. RN icon sizes: sm 16, md 20, lg 24, xl 28. */
@Composable
fun BskyIcon(@DrawableRes id: Int, size: Dp, tint: Color?, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id),
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = tint?.let { ColorFilter.tint(it) },
    )
}

@Composable
fun Txt(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
    /** RN `maxFontSizeMultiplier`: caps system font scaling for this text (for example 2 for header titles). */
    maxFontScale: Float? = null,
) {
    io.nativestarter.showcase.bluesky.designsystem.text.RnText(
        text = androidx.compose.ui.text.AnnotatedString(text),
        style = resolveTextStyle(style, align),
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        maxFontScale = maxFontScale,
    )
}

/** Resolves an unspecified color to the theme text atom. The RN `maxFontSizeMultiplier` cap is applied by `RnText`. */
@Composable
fun resolveTextStyle(style: TextStyle, align: TextAlign? = null): TextStyle {
    var resolved = if (style.color == Color.Unspecified) style.copy(color = LocalAlf.current.text) else style
    if (align != null) resolved = resolved.copy(textAlign = align)
    return resolved
}

/** RN `a.border_t` / `a.border_b`: StyleSheet.hairlineWidth. */
@Composable
fun Hairline(color: Color = Theme.borderContrastLow, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(hairlineWidth())
            .drawBehind {
                val stroke = size.height + RN_ANDROID_HAIRLINE_OVERDRAW_PX
                drawRect(
                    color,
                    alpha = RN_ANDROID_HAIRLINE_COVERAGE,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - stroke),
                    size = androidx.compose.ui.geometry.Size(size.width, stroke),
                )
            },
    )
}

/**
 * RN Android draws a `StyleSheet.hairlineWidth` border (1 px at density 3) as an anti-aliased stroke on a pixel edge:
 * two rows at half coverage. Measured on the RN Android baselines: every border on home, thread and profile is 2 px
 * tall in (238, 241, 245), which is contrast_100 (#DCE2EA) at 50 % over white, and the content after a border moves
 * by 1 px. Here the border reserves 1 px, draws one more pixel over the previous sibling, and uses half alpha.
 */
private const val RN_ANDROID_HAIRLINE_OVERDRAW_PX = 1f
private const val RN_ANDROID_HAIRLINE_COVERAGE = 0.5f

/**
 * RN `StyleSheet.hairlineWidth` (react-native Libraries/StyleSheet/StyleSheet.js): `PixelRatio.roundToNearestPixel(0.4)`,
 * or one physical pixel when that rounds to zero. `Dp.Hairline` is zero-sized in a layout, so it cannot stand in for it.
 */
@Composable
fun hairlineWidth(): Dp {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val pixels = kotlin.math.round(0.4f * density).coerceAtLeast(1f)
    return (pixels / density).dp
}

/** Press handling without Material ripple; RN `Pressable` has no ripple on these controls. */
fun Modifier.pressable(label: String? = null, onClick: () -> Unit): Modifier =
    this
        .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)
        .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)

/**
 * Fallback avatar for accounts without an avatar image. A generic silhouette drawn in code:
 * Bluesky's default avatar artwork is a trademark asset (ASSETS.md section 3) and is not bundled.
 * Geometry: blue disc, head circle, and a shoulder ellipse clipped by the disc.
 */
@Composable
fun DefaultAvatar(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size).clip(CircleShape).semantics { contentDescription = "Default avatar" }) {
        val k = this.size.width / 24f
        scale(k, k, pivot = Offset.Zero) {
            drawCircle(Palette.defaultAvatar, radius = 12f, center = Offset(12f, 12f))
            drawCircle(Color.White, radius = 3.5f, center = Offset(12f, 9.5f))
            drawOval(Color.White, topLeft = Offset(5f, 15.5f), size = androidx.compose.ui.geometry.Size(14f, 11f))
        }
    }
}

/**
 * Neutral placeholder mark for the home header. Not the Bluesky butterfly (ASSETS.md section 3):
 * a rounded square in the brand blue with a white inner dot.
 */
@Composable
fun ShowcaseMark(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size).semantics { contentDescription = "Showcase logo" }) {
        val s = this.size.width
        drawRoundRect(
            Palette.brandBlue,
            size = androidx.compose.ui.geometry.Size(s, s),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.28f),
        )
        drawCircle(Color.White, radius = s * 0.16f, center = Offset(s / 2, s / 2))
    }
}

/**
 * RN `UserAvatar`. Remote avatar images are not part of this slice (the seeded accounts have none),
 * so every account renders the RN fallback avatar.
 */
@Composable
fun UserAvatar(avatarUrl: String?, size: Dp, modifier: Modifier = Modifier) {
    DefaultAvatar(size, modifier)
}

/** RN `FABInner`: 56dp circle, primary_500, EditBig icon. */
@Composable
fun ComposeFab(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Palette.primary_500)
            .pressable("New post", onClick),
        contentAlignment = Alignment.Center,
    ) {
        BskyIcon(io.nativestarter.showcase.bluesky.R.drawable.ic_edit_big, 24.dp, Color.White)
    }
}

/**
 * RN `Layout.Header` (src/components/Layout/Header): minHeight 48, pb_xs, border_b contrast_low,
 * 34dp slots. On Android the title is left aligned (`align === 'platform'` centers only on iOS).
 */
@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    right: @Composable () -> Unit = {},
) {
    Column(modifier.fillMaxWidth().background(Theme.bg)) {
        Row(
            // Gutters: 16dp on phones (measured against the RN Android baseline: title at 57dp, slot centre 394dp).
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = Space.lg, end = Space.lg, bottom = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(34.dp).pressable("Go back", onBack), contentAlignment = Alignment.Center) {
                BskyIcon(io.nativestarter.showcase.bluesky.R.drawable.ic_arrow_left, 24.dp, Theme.textContrastMedium)
            }
            Spacer(Modifier.width(Space.sm))
            // RN Layout.Header.TitleText: numberOfLines 2, maxFontSizeMultiplier 2 (review P13).
            Txt(
                title,
                alfText(FontSize.lg, FontWeight.SemiBold, leading = LineHeight.tight),
                Modifier.weight(1f),
                maxLines = 2,
                maxFontScale = 2f,
            )
            Spacer(Modifier.width(Space.sm))
            Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) { right() }
        }
        Hairline()
    }
}

/** Shown for tabs and actions outside CP-00..CP-03. */
@Composable
fun NotInShowcase(title: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().background(Theme.bg).padding(horizontal = Space.xl),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Txt(title, alfText(FontSize._2xl, FontWeight.Bold), align = TextAlign.Center)
        Spacer(Modifier.height(Space.sm))
        Txt(
            "Not part of this showcase",
            alfText(FontSize.md, color = Theme.textContrastMedium),
            align = TextAlign.Center,
        )
    }
}
