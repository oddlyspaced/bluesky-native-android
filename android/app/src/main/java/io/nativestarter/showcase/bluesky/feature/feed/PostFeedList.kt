package io.nativestarter.showcase.bluesky.feature.feed

import io.nativestarter.showcase.bluesky.designsystem.Space
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.nativestarter.showcase.bluesky.designsystem.LocalAlf
import io.nativestarter.showcase.bluesky.designsystem.feed.LoadMoreRetryButton
import io.nativestarter.showcase.bluesky.designsystem.feed.PostLoadingPlaceholder
import io.nativestarter.showcase.bluesky.designsystem.feed.ViewFullThreadRow
import io.nativestarter.showcase.bluesky.designsystem.post.PostActions
import io.nativestarter.showcase.bluesky.designsystem.post.PostFeedItem

/**
 * The RN `PostFeed` list body (src/view/com/posts/PostFeed.tsx), shared by the Following feed and profile feeds
 * (review A11): loading placeholders, the first-load error, the empty state, slice rows, "View full thread", the
 * load-more retry and `FeedFooter`. Screens pass the parts that differ per feed.
 */
fun LazyListScope.postFeedItems(
    state: FeedUiState,
    /** RN FeedFooter offset: `max(headerOffset, 32) * 2` on native. */
    footerOffset: Dp,
    currentAccountDid: String?,
    onPostSeen: (PostFeedRow.SliceItemRow) -> Unit,
    onOpenFullThread: (String) -> Unit,
    onRetryLoadMore: () -> Unit,
    postActions: (PostFeedRow.SliceItemRow) -> PostActions,
    errorContent: @Composable () -> Unit,
    emptyContent: @Composable () -> Unit,
    endOfFeedContent: @Composable () -> Unit,
    /** Rows only some feeds have: the composer prompt and the Discover fallback marker. */
    extraRow: @Composable (PostFeedRow) -> Unit = {},
) {
    if (state.showLoading) {
        items(8, key = { "loading-$it" }) { PostLoadingPlaceholder() }
    } else if (state.showFirstLoadError) {
        item(key = "error") { errorContent() }
    } else if (state.showEmpty) {
        item(key = "empty") { emptyContent() }
    }
    items(state.rows, key = { it.key }, contentType = { it::class }) { row ->
        when (row) {
            is PostFeedRow.SliceItemRow -> {
                PostFeedItem(
                    post = row.post,
                    actions = postActions(row),
                    isThreadParent = row.isThreadParent,
                    isThreadChild = row.isThreadChild,
                    isThreadLastChild = row.isThreadLastChild,
                    showTopBorder = !row.hideTopBorder,
                    reason = row.reason,
                    showReplyTo = row.showReplyTo,
                    parentAuthor = row.parentAuthor,
                    isParentBlocked = row.isParentBlocked,
                    isParentNotFound = row.isParentNotFound,
                    currentAccountDid = currentAccountDid,
                )
            }
            is PostFeedRow.ViewFullThread -> ViewFullThreadRow(onPress = { onOpenFullThread(row.uri) })
            PostFeedRow.ComposerPrompt, PostFeedRow.FallbackMarker -> extraRow(row)
        }
    }
    if (state.showLoadMoreError) {
        item(key = "loadMoreError") {
            LoadMoreRetryButton("There was an issue fetching posts. Tap here to try again.", onRetryLoadMore)
        }
    }
    item(key = "footer") {
        when {
            state.isFetchingNextPage -> Column(Modifier.fillMaxWidth().padding(top = Space.xl), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.size(24.dp), color = LocalAlf.current.textContrastMedium, strokeWidth = 2.dp)
                Spacer(Modifier.height(footerOffset))
            }
            state.showEndOfFeed -> Box(Modifier.heightIn(min = footerOffset)) { endOfFeedContent() }
            else -> Spacer(Modifier.height(footerOffset))
        }
    }
}

/**
 * RN `List` viewability for `onItemSeen` (src/view/com/util/List.tsx): `itemVisiblePercentThreshold: 40` and
 * `minimumViewTime: 500`. `onViewableItemsChanged` reports the items in index order, so a screen of rows logs
 * `post:view` top to bottom (review N6: `events.mjs --ordered`).
 */
object PostViewVisibility {
    const val VISIBLE_FRACTION = 0.4f
    const val MIN_VIEW_MS = 500L

    /** One laid-out list item: its key, index, and offset and size along the list axis. */
    data class Item(val key: Any, val index: Int, val offset: Int, val size: Int)

    /** Keys of items at least [VISIBLE_FRACTION] inside the viewport, top to bottom. */
    fun visibleKeys(items: List<Item>, viewportStart: Int, viewportEnd: Int): List<Any> = items
        .filter { item ->
            val visible = minOf(item.offset + item.size, viewportEnd) - maxOf(item.offset, viewportStart)
            item.size > 0 && visible.toFloat() / item.size >= VISIBLE_FRACTION
        }
        .sortedBy { it.index }
        .map { it.key }
}

/**
 * Reports the keys of the rows that stayed at least [PostViewVisibility.VISIBLE_FRACTION] visible for
 * [PostViewVisibility.MIN_VIEW_MS], in list order.
 *
 * Every list that logs `post:view` uses this, so the feed and the thread share RN's one `List` viewability rule
 * (src/view/com/util/List.tsx). The thread used to log on composition instead, which counted rows the lazy list
 * had prepared off-screen and rows the reader never saw (review B9).
 */
@Composable
fun TrackVisibleRows(listState: LazyListState, enabled: Boolean = true, onVisible: (List<Any>) -> Unit) {
    val currentOnVisible by rememberUpdatedState(onVisible)
    LaunchedEffect(listState, enabled) {
        if (!enabled) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            PostViewVisibility.visibleKeys(
                info.visibleItemsInfo.map { PostViewVisibility.Item(it.key, it.index, it.offset, it.size) },
                info.viewportStartOffset,
                info.viewportEndOffset,
            )
        }
            .distinctUntilChanged()
            .collectLatest { keys ->
                delay(PostViewVisibility.MIN_VIEW_MS)
                currentOnVisible(keys)
            }
    }
}

/**
 * Calls [onPostSeen] for each post row that stays visible for [PostViewVisibility.MIN_VIEW_MS], in list order.
 * The view model logs each URI once.
 */
@Composable
fun TrackPostViews(
    listState: LazyListState,
    rows: List<PostFeedRow>,
    onPostSeen: (PostFeedRow.SliceItemRow) -> Unit,
    enabled: Boolean = true,
) {
    val currentRows by rememberUpdatedState(rows)
    TrackVisibleRows(listState, enabled) { keys ->
        val byKey = currentRows.filterIsInstance<PostFeedRow.SliceItemRow>().associateBy { it.key }
        keys.forEach { key -> byKey[key]?.let(onPostSeen) }
    }
}

/**
 * RN `PostFeed` `getPostPosition('sliceItem', slice.items[0]._reactKey)`: the number of root rows
 * (`indexInSlice === 0`) before the first row of the post's slice. Every post of a slice therefore logs the
 * slice's position, including a thread reply that is the slice's feed post.
 */
object PostViewPosition {
    fun of(rows: List<PostFeedRow>, row: PostFeedRow.SliceItemRow): Int {
        val first = rows.indexOfFirst { it is PostFeedRow.SliceItemRow && it.slice == row.slice && it.indexInSlice == 0 }
        val index = if (first >= 0) first else rows.indexOfFirst { it.key == row.key }.coerceAtLeast(0)
        return rows.take(index).count { it is PostFeedRow.SliceItemRow && it.indexInSlice == 0 }
    }
}
