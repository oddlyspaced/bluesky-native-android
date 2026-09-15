package io.nativestarter.showcase.bluesky.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nativestarter.showcase.bluesky.core.analytics.Analytics
import io.nativestarter.showcase.bluesky.core.analytics.InteractionContext
import io.nativestarter.showcase.bluesky.core.common.runCatchingCancellable
import io.nativestarter.showcase.bluesky.core.network.BskyRepository
import io.nativestarter.showcase.bluesky.core.network.HomeFeedPrefs
import io.nativestarter.showcase.bluesky.core.network.PinnedFeed
import io.nativestarter.showcase.bluesky.core.network.PostCache
import io.nativestarter.showcase.bluesky.core.network.PostInteractions
import io.nativestarter.showcase.bluesky.core.network.PostView
import io.nativestarter.showcase.bluesky.core.network.StrongRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What `PostFeed` renders for the Following feed. The `show*` rules are RN `PostFeed.tsx`. */
data class FeedUiState(
    val feedTabs: List<String> = listOf("Following", HomeTabs.FEEDS_LINK),
    val selectedTab: Int = 0,
    val rows: List<PostFeedRow> = emptyList(),
    val isFetched: Boolean = false,
    val isFetching: Boolean = true,
    val isRefreshing: Boolean = false,
    val isFetchingNextPage: Boolean = false,
    val hasNextPage: Boolean = false,
    /** RN `isEmpty`: `!isFetching && !pages.some(page => page.slices.length)`. */
    val isEmpty: Boolean = false,
    val error: Throwable? = null,
) {
    val showLoading: Boolean get() = !isFetched
    val showFirstLoadError: Boolean get() = error != null && isEmpty
    val showEmpty: Boolean get() = isFetched && isEmpty && error == null
    val showLoadMoreError: Boolean get() = error != null && !isEmpty
    /** `shouldRenderEndOfFeed = !hasNextPage && !isEmpty && !isFetching && !isError`. */
    val showEndOfFeed: Boolean get() = !hasNextPage && !isEmpty && !isFetching && error == null && isFetched
}

