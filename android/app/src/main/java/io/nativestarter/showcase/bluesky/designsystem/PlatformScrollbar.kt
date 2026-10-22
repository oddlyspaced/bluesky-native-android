package io.nativestarter.showcase.bluesky.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * The scrollbar Android draws for a `ScrollView`, for a `LazyColumn` that would otherwise have none.
 *
 * React Native renders its feeds in a `ScrollView`, so the platform gives it a scrollbar: awakened
 * when the list scrolls, held briefly, then faded out. A Compose `LazyColumn` draws nothing, which
 * was PARITY.md difference 6 and the last difference on the Android home screen.
 *
 * Geometry, colour and timing are the platform's, not invented here:
 *
 * - [THICKNESS] 4 dp, which measured 12 px at the capture density, matching React Native's thumb at
 *   x 1268-1279 on a 1280 px screen.
 * - The colours are the measured thumb: #B0B0B0 in light and #6A6F76 in dark.
 * - [HOLD] and [FADE] are `ViewConfiguration.getScrollDefaultDelay()` and
 *   `getScrollBarFadeDuration()`, both 250 ms.
 * - The thumb length and offset follow `ScrollBarDrawable`: the thumb covers the same fraction of
 *   the track that the viewport covers of the content.
 *
 * A `LazyColumn` does not know the height of items it has not composed, so the content height is
 * estimated from the average of the items that are laid out, which is what `RecyclerView` does in
 * `ScrollbarHelper`. The estimate is exact when every item is composed and approaches the true
 * height as more are.
 *
 * Measured against React Native on the base home feed, both at rest after launch:
 *
 *   React Native   y 544-1755, length 1212, 12 px wide, #B0B0B0
 *   this port      y 548-1809, length 1262, 12 px wide, #B0B0B0
 *
 * Four pixels at the top and fifty on the length, which is the estimate above showing through.
 *
 * This is drawn for fidelity with React Native, not to move a parity ratio: the capture flows wait
 * for the fade before the first screenshot, so neither app shows a thumb in a measured screen, and
 * the numbers are the same with it and without it.
 */
object PlatformScrollbar {
    val THICKNESS: Dp = 4.dp
    val MIN_LENGTH: Dp = 32.dp
    const val HOLD_MS = 250L
    const val FADE_MS = 250

    val light = Color(0xFFB0B0B0)
    val dark = Color(0xFF6A6F76)
}

/**
 * Draws [PlatformScrollbar] over this list.
 *
 * @param state the list's own state, read for the visible window and the scroll position.
 * @param color the thumb colour; pass the dark value under a dark theme.
 */
@Composable
fun Modifier.platformScrollbar(state: LazyListState, color: Color): Modifier {
    // Android awakens the scrollbar at first layout as well as on a scroll (`View.awakenScrollBars`
    // from `onLayout`), which is why React Native shows a thumb on a feed the user has not touched
    // yet. Starting at 1 and fading reproduces that; starting at 0 would only ever show it mid-drag.
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(state.isScrollInProgress) {
        if (state.isScrollInProgress) {
            alpha.snapTo(1f)
        } else {
            delay(PlatformScrollbar.HOLD_MS)
            alpha.animateTo(0f, tween(PlatformScrollbar.FADE_MS))
        }
    }
    val shown by alpha.asState()
    return this.drawWithContent {
        drawContent()
        if (shown <= 0f) return@drawWithContent
        val info = state.layoutInfo
        val visible = info.visibleItemsInfo
        if (visible.isEmpty()) return@drawWithContent
        val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
        if (viewport <= 0f) return@drawWithContent
        // RecyclerView's ScrollbarHelper: average what is laid out, times the item count.
        val average = visible.sumOf { it.size }.toFloat() / visible.size
        val range = average * info.totalItemsCount
        if (range <= viewport) return@drawWithContent
        val scrolled = average * state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset
        val minLength = PlatformScrollbar.MIN_LENGTH.toPx()
        val length = maxOf(minLength, viewport * viewport / range)
        val travel = (viewport - length).coerceAtLeast(0f)
        val top = travel * (scrolled / (range - viewport)).coerceIn(0f, 1f)
        val thickness = PlatformScrollbar.THICKNESS.toPx()
        drawRect(
            color = color.copy(alpha = color.alpha * shown),
            topLeft = Offset(size.width - thickness, top),
            size = Size(thickness, length),
        )
    }
}
