package io.nativestarter.showcase.bluesky.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nativestarter.showcase.bluesky.core.analytics.Analytics
import io.nativestarter.showcase.bluesky.core.analytics.InteractionContext
import io.nativestarter.showcase.bluesky.core.analytics.NoopAnalytics
import io.nativestarter.showcase.bluesky.core.common.ErrorText
import io.nativestarter.showcase.bluesky.core.common.runCatchingCancellable
import io.nativestarter.showcase.bluesky.core.network.BskyRepository
import io.nativestarter.showcase.bluesky.core.network.Facet
import io.nativestarter.showcase.bluesky.core.network.PostCache
import io.nativestarter.showcase.bluesky.core.network.PostInteractions
import io.nativestarter.showcase.bluesky.core.network.PostView
import io.nativestarter.showcase.bluesky.core.network.ProfileViewDetailed
import io.nativestarter.showcase.bluesky.core.network.StrongRef
import io.nativestarter.showcase.bluesky.core.network.XrpcException
import io.nativestarter.showcase.bluesky.designsystem.text.RichTextModel
import io.nativestarter.showcase.bluesky.feature.feed.AuthorFeedFilter
import io.nativestarter.showcase.bluesky.feature.feed.FeedPage
import io.nativestarter.showcase.bluesky.feature.feed.FeedPager
import io.nativestarter.showcase.bluesky.feature.feed.FeedTuner
import io.nativestarter.showcase.bluesky.feature.feed.FeedUiState
import io.nativestarter.showcase.bluesky.feature.feed.PostFeedRow
import io.nativestarter.showcase.bluesky.feature.feed.PostFeedRows
import io.nativestarter.showcase.bluesky.feature.feed.PostRefs
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** RN own-profile section order (src/view/screens/Profile.tsx `sectionTitles` for a non-labeler, isMe). */
val PROFILE_TABS = listOf("Posts", "Replies", "Media", "Videos", "Likes", "Feeds", "Starter Packs")

/** RN profile `ErrorScreen` copy (src/view/screens/Profile.tsx). */
data class ProfileErrorCopy(val title: String, val message: String)

data class ProfileUiState(
    val profile: ProfileViewDetailed? = null,
    /** RN `isProfilePending`: `ProfileHeaderLoading` until the first profile response. */
    val isProfileLoading: Boolean = true,
    val profileError: ProfileErrorCopy? = null,
    val selectedTab: Int = 0,
    /**
     * Facets detected in the description. RN runs `detectFacets` client-side (`useRichText`), so the text
     * renders plain first and gains its links, mentions and tags when the AppView has resolved them (A8).
     */
    val descriptionFacets: List<Facet> = emptyList(),
    /** The Posts section: RN `PostFeed` for `author|<did>|posts_and_author_threads`, with the Following show rules. */
    val feed: FeedUiState = FeedUiState(),
) {
    /** RN `PostFeedErrorMessage` `KnownError.Block`: "Posts hidden" instead of the error banner. */
    val isPostsHidden: Boolean
        get() = (feed.error as? XrpcException)?.error.let { it == "BlockedActor" || it == "BlockedByActor" }
}

/**
 * CP-03: RN `ProfileScreen` (src/view/screens/Profile.tsx) with the Posts `ProfileFeedSection`.
 * - The profile loads first; the Posts feed starts once it has loaded, as RN renders the section only then.
 * - The feed uses the RN author API filter (`posts_and_author_threads`), the author tuner and the `PostFeed` rows.
 * - Events: `post:view` with the author feed descriptor, `feed:refresh` (pull to refresh), `feed:endReached`,
 *   `post:clickthroughItem`, `post:clickthroughAuthor` and `post:clickReply` without a feed descriptor.
 */
