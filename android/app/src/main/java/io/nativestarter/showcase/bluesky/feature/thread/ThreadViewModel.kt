package io.nativestarter.showcase.bluesky.feature.thread

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nativestarter.showcase.bluesky.core.analytics.Analytics
import io.nativestarter.showcase.bluesky.core.analytics.InteractionContext
import io.nativestarter.showcase.bluesky.core.analytics.NoopAnalytics
import io.nativestarter.showcase.bluesky.core.common.ErrorText
import io.nativestarter.showcase.bluesky.core.common.runCatchingCancellable
import io.nativestarter.showcase.bluesky.core.network.BskyRepository
import io.nativestarter.showcase.bluesky.core.network.PostCache
import io.nativestarter.showcase.bluesky.core.network.PostInteractions
import io.nativestarter.showcase.bluesky.core.network.PostSource
import io.nativestarter.showcase.bluesky.core.network.PostView
import io.nativestarter.showcase.bluesky.core.network.PreferencesCache
import io.nativestarter.showcase.bluesky.core.network.ThreadViewPrefs
import io.nativestarter.showcase.bluesky.core.network.UserPreferences
import io.nativestarter.showcase.bluesky.core.network.ThreadV2Response
import io.nativestarter.showcase.bluesky.core.network.XrpcException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** RN `ThreadError` copy (src/screens/PostThread/components/ThreadError.tsx). */
data class ThreadErrorCopy(val title: String, val message: String)

/** What RN `PostThread` renders. [items] are the RN `deferredSlices`; [anchor] and [hasParents] come from all items. */
data class ThreadUiState(
    val items: List<ThreadItem> = emptyList(),
    val anchor: PostView? = null,
    val hasParents: Boolean = false,
    val error: ThreadErrorCopy? = null,
    val isFetching: Boolean = true,
    val isPlaceholderData: Boolean = true,
    val deferParents: Boolean = true,
    val isTombstoneView: Boolean = false,
    val sort: String = "top",
    val totalParentCount: Int = 0,
    val totalChildrenCount: Int = 0,
) {
    /** RN `canReply = !anchor?.value.post?.viewer?.replyDisabled`: true while the anchor is unknown. */
    val canReply: Boolean get() = anchor?.viewer?.replyDisabled != true
}

/**
 * CP-02: RN `usePostThread` (src/state/queries/usePostThread/index.ts) and `PostThread` (src/screens/PostThread).
 * - Waits for the thread preferences, then calls `getPostThreadV2` with the preferred sort.
 * - Shows the anchor from [postCache] with skeletons until the response arrives (`placeholderData`).
 * - "Show more replies" reveals moderated replies and loads `getPostThreadOtherV2` when the server has more.
 * - Events: `post:view` for the anchor (`Post`) and once per other post (`PostThreadItem`),
 *   `thread:click:showOtherReplies`, `post:clickReply` and `post:clickthroughAuthor`.
 */
