package io.nativestarter.showcase.bluesky.feature.feed

import io.nativestarter.showcase.bluesky.core.common.runCatchingCancellable
import io.nativestarter.showcase.bluesky.core.network.FeedResponse
import io.nativestarter.showcase.bluesky.core.network.FeedViewPost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Pages loaded for one feed, and the query state RN `usePostFeedQuery` exposes to `PostFeed`. */
data class FeedPagerState(
    val pages: List<List<FeedViewPost>> = emptyList(),
    val cursor: String? = null,
    val hasNextPage: Boolean = false,
    val isFetched: Boolean = false,
    val isFetching: Boolean = false,
    val isFetchingNextPage: Boolean = false,
    val isRefreshing: Boolean = false,
    /** RN `isError`: the last fetch (first page, refresh or next page) failed. */
    val error: Throwable? = null,
    val generation: Int = 0,
    /** The cursor returned with page 1: RN keeps `pageParams.slice(0, 1)` on a refresh. */
    val firstPageCursor: String? = null,
) {
    val items: List<FeedViewPost> get() = pages.flatten()
}

/**
 * Port of the RN query behaviour for `PostFeed` (src/state/queries/post-feed.ts, src/state/queries/util.ts):
 *
 * - One load job at a time. A refresh cancels a running next-page load and starts a new generation, so a page
 *   from an older generation is never appended (review A7).
 * - `fetchNextPage` does nothing while fetching, without a next page, or while the query is in error (RN `onEndReached`).
 * - Auto-pagination: while the selected (filtered) item count is below [wantedCount] and a next page exists, load
 *   another page, at most [maxAttempts] times, and stop when a cursor repeats.
 * - [fetch] returns [HomeFeedPage.fallbackMarker] = true on the page where Following ran out (`home.ts`).
 */
class FeedPager(
    private val scope: CoroutineScope,
    private val fetch: suspend (cursor: String?) -> FeedPage,
    /** Number of items the feed would show after tuning, for auto-pagination. */
    private val selectedCount: (List<FeedViewPost>) -> Int,
    private val wantedCount: Int = MIN_POSTS,
    private val maxAttempts: Int = MAX_ATTEMPTS,
) {
    private val _state = MutableStateFlow(FeedPagerState())
    val state: StateFlow<FeedPagerState> = _state.asStateFlow()
    private var job: Job? = null

    /**
     * First load, pull to refresh and soft reset. RN `truncateAndInvalidate` (src/state/queries/util.ts:151-165)
     * first keeps only page 1 and its page param, then refetches. If the refetch fails, the query keeps page 1 and is
     * in error, so `PostFeed` shows page 1, the load-more retry row, and no fallback marker from later pages.
     */
    fun refresh(isPullToRefresh: Boolean = false) {
        job?.cancel()
        val generation = _state.value.generation + 1
        _state.update {
            val truncated = it.pages.isNotEmpty()
            it.copy(
                pages = it.pages.take(1),
                cursor = if (truncated) it.firstPageCursor else it.cursor,
                hasNextPage = if (truncated) it.firstPageCursor != null else it.hasNextPage,
                isFetching = true,
                isFetchingNextPage = false,
                isRefreshing = isPullToRefresh,
                generation = generation,
            )
        }
        job = scope.launch {
            runCatchingCancellable { fetch(null) }
                .onSuccess { page ->
                    if (_state.value.generation != generation) return@onSuccess
                    _state.value = FeedPagerState(
                        pages = listOf(page.items),
                        cursor = page.cursor,
                        hasNextPage = page.cursor != null,
                        isFetched = true,
                        generation = generation,
                        firstPageCursor = page.cursor,
                    )
                    autoPaginate(generation)
                }
                .onFailure { e ->
                    if (_state.value.generation != generation) return@onFailure
                    _state.update { it.copy(isFetched = true, isFetching = false, isRefreshing = false, error = e) }
                }
        }
    }

    /** RN `fetchNextPage` from `onEndReached`; also the load-more retry button. */
    fun fetchNextPage(fromRetry: Boolean = false) {
        val s = _state.value
        if (s.isFetching || !s.hasNextPage || (s.error != null && !fromRetry)) return
        val generation = s.generation
        _state.update { it.copy(isFetching = true, isFetchingNextPage = true, error = null) }
        job = scope.launch {
            loadNext(generation)
            autoPaginate(generation)
        }
    }

    /** Result used by tests and by the end-reached event: true when a next-page load started. */
    fun canFetchNextPage(): Boolean = _state.value.let { !it.isFetching && it.hasNextPage && it.error == null }

    private suspend fun loadNext(generation: Int): Boolean {
        val cursor = _state.value.cursor ?: return false
        var ok = false
        runCatchingCancellable { fetch(cursor) }
            .onSuccess { page ->
                if (_state.value.generation != generation) return@onSuccess
                val repeated = page.cursor != null && page.cursor == cursor
                _state.update {
                    it.copy(
                        pages = it.pages + listOf(page.items),
                        cursor = page.cursor,
                        hasNextPage = page.cursor != null && !repeated,
                        isFetching = false,
                        isFetchingNextPage = false,
                    )
                }
                ok = true
            }
            .onFailure { e ->
                if (_state.value.generation != generation) return@onFailure
                _state.update { it.copy(isFetching = false, isFetchingNextPage = false, error = e) }
            }
        return ok
    }

    private suspend fun autoPaginate(generation: Int) {
        var attempts = 0
        while (true) {
            val s = _state.value
            if (s.generation != generation || s.error != null || !s.hasNextPage) break
            if (selectedCount(s.items) >= wantedCount || attempts >= maxAttempts) break
            attempts++
            _state.update { it.copy(isFetching = true, isFetchingNextPage = true) }
            if (!loadNext(generation)) break
        }
    }

    companion object {
        const val MIN_POSTS = 30
        const val MAX_ATTEMPTS = 5
    }
}

/** One fetched page. */
data class FeedPage(val items: List<FeedViewPost>, val cursor: String?)

/**
 * `HomeFeedAPI` (src/lib/api/feed/home.ts) for the Following feed pinned first. When the Following cursor ends, the
 * page gets the fallback marker and the feed switches to Discover. In `__DEV__` builds RN does not fetch Discover,
 * which is what the RN e2e baseline shows; this port matches that and does not fetch the production Discover feed.
 */
class HomeFeedSource(private val fetchFollowing: suspend (cursor: String?) -> FeedResponse) {
    private var usingDiscover = false

    suspend fun fetch(cursor: String?): FeedPage {
        if (cursor == null) usingDiscover = false
        if (usingDiscover) return FeedPage(emptyList(), null)
        val response = fetchFollowing(cursor)
        return if (response.cursor == null || response.feed.isEmpty() && cursor != null) {
            usingDiscover = true
            FeedPage(response.feed + FALLBACK_MARKER, null)
        } else {
            FeedPage(response.feed, response.cursor)
        }
    }

    companion object {
        val FALLBACK_MARKER: FeedViewPost = FeedViewPost(
            post = io.nativestarter.showcase.bluesky.core.network.PostView(
                uri = FALLBACK_MARKER_URI,
                cid = "",
                author = io.nativestarter.showcase.bluesky.core.network.ProfileViewBasic("", ""),
                record = io.nativestarter.showcase.bluesky.core.network.PostRecord(),
                indexedAt = "",
            ),
        )
    }
}