class ProfileViewModel(
    private val actorDid: String,
    private val repository: BskyRepository,
    private val interactions: PostInteractions,
    private val analytics: Analytics = NoopAnalytics,
    private val postCache: PostCache = PostCache(),
    /** RN profile query data, which the own-profile consumers (composer avatar, `likerClout`) read. */
    private val onProfileLoaded: (ProfileViewDetailed) -> Unit = {},
) : ViewModel() {
    private data class ProfileLoad(
        val profile: ProfileViewDetailed? = null,
        val isLoading: Boolean = true,
        val error: ProfileErrorCopy? = null,
    )

    val feedDescriptor = "author|$actorDid|posts_and_author_threads"

    private val profileLoad = MutableStateFlow(ProfileLoad())
    private val selectedTab = MutableStateFlow(0)
    private val descriptionFacets = MutableStateFlow<List<Facet>>(emptyList())
    private var profileJob: Job? = null
    private var facetsJob: Job? = null
    private var facetsText: String? = null
    private var feedStarted = false
    private val seenUris = HashSet<String>()

    /**
     * The author tuner output depends on the loaded pages only, but `combine` re-runs on every like and repost
     * shadow emission. Memoising it keeps one tap from re-tuning the whole profile feed (review B5).
     */
    private var tunedPages: List<List<io.nativestarter.showcase.bluesky.core.network.FeedViewPost>>? = null
    private var tunedRows: List<PostFeedRow> = emptyList()
    private var tunedSliceCount = 0

    private fun tunedRowsFor(
        pages: List<List<io.nativestarter.showcase.bluesky.core.network.FeedViewPost>>,
    ): List<PostFeedRow> {
        if (tunedPages !== pages) {
            val slices = FeedTuner.author().tune(pages.flatten())
            tunedRows = PostFeedRows.build(slices, withComposerPrompt = false)
            tunedSliceCount = slices.size
            tunedPages = pages
        }
        return tunedRows
    }

    private val pager = FeedPager(
        scope = viewModelScope,
        fetch = { cursor ->
            val response = repository.authorFeed(actorDid, cursor)
            postCache.put(response.feed.flatMap { listOfNotNull(it.post, PostRefs.postView(it.reply?.parent), PostRefs.postView(it.reply?.root)) })
            // RN `AuthorFeedAPI.fetch` filters each page before it reaches the tuner.
            FeedPage(AuthorFeedFilter.filter(response.feed, actorDid), response.cursor)
        },
        selectedCount = { items -> FeedTuner.author().tune(items).sumOf { it.items.size } },
    )

    val state: StateFlow<ProfileUiState> = combine(
        profileLoad,
        selectedTab,
        pager.state,
        interactions.shadows,
        descriptionFacets,
    ) { p, tab, f, shadows, facets ->
        val rows = tunedRowsFor(f.pages).map { row ->
            if (row is PostFeedRow.SliceItemRow) row.copy(post = interactions.apply(row.post, shadows)) else row
        }
        ProfileUiState(
            profile = p.profile,
            isProfileLoading = p.isLoading,
            profileError = p.error,
            selectedTab = tab,
            descriptionFacets = facets,
            feed = FeedUiState(
                rows = rows,
                isFetched = f.isFetched,
                isFetching = f.isFetching,
                isRefreshing = f.isRefreshing,
                isFetchingNextPage = f.isFetchingNextPage,
                hasNextPage = f.hasNextPage,
                isEmpty = !f.isFetching && tunedSliceCount == 0,
                error = f.error,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ProfileUiState())

    init {
        loadProfile()
    }

    /** `ErrorScreen` "Try again": RN `refetchProfile()`. The error stays until a request succeeds. */
    fun retryProfile() = loadProfile()

    private fun loadProfile() {
        profileJob?.cancel()
        profileLoad.update { it.copy(isLoading = it.profile == null && it.error == null) }
        profileJob = viewModelScope.launch {
            runCatchingCancellable { repository.profile(actorDid) }
                .onSuccess { profile ->
                    profileLoad.value = ProfileLoad(profile = profile, isLoading = false)
                    onProfileLoaded(profile)
                    resolveDescriptionFacets(profile.description.orEmpty())
                    if (!feedStarted) {
                        feedStarted = true
                        pager.refresh()
                    }
                }
                // The own profile is loaded by DID, so the RN handle-resolution error ("Oops!") cannot occur.
                .onFailure { e -> profileLoad.update { it.copy(isLoading = false, error = ProfileErrorCopy("Not Found", ErrorText.clean(e))) } }
        }
    }

    /**
     * RN `useRichText(profile.description)` (src/view/screens/Profile.tsx:209, ProfileHeaderStandard.tsx:145-156).
     * A profile description carries no facets of its own, so RN detects them client-side and resolves each
     * mention's handle through the AppView. The raw text renders while that runs (`resolvedRT ?? rawRT`), so the
     * facets are published only when they are ready (review A8).
     */
    private fun resolveDescriptionFacets(text: String) {
        if (text == facetsText) return
        facetsText = text
        facetsJob?.cancel()
        descriptionFacets.value = emptyList()
        if (text.isBlank()) return
        facetsJob = viewModelScope.launch {
            val resolved = RichTextModel.resolveFacets(text) { handle ->
                runCatchingCancellable { repository.resolveHandle(handle) }.getOrNull()
            }
            descriptionFacets.value = resolved
        }
    }

    fun selectTab(index: Int) {
        selectedTab.value = index
    }

    /** Pull to refresh: RN `refreshFeed` logs `feed:refresh` and truncates the query. */
    fun refresh() {
        analytics.metric("feed:refresh", linkedMapOf("feedType" to FEED_TYPE, "feedUrl" to feedDescriptor, "reason" to "pull-to-refresh"))
        pager.refresh(isPullToRefresh = true)
    }

    /** Soft reset or a press on the selected tab: RN `onScrollToTop` truncates and invalidates without an event. */
    fun softReset() = pager.refresh()

    /** `PostFeedErrorMessage` retry: RN `refetch()`. */
    fun retry() = pager.refresh()

    fun retryLoadMore() = pager.fetchNextPage(fromRetry = true)

    fun onEndReached() {
        if (!pager.canFetchNextPage()) return
        analytics.metric(
            "feed:endReached",
            linkedMapOf("feedType" to FEED_TYPE, "feedUrl" to feedDescriptor, "itemCount" to state.value.feed.rows.size),
        )
        pager.fetchNextPage()
    }

    /** RN `onItemSeen`: `post:view` once per URI for the slice's feed post; `feedDescriptor` falls back to the feed. */
    fun onPostSeen(row: PostFeedRow.SliceItemRow) {
        if (row.post.uri != row.slice.feedPostUri || !seenUris.add(row.post.uri)) return
        val rows = state.value.feed.rows
        val position = io.nativestarter.showcase.bluesky.feature.feed.PostViewPosition.of(rows, row)
        analytics.metric(
            "post:view",
            linkedMapOf(
                "uri" to row.post.uri,
                "authorDid" to row.post.author.did,
                "isReply" to (row.post.record.reply != null),
                "logContext" to CONTEXT.logContext,
                "feedDescriptor" to feedDescriptor,
                "position" to position,
            ),
        )
    }

    fun onPostPressed(post: PostView) = analytics.metric("post:clickthroughItem", clickProps(post))
    fun onAuthorPressed(post: PostView) = analytics.metric("post:clickthroughAuthor", clickProps(post))
    fun onReplyPressed(post: PostView) = analytics.metric("post:clickReply", clickProps(post))

    fun toggleLike(post: PostView, via: StrongRef?) = interactions.toggleLike(post, CONTEXT, via)
    fun toggleRepost(post: PostView, via: StrongRef?) = interactions.toggleRepost(post, CONTEXT, via)

    private fun clickProps(post: PostView) = linkedMapOf<String, Any?>(
        "uri" to post.uri,
        "authorDid" to post.author.did,
        "logContext" to CONTEXT.logContext,
        "feedDescriptor" to CONTEXT.feedDescriptor,
    )

    private companion object {
        const val FEED_TYPE = "author"

        /** The profile feed has no feed feedback source, so PostControls and PostFeedItem send no `feedDescriptor`. */
        val CONTEXT = InteractionContext("FeedItem", null)
    }
}
