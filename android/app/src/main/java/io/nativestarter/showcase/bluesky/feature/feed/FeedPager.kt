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
        fillAttempts = 0
        lastFillCount = -1
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
                }
                .onFailure { e ->
                    if (_state.value.generation != generation) return@onFailure
                    /*
                     * A refetch that fails does not error a feed that still has pages.
                     *
                     * This matches the captured RN screen, not the library. Both are measured, and
                     * they disagree, so the divergence is deliberate and the reason is recorded
                     * here rather than guessed at.
                     *
                     * What the server does. After the reseed the session both apps hold is for a
                     * DID that no longer exists, and the server answers every app the same way:
                     *
                     *   getTimeline    with the old access token    HTTP 500
                     *   refreshSession with the old refresh token   HTTP 400 InvalidRequest
                     *
                     * What the library does. `parity/probes/tanstack-failed-refetch.mjs` drives
                     * the real @tanstack/query-core 5.100.10 that RN builds against, with this
                     * feed's page shape (page 1: five posts and a cursor; page 2: the fallback
                     * marker and none). `truncateAndInvalidate` and then a failing refetch leaves:
                     *
                     *   pages=1 items=5 hasNextPage=true status=error isError=true
                     *
                     * So a failed refetch over existing data IS an error state for TanStack. An
                     * earlier version of this comment claimed the opposite; that claim was wrong.
                     *
                     * What RN draws. The captured `error` home screen shows the cached posts and
                     * the end-of-feed block, with no error row and no load-more retry row. RN
                     * renders the end-of-feed only when `!hasNextPage && !isError`, and skips
                     * `feed:endReached` when `isError` - and the capture logs zero `feed:endReached`
                     * against this port's 44. No single state of the library above produces that
                     * screen, so RN's runtime state on that capture is not yet explained.
                     *
                     * Why this branch stays. Suppressing the error puts the port closer to the
                     * captured screen (4.87% against 5.24% with the retry row). Adopting the
                     * library's `isError` instead would draw a retry row that RN does not draw.
                     * Settling it needs a live capture with both apps instrumented; until then this
                     * is a known divergence, recorded as difference 2 in PARITY.md.
                     *
                     * A first load with nothing to show still errors, which is RN's
                     * `isError && isEmpty` branch.
                     */
                    val hasPages = _state.value.pages.isNotEmpty()
                    _state.update {
                        it.copy(
                            isFetched = true,
                            isFetching = false,
                            isRefreshing = false,
                            error = if (hasPages) it.error else e,
                        )
                    }
                }
        }
    }

    /** RN `fetchNextPage` from `onEndReached`; also the load-more retry button. */
    fun fetchNextPage(fromRetry: Boolean = false) {
        val s = _state.value
        if (s.isFetching || !s.hasNextPage || (s.error != null && !fromRetry)) return
        val generation = s.generation
        _state.update { it.copy(isFetching = true, isFetchingNextPage = true, error = null) }
        job = scope.launch { loadNext(generation) }
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
                // The same deliberate divergence as the failed refresh above, for the same
                // reason: it draws the captured RN screen, not the library's `isError`.
                val hasPages = _state.value.pages.isNotEmpty()
                _state.update {
                    it.copy(isFetching = false, isFetchingNextPage = false, error = if (hasPages) it.error else e)
                }
            }
        return ok
    }

    /**
     * One pass of RN's page fill (`src/state/queries/util.ts`), driven by the screen.
     *
     * This used to run inline, immediately after the first page landed, and that is what stopped
     * `feed:endReached` from ever being logged. RN's fill is an effect: the list renders the first
     * page, the layout pass reports that the end is already visible, and `onEndReached` logs the
     * event while a next page still exists. Only then does the fill effect run. Running the fill
     * inside the fetch instead consumed the next page before the list had laid out even once, so
     * `hasNextPage` was already false by the time anything could ask.
     *
     * Measured on the seeded author feed: page 1 returns 1 item and a cursor, page 2 returns 0 items
     * and no cursor. RN logs `feed:endReached` with `itemCount: 1`; the port logged nothing.
     */
    fun fillIfNeeded() {
        val s = _state.value
        if (s.isFetching || s.error != null || !s.hasNextPage || !s.isFetched) return
        val selected = selectedCount(s.items)
        if (selected >= wantedCount) return
        // RN keeps its attempt count in a ref across renders and clears it whenever the item count
        // moves (`attemptCount`, src/state/queries/util.ts). Counting per call instead would let the
        // screen, which asks after every render, fetch without limit against a feed that keeps
        // returning a cursor and no items.
        if (selected != lastFillCount) {
            fillAttempts = 0
            lastFillCount = selected
        }
        if (fillAttempts >= maxAttempts) return
        fillAttempts++
        val generation = s.generation
        _state.update { it.copy(isFetching = true, isFetchingNextPage = true) }
        job = scope.launch { loadNext(generation) }
    }

    private var fillAttempts = 0
    private var lastFillCount = -1

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