/** CP-01: the "Following" timeline with RN tuners, pagination, states and events. */
class FeedViewModel(
    private val repository: BskyRepository,
    private val interactions: PostInteractions,
    private val analytics: Analytics,
    private val userDid: String,
    /** Every loaded post, including reply parents and roots, can be a thread placeholder. */
    private val postCache: PostCache = PostCache(),
) : ViewModel() {
    private val prefs = MutableStateFlow(HomeFeedPrefs())
    private val tabs = MutableStateFlow(listOf("Following", HomeTabs.FEEDS_LINK))
    private val selectedTab = MutableStateFlow(0)
    private val source = HomeFeedSource { cursor -> repository.timeline(cursor) }
    private val pager = FeedPager(
        scope = viewModelScope,
        fetch = { cursor ->
            source.fetch(cursor).also { page ->
                postCache.put(page.items.flatMap { listOfNotNull(it.post, PostRefs.postView(it.reply?.parent), PostRefs.postView(it.reply?.root)) })
            }
        },
        selectedCount = { items -> FeedTuner.following(prefs.value, userDid).tune(items).sumOf { it.items.size } },
    )
    private val seenUris = HashSet<String>()

    val state: StateFlow<FeedUiState> = combine(pager.state, prefs, tabs, selectedTab, interactions.shadows) { p, homePrefs, feedTabs, selected, shadows ->
        val slices = FeedTuner.following(homePrefs, userDid).tune(p.items)
        val rows = PostFeedRows.build(slices, withComposerPrompt = true).map { row ->
            if (row is PostFeedRow.SliceItemRow) row.copy(post = interactions.apply(row.post, shadows)) else row
        }
        FeedUiState(
            feedTabs = feedTabs,
            selectedTab = selected,
            rows = rows,
            isFetched = p.isFetched,
            isFetching = p.isFetching,
            isRefreshing = p.isRefreshing,
            isFetchingNextPage = p.isFetchingNextPage,
            hasNextPage = p.hasNextPage,
            isEmpty = !p.isFetching && slices.isEmpty(),
            error = p.error,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FeedUiState())

    init {
        viewModelScope.launch {
            runCatchingCancellable { repository.preferences() }.onSuccess { prefs.value = it.homeFeed }
        }
        viewModelScope.launch {
            runCatchingCancellable { HomeTabs.from(repository.pinnedFeeds()) }.onSuccess { tabs.value = it }
        }
        pager.refresh()
    }

    fun selectTab(index: Int) {
        selectedTab.value = index
    }

    /** RN `feed:refresh`: reason `pull-to-refresh` (PostFeed `refreshFeed`) or `soft-reset` (FeedPage). */
    fun refresh(reason: String) {
        analytics.metric("feed:refresh", linkedMapOf("feedType" to FEED, "feedUrl" to FEED, "reason" to reason))
        pager.refresh(isPullToRefresh = reason == REASON_PULL_TO_REFRESH)
    }

    /** Error banner "Retry": RN `refetch()` without an event. */
    fun retry() = pager.refresh()

    /** `LoadMoreRetryBtn`: RN `fetchNextPage()`. */
    fun retryLoadMore() = pager.fetchNextPage(fromRetry = true)

    /** RN `onEndReached`: does nothing while fetching, without a next page, or in error. */
    fun onEndReached() {
        if (!pager.canFetchNextPage()) return
        analytics.metric(
            "feed:endReached",
            linkedMapOf("feedType" to FEED, "feedUrl" to FEED, "itemCount" to state.value.rows.size),
        )
        pager.fetchNextPage()
    }

    /** RN `onItemSeen`: `post:view` once per URI, only for the slice's feed post. */
    fun onPostSeen(row: PostFeedRow.SliceItemRow) {
        if (row.post.uri != row.slice.feedPostUri || !seenUris.add(row.post.uri)) return
        val rows = state.value.rows
        val index = rows.indexOfFirst { it.key == row.key }.coerceAtLeast(0)
        val position = rows.take(index).count { it is PostFeedRow.SliceItemRow && it.indexInSlice == 0 }
        analytics.metric(
            "post:view",
            linkedMapOf(
                "uri" to row.post.uri,
                "authorDid" to row.post.author.did,
                "isReply" to (row.post.record.reply != null),
                "logContext" to CONTEXT.logContext,
                "feedDescriptor" to FEED,
                "position" to position,
            ),
        )
    }

    fun onPostPressed(post: PostView) = analytics.metric("post:clickthroughItem", clickProps(post))
    fun onAuthorPressed(post: PostView) = analytics.metric("post:clickthroughAuthor", clickProps(post))
    fun onReplyPressed(post: PostView) = analytics.metric("post:clickReply", clickProps(post))
    fun onComposerPressed() = analytics.metric("composerPrompt:press")

    fun toggleLike(post: PostView, via: StrongRef?) = interactions.toggleLike(post, CONTEXT, via)
    fun toggleRepost(post: PostView, via: StrongRef?) = interactions.toggleRepost(post, CONTEXT, via)

    private fun clickProps(post: PostView) = linkedMapOf<String, Any?>(
        "uri" to post.uri,
        "authorDid" to post.author.did,
        "logContext" to CONTEXT.logContext,
        "feedDescriptor" to FEED,
    )

    companion object {
        const val FEED = "following"
        const val REASON_PULL_TO_REFRESH = "pull-to-refresh"
        const val REASON_SOFT_RESET = "soft-reset"
        val CONTEXT = InteractionContext(logContext = "FeedItem", feedDescriptor = FEED)

        /** `viaRepost` (PostFeedItem.tsx): the repost reason `{uri, cid}` when both are set. */
        fun viaOf(reason: FeedReason?): StrongRef? {
            val repost = reason as? FeedReason.Repost ?: return null
            val uri = repost.uri ?: return null
            val cid = repost.cid ?: return null
            return StrongRef(uri, cid)
        }
    }
}

/**
 * Port of `items` in RN src/view/com/home/HomeHeader.tsx: pinned feed names, plus a trailing
 * "Feeds ✨" link tab when no custom feed is pinned. Selecting that tab opens the Feeds screen.
 */
object HomeTabs {
    const val FEEDS_LINK = "Feeds ✨"

    fun from(pinned: List<PinnedFeed>): List<String> {
        val names = pinned.map { it.displayName }.ifEmpty { listOf("Following") }
        val hasPinnedCustom = pinned.any { !it.isTimeline }
        return if (hasPinnedCustom) names else names + FEEDS_LINK
    }

    /** Index of the "Feeds ✨" link tab, or null when custom feeds are pinned. */
    fun feedsLinkIndex(tabs: List<String>): Int? = tabs.indexOf(FEEDS_LINK).takeIf { it >= 0 && it == tabs.lastIndex }
}
