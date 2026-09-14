package io.nativestarter.showcase.bluesky.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import io.nativestarter.showcase.bluesky.core.network.BskyRepository
import io.nativestarter.showcase.bluesky.core.network.PinnedFeed
import io.nativestarter.showcase.bluesky.core.network.PostInteractions
import io.nativestarter.showcase.bluesky.core.network.PostView
import io.nativestarter.showcase.bluesky.core.network.withShadow

data class FeedUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val feedTabs: List<String> = listOf("Following", HomeTabs.FEEDS_LINK),
    val selectedTab: Int = 0,
    val slices: List<FeedSlice> = emptyList(),
    /** The timeline has no next page: RN shows the Discover fallback header and FollowingEndOfFeed. */
    val reachedEnd: Boolean = false,
    val error: String? = null,
)

/** CP-01: the "Following" timeline. */
class FeedViewModel(
    private val repository: BskyRepository,
    private val interactions: PostInteractions,
) : ViewModel() {
    private val raw = MutableStateFlow(FeedUiState())

    val state: StateFlow<FeedUiState> = combine(raw, interactions.shadows) { s, shadows ->
        s.copy(slices = s.slices.map { slice -> slice.copy(rows = slice.rows.map { it.copy(post = it.post.withShadow(shadows)) }) })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FeedUiState())

    init {
        load(refresh = false)
    }

    fun refresh() = load(refresh = true)

    fun selectTab(index: Int) = raw.update { it.copy(selectedTab = index) }

    private fun load(refresh: Boolean) {
        raw.update { it.copy(refreshing = refresh, error = null) }
        viewModelScope.launch {
            val tabs = async { runCatching { HomeTabs.from(repository.pinnedFeeds()) }.getOrNull() }
            runCatching { loadPages { cursor -> repository.timeline(cursor) } }
                .onSuccess { loaded ->
                    if (refresh) interactions.reset()
                    raw.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            slices = FeedSlices.build(loaded.items),
                            reachedEnd = loaded.reachedEnd,
                            feedTabs = tabs.await() ?: it.feedTabs,
                        )
                    }
                }
                .onFailure { e -> raw.update { it.copy(loading = false, refreshing = false, error = e.message) } }
        }
    }

    fun toggleLike(post: PostView) {
        viewModelScope.launch { interactions.toggleLike(post) }
    }

    fun toggleRepost(post: PostView) {
        viewModelScope.launch { interactions.toggleRepost(post) }
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