class ThreadViewModel(
    private val anchorUri: String,
    private val repository: BskyRepository,
    private val interactions: PostInteractions,
    private val analytics: Analytics = NoopAnalytics,
    private val userDid: String? = null,
    private val postCache: PostCache = PostCache(),
    /** RN `useUnstablePostSource(uri)`, consumed once when the screen opens. */
    private val source: PostSource? = null,
    /** RN `usePreferencesQuery` data for the account, shared with the feed. */
    private val preferencesCache: PreferencesCache = PreferencesCache(),
) : ViewModel() {
    private data class Query(
        /** Null until the thread preferences load (RN `isThreadPreferencesLoaded`). */
        val sort: String? = null,
        val data: ThreadV2Response? = null,
        /** RN `ctx.meta.hasOtherReplies`: once true it stays true across refetches. */
        val hasOtherReplies: Boolean = false,
        val error: Throwable? = null,
        val isFetching: Boolean = true,
    )

    private sealed interface OtherReplies {
        data object Idle : OtherReplies
        data object Failed : OtherReplies
        data class Loaded(val items: List<ThreadItem>) : OtherReplies
    }

    private val query = MutableStateFlow(Query())
    private val otherItemsVisible = MutableStateFlow(false)
    private val otherReplies = MutableStateFlow<OtherReplies>(OtherReplies.Idle)
    private val window = MutableStateFlow(ThreadWindow())
    private val placeholder: PostView? = postCache.get(anchorUri)
    private var fetchJob: Job? = null
    private var seenAnchorUri: String? = null
    private val seenItemUris = HashSet<String>()

    /** PostControls read `logContext` and the feed descriptor of the feed the thread was opened from. */
    private val context = InteractionContext(LOG_CONTEXT, source?.feedDescriptor)

    /**
     * RN `useThreadPreferences` `useState(serverPrefs)`: the **thread view** preferences the screen had when it
     * opened. RN watches `preferences.threadViewPrefs` only, so a change to an unrelated preference is not a
     * thread preference change (review A13).
     */
    private val threadPrefsAtOpen: ThreadViewPrefs? = preferencesCache.value?.threadView
    private var loggedPreferencesLoad = false

    val state: StateFlow<ThreadUiState> = combine(query, otherItemsVisible, otherReplies, window, interactions.shadows) { q, visible, other, w, shadows ->
        val isPlaceholderData = q.data == null && q.error == null
        val api = if (isPlaceholderData) {
            listOfNotNull(placeholder?.let(ApiThreadItem::placeholder))
        } else {
            ThreadTraversal.parse(q.data?.thread.orEmpty())
        }
        val annotated = ThreadTraversal.sortAndAnnotate(api, userDid)
        val serverOther = when {
            !(q.hasOtherReplies && visible) -> emptyList()
            other is OtherReplies.Loaded -> other.items
            other is OtherReplies.Failed -> emptyList()
            else -> ThreadTraversal.otherReplySkeletons()
        }
        val items = ThreadTraversal.buildThread(
            threadItems = annotated.threadItems,
            otherThreadItems = annotated.otherThreadItems,
            serverOtherThreadItems = serverOther,
            isLoading = isPlaceholderData,
            otherItemsVisible = visible,
            hasOtherThreadItems = q.hasOtherReplies,
        ).map { if (it is ThreadItem.Post) it.copy(post = interactions.apply(it.post, shadows)) else it }
        val (anchor, hasParents) = ThreadTraversal.anchorOf(items)
        val slices = ThreadTraversal.slices(items, w)
        ThreadUiState(
            items = slices.items,
            anchor = anchor?.post,
            hasParents = hasParents,
            error = q.error?.let(::errorCopy),
            isFetching = q.isFetching,
            isPlaceholderData = isPlaceholderData,
            deferParents = w.deferParents,
            isTombstoneView = ThreadTraversal.isTombstoneView(slices.items),
            sort = q.sort ?: UNKNOWN_SORT,
            totalParentCount = slices.totalParentCount,
            totalChildrenCount = slices.totalChildrenCount,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ThreadUiState())

    init {
        viewModelScope.launch {
            state.collect { s -> s.anchor?.let(::logAnchorView) }
        }
        // RN `usePreferencesQuery` is one cache entry per account, so a thread that is already open sees a later
        // change to the thread view preferences as well (review A13). A StateFlow replays the current value, so
        // the preferences the screen opened with arrive first and log nothing.
        viewModelScope.launch {
            preferencesCache.state.collect { prefs -> prefs?.let(::onThreadPreferences) }
        }
        fetch()
    }

    /** RN `thread.actions.refetch` from `ThreadError`. The error stays until a request succeeds. */
    fun retry() = fetch()

    private fun fetch() {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            query.update { it.copy(isFetching = true) }
            val sort = query.value.sort ?: runCatchingCancellable { repository.preferences() }
                .onSuccess(preferencesCache::put)
                .map { it.threadSort }
                .getOrDefault(UNKNOWN_SORT)
                .also { loaded -> query.update { it.copy(sort = loaded) } }
            runCatchingCancellable { repository.postThreadV2(anchorUri, sort) }
                .onSuccess { data ->
                    postCache.put(ThreadTraversal.postsOf(ThreadTraversal.parse(data.thread)))
                    query.update {
                        it.copy(data = data, hasOtherReplies = it.hasOtherReplies || data.hasOtherReplies, error = null, isFetching = false)
                    }
                }
                .onFailure { e -> query.update { it.copy(error = e, isFetching = false) } }
        }
    }

    /**
     * RN `useThreadPreferences` (src/state/queries/preferences/useThreadPreferences.ts:50-71): `thread:preferences:load`
     * once per screen, when `preferences.threadViewPrefs` arrives and differs from the value the screen had at
     * mount (`prevServerPrefs !== serverPrefs`). `usePreferencesQuery` uses `replaceEqualDeep`, so preferences
     * already loaded by the feed, and an equal reload, log nothing. A failed load logs nothing (review N22).
     *
     * Only `threadViewPrefs` is compared: an unrelated preference change is not a thread preference change, and a
     * `threadViewPrefs` change that arrives after the screen opened still logs (review A13).
     *
     * Props: the raw `sort` (with the "hotness" default) and `view` from `lab_treeViewEnabled`.
     */
    private fun onThreadPreferences(loaded: UserPreferences) {
        val current = loaded.threadView
        if (loggedPreferencesLoad || current == threadPrefsAtOpen) return
        loggedPreferencesLoad = true
        analytics.metric(
            "thread:preferences:load",
            linkedMapOf("sort" to current.sortRaw, "view" to if (current.treeViewEnabled) "tree" else "linear"),
        )
    }

    /** `ThreadItemShowOtherReplies`: `onPress()` then `thread:click:showOtherReplies`. */
    fun showOtherReplies() {
        otherItemsVisible.value = true
        analytics.metric("thread:click:showOtherReplies")
        if (query.value.hasOtherReplies && otherReplies.value !is OtherReplies.Loaded) loadOtherReplies()
    }

    private fun loadOtherReplies() {
        otherReplies.value = OtherReplies.Idle
        viewModelScope.launch {
            runCatchingCancellable { repository.postThreadOtherV2(anchorUri) }
                .onSuccess { response ->
                    val api = ThreadTraversal.parse(response.thread)
                    postCache.put(ThreadTraversal.postsOf(api))
                    otherReplies.value = OtherReplies.Loaded(
                        ThreadTraversal.sortAndAnnotate(api, userDid, skipModerationHandling = true).threadItems,
                    )
                }
                // RN: "these are optional additional replies", so a failure shows nothing.
                .onFailure { otherReplies.value = OtherReplies.Failed }
        }
    }

    /** The anchor's `onLayout`: parents may now be prepended above it. */
    fun onAnchorLaidOut() = window.update { it.copy(deferParents = false) }

    fun onStartReached() {
        val s = state.value
        if (s.isFetching || s.deferParents) return
        if (window.value.maxParentCount >= s.totalParentCount) return
        window.update { it.copy(maxParentCount = it.maxParentCount + ThreadTraversal.PARENT_CHUNK_SIZE) }
    }

    fun onEndReached() {
        val s = state.value
        if (s.isFetching || s.deferParents) return
        if (window.value.maxChildrenCount >= s.totalChildrenCount) return
        window.update { it.copy(maxChildrenCount = it.maxChildrenCount + ThreadTraversal.CHILDREN_CHUNK_SIZE) }
    }

    /** RN `onItemSeen` with `usePostViewTracking('PostThreadItem')`: parents and replies, once per URI. */
    fun onItemSeen(item: ThreadItem.Post) {
        if (item.depth == 0 || !seenItemUris.add(item.post.uri)) return
        analytics.metric(
            "post:view",
            linkedMapOf(
                "uri" to item.post.uri,
                "authorDid" to item.post.author.did,
                "isReply" to (item.post.record.reply != null),
                "logContext" to LOG_CONTEXT,
            ),
        )
    }

    private fun logAnchorView(anchor: PostView) {
        if (anchor.uri == seenAnchorUri) return
        seenAnchorUri = anchor.uri
        analytics.metric(
            "post:view",
            linkedMapOf(
                "uri" to anchor.uri,
                "authorDid" to anchor.author.did,
                "isReply" to (anchor.record.reply != null),
                "logContext" to "Post",
                "feedDescriptor" to source?.feedDescriptor,
            ),
        )
    }

    /** PostControls `post:clickReply`; the composer it opens is outside the slice. */
    fun onReplyPressed(post: PostView) = analytics.metric("post:clickReply", clickProps(post))

    /** `ThreadItemAnchor` `onOpenAuthor`. Other thread items open the author without an event. */
    fun onAnchorAuthorPressed(post: PostView) = analytics.metric("post:clickthroughAuthor", clickProps(post))

    /** The anchor carries the repost `via` of the feed item it was opened from; replies have none. */
    fun toggleLike(post: PostView) = interactions.toggleLike(post, context, viaFor(post))

    fun toggleRepost(post: PostView) = interactions.toggleRepost(post, context, viaFor(post))

    private fun viaFor(post: PostView) = source?.via?.takeIf { post.uri == anchorUri }

    private fun clickProps(post: PostView) = linkedMapOf<String, Any?>(
        "uri" to post.uri,
        "authorDid" to post.author.did,
        "logContext" to LOG_CONTEXT,
        "feedDescriptor" to source?.feedDescriptor,
    )

    companion object {
        const val LOG_CONTEXT = "PostThreadItem"

        /** RN `normalizeSort` default when the preferences cannot be read. */
        private const val UNKNOWN_SORT = "top"

        const val DEFAULT_MESSAGE = "Something went wrong. Please try again in a moment."

        /** RN `ThreadError`: "Post not found" when the error message says so, else "Error loading post". */
        fun errorCopy(error: Throwable): ThreadErrorCopy {
            val raw = (error as? XrpcException)?.serverMessage ?: error.message.orEmpty()
            if (!raw.startsWith("Post not found")) return ThreadErrorCopy("Error loading post", DEFAULT_MESSAGE)
            val clean = ErrorText.cleanForScreen(error)
            return ThreadErrorCopy("Post not found", clean.ifBlank { raw.ifBlank { DEFAULT_MESSAGE } })
        }
    }
}
