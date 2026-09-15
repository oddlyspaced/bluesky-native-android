package io.nativestarter.showcase.bluesky.feature.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
                LaunchedEffect(row.key) { onPostSeen(row) }
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
            state.isFetchingNextPage -> Column(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.size(24.dp), color = LocalAlf.current.textContrastMedium, strokeWidth = 2.dp)
                Spacer(Modifier.height(footerOffset))
            }
            state.showEndOfFeed -> Box(Modifier.heightIn(min = footerOffset)) { endOfFeedContent() }
            else -> Spacer(Modifier.height(footerOffset))
        }
    }
}
