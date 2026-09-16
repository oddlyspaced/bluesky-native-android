package io.nativestarter.showcase.bluesky.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ButtonSize { Small, Large }

enum class ButtonColor { Primary, Secondary, SecondaryInverted }

/**
 * ALF `Button` (src/components/Button.tsx), solid variant, default (pill) shape:
 * - Small: paddingVertical 8, paddingHorizontal 14, gap 5, `text_sm` medium; 33 dp tall, 33 x 33 when [square].
 * - Large: paddingVertical 12, paddingHorizontal 24, gap 6, `text_md` medium.
 * - Colours: primary (primary_500, white), secondary (contrast_50, contrast_700),
 *   secondary_inverted (contrast_900, text_inverted).
 * [label] is the accessibility label; [text] is the visible text, or null for an icon-only button.
 */
@Composable
fun AlfButton(
    label: String,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    size: ButtonSize = ButtonSize.Small,
    color: ButtonColor = ButtonColor.Secondary,
    text: String? = label,
    @DrawableRes iconStart: Int? = null,
    @DrawableRes iconEnd: Int? = null,
    iconSize: Dp = 16.dp,
    square: Boolean = false,
    testTag: String? = null,
    /** RN `accessibilityHint`, exposed as the click action label. */
    hint: String? = null,
    /** RN `<ButtonText maxFontSizeMultiplier>`, for example 2 on the thread anchor follow button. */
    maxFontScale: Float? = null,
) {
    val theme = LocalAlf.current
    val background = when (color) {
        ButtonColor.Primary -> theme.primary
        ButtonColor.Secondary -> theme.bgContrast50
        ButtonColor.SecondaryInverted -> theme.invertedButtonBg
    }
    val foreground = when (color) {
        ButtonColor.Primary -> theme.palette.white
        ButtonColor.Secondary -> theme.textContrastMedium
        ButtonColor.SecondaryInverted -> theme.textInverted
    }
    val small = size == ButtonSize.Small
    Row(
        modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .then(if (small) Modifier.heightIn(min = 33.dp) else Modifier)
            .then(if (square) Modifier.size(33.dp) else Modifier)
            .clip(RoundedCornerShape(Radius.full))
            .background(background)
            .then(if (hint != null) Modifier.semantics { onClick(label = hint) { onPress(); true } } else Modifier)
            .pressable(label, onPress)
            .padding(
                horizontal = if (square) 0.dp else if (small) 14.dp else 24.dp,
                vertical = if (square) 0.dp else if (small) 8.dp else 12.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(if (small) 5.dp else 6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        iconStart?.let { BskyIcon(it, iconSize, foreground) }
        if (text != null) {
            Txt(text, alfText(if (small) FontSize.sm else FontSize.md, FontWeight.Medium, foreground), maxFontScale = maxFontScale)
        }
        iconEnd?.let { BskyIcon(it, iconSize, foreground, Modifier.width(iconSize)) }
    }
}

