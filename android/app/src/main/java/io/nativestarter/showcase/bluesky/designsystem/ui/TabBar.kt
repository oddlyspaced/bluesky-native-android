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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.nativestarter.showcase.bluesky.designsystem.FontSize
import io.nativestarter.showcase.bluesky.designsystem.Palette
import io.nativestarter.showcase.bluesky.designsystem.RnExplicitLineHeightStyle
import io.nativestarter.showcase.bluesky.designsystem.Theme
import io.nativestarter.showcase.bluesky.designsystem.Txt
import io.nativestarter.showcase.bluesky.designsystem.alfText
import io.nativestarter.showcase.bluesky.designsystem.pressable

private val CONTENT_PADDING = 6.dp
private val ITEM_PADDING = 10.dp
private val MIN_INDICATOR_WIDTH = 45.dp
private val INDICATOR_HEIGHT = 2.dp

/**
 * RN `TabBar` (src/view/com/pager/TabBar.tsx): content padding 6, item padding 10, paddingTop 10,
 * inner paddingBottom 10 with a 3dp bottom border slot, text md semibold lineHeight 20,
 * unselected opacity 0.7, primary_500 indicator under the selected label and a hairline below.
 *
 * - [centered] (home feed tabs): every item has `flexGrow: 1`, so each keeps its natural width and the spare
 *   width is shared equally. Equal item widths would move the labels (measured: "Following" 2 px, "Feeds" 1 px).
 * - Otherwise (profile tabs) the row scrolls horizontally and items keep their natural width.
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
    // RN `outerBottomBorder`: absolute at top 100%, hairline, bg_contrast_100. It sits below the bar and adds no height.
    val borderColor = Palette.contrast_100
    Column(
        modifier
            .fillMaxWidth()
            .background(Theme.bg)
            .drawWithContent {
                drawContent()
                drawRect(borderColor, topLeft = Offset(0f, size.height), size = Size(size.width, 1f))
            }
            .testTag(testTag),
    ) {
        val content: @Composable () -> Unit = {
            items.forEachIndexed { index, item ->
                TabItem(item, index, index == selected, testTag, onSelect)
            }
        }
        if (centered) {
            FlexGrowRow(Modifier.fillMaxWidth().padding(horizontal = CONTENT_PADDING).testTag("$testTag-selector"), content)
        } else {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = CONTENT_PADDING)
                    .testTag("$testTag-selector"),
            ) { content() }
        }
    }
}

/** A row whose children keep their natural width plus an equal share of the spare width (CSS/Yoga `flexGrow: 1`). */
@Composable
private fun FlexGrowRow(modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        if (measurables.isEmpty()) return@Layout layout(constraints.minWidth, constraints.minHeight) {}
        val natural = measurables.map { it.maxIntrinsicWidth(constraints.maxHeight) }
        val available = if (constraints.hasBoundedWidth) constraints.maxWidth else natural.sum()
        val spare = (available - natural.sum()).coerceAtLeast(0)
        val share = spare / measurables.size
        val remainder = spare - share * measurables.size
        val placeables = measurables.mapIndexed { i, measurable ->
            val width = natural[i] + share + if (i == measurables.lastIndex) remainder else 0
            measurable.measure(Constraints(minWidth = width, maxWidth = width, minHeight = 0, maxHeight = constraints.maxHeight))
        }
        val height = placeables.maxOf { it.height }
        layout(available, height) {
            var x = 0
            placeables.forEach {
                it.place(x, 0)
                x += it.width
            }
        }
    }
}

@Composable
private fun TabItem(item: String, index: Int, isSelected: Boolean, testTag: String, onSelect: (Int) -> Unit) {
    val density = LocalDensity.current
    val indicatorColor = Palette.primary_500
    var textWidthPx by remember(item) { mutableIntStateOf(0) }
    var itemWidthPx by remember(item) { mutableIntStateOf(0) }
    // getScaleX: min(max(45, textWidth), itemWidth - 2 * CONTENT_PADDING).
    val indicatorWidth = with(density) {
        minOf(maxOf(MIN_INDICATOR_WIDTH, textWidthPx.toDp()), itemWidthPx.toDp() - CONTENT_PADDING * 2)
    }
    Box(
        Modifier
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
                // itemText lineHeight 20: an explicit line height. The line box is 20 dp, with no RN text-block padding.
                alfText(FontSize.md, FontWeight.SemiBold).copy(lineHeight = 20.sp, lineHeightStyle = RnExplicitLineHeightStyle),
                Modifier
                    .alpha(if (isSelected) 1f else 0.7f)
                    .testTag("$testTag-$item")
                    .onSizeChanged { textWidthPx = it.width },
                maxLines = 1,
                align = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            // itemInner: paddingBottom 10 then a transparent 3 dp bottom border. The indicator is a separate absolute
            // view, borderBottomWidth 2, at the bottom of the items row. It is drawn, so it never changes the layout.
            Box(
                Modifier
                    .width(with(density) { textWidthPx.toDp() })
                    .height(3.dp)
                    .drawBehind {
                        if (isSelected) {
                            val w = indicatorWidth.coerceAtLeast(0.dp).toPx()
                            val h = INDICATOR_HEIGHT.toPx()
                            drawRect(
                                indicatorColor,
                                topLeft = Offset((size.width - w) / 2f, size.height - h),
                                size = Size(w, h),
                            )
                        }
                    },
            )
        }
    }
}
