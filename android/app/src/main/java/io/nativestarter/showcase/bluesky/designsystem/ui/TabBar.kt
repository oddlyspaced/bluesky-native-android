package io.nativestarter.showcase.bluesky.designsystem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.nativestarter.showcase.bluesky.designsystem.FontSize
import io.nativestarter.showcase.bluesky.designsystem.Hairline
import io.nativestarter.showcase.bluesky.designsystem.Palette
import io.nativestarter.showcase.bluesky.designsystem.Theme
import io.nativestarter.showcase.bluesky.designsystem.Txt
import io.nativestarter.showcase.bluesky.designsystem.alfText
import io.nativestarter.showcase.bluesky.designsystem.pressable

private val CONTENT_PADDING = 6.dp
private val ITEM_PADDING = 10.dp
private val MIN_INDICATOR_WIDTH = 45.dp

/**
 * RN `TabBar` (src/view/com/pager/TabBar.tsx): content padding 6, item padding 10, paddingTop 10,
 * inner paddingBottom 10 with a 3dp bottom border slot, text md semibold lineHeight 20,
 * unselected opacity 0.7, primary_500 indicator under the selected label and a hairline below.
 * [centered] gives every item flexGrow (home feed tabs); otherwise the row scrolls (profile tabs).
 */
@Composable
fun TabBar(
    items: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    centered: Boolean,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    Column(modifier.fillMaxWidth().background(Theme.bg).testTag(testTag)) {
        val rowModifier = if (centered) Modifier.fillMaxWidth() else Modifier.horizontalScroll(rememberScrollState())
        Row(rowModifier.padding(horizontal = CONTENT_PADDING)) {
            items.forEachIndexed { index, item ->
                var textWidthPx by remember(item) { mutableIntStateOf(0) }
                var itemWidthPx by remember(item) { mutableIntStateOf(0) }
                val isSelected = index == selected
                // getScaleX: min(max(45, textWidth), itemWidth - 2 * CONTENT_PADDING).
                val indicatorWidth = with(density) {
                    minOf(maxOf(MIN_INDICATOR_WIDTH, textWidthPx.toDp()), itemWidthPx.toDp() - CONTENT_PADDING * 2)
                }
                Box(
                    Modifier
                        .then(if (centered) Modifier.weight(1f) else Modifier)
                        .onSizeChanged { itemWidthPx = it.width }
                        .testTag("$testTag-selector-$index")
                        .pressable(item) { onSelect(index) }
                        .padding(horizontal = ITEM_PADDING)
                        .padding(top = 10.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Txt(
                            item,
                            alfText(FontSize.md, FontWeight.SemiBold).copy(lineHeight = 20.sp),
                            Modifier
                                .alpha(if (isSelected) 1f else 0.7f)
                                .testTag("$testTag-$item")
                                .onSizeChanged { textWidthPx = it.width },
                            maxLines = 1,
                            align = TextAlign.Center,
                        )
                        Spacer(Modifier.height(10.dp))
                        // RN draws the indicator as a scaled overlay, so it never changes the item width.
                        // The slot has the text width; the indicator is drawn centred and may overflow it.
                        Box(
                            Modifier
                                .width(with(density) { textWidthPx.toDp() })
                                .height(3.dp)
                                .drawBehind {
                                    if (isSelected) {
                                        val w = indicatorWidth.coerceAtLeast(0.dp).toPx()
                                        drawRect(
                                            Palette.primary_500,
                                            topLeft = Offset((size.width - w) / 2f, 0f),
                                            size = Size(w, size.height),
                                        )
                                    }
                                },
                        )
                    }
                }
            }
        }
        Hairline()
    }
}
