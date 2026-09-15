package io.nativestarter.showcase.bluesky.feature.profile

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
import io.nativestarter.showcase.bluesky.core.network.PostInteractions
import io.nativestarter.showcase.bluesky.core.network.PostView
import io.nativestarter.showcase.bluesky.core.network.ProfileViewDetailed
import io.nativestarter.showcase.bluesky.feature.feed.FeedSlice
import io.nativestarter.showcase.bluesky.feature.feed.FeedSlices
import io.nativestarter.showcase.bluesky.feature.feed.loadPages

/** RN own-profile section order (src/view/screens/Profile.tsx `sectionTitles` for a non-labeler, isMe). */
val PROFILE_TABS = listOf("Posts", "Replies", "Media", "Videos", "Likes", "Feeds", "Starter Packs")

data class ProfileUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val profile: ProfileViewDetailed? = null,
    val posts: List<FeedSlice> = emptyList(),
    val selectedTab: Int = 0,
    /** No next page: RN ProfileEndOfFeed ("End of feed") is shown. */
    val reachedEnd: Boolean = false,
    val error: String? = null,
)

class ProfileViewModel(
    private val actor: String,
    private val repository: BskyRepository,
    private val interactions: PostInteractions,
) : ViewModel() {
    private val raw = MutableStateFlow(ProfileUiState())

    val state: StateFlow<ProfileUiState> = combine(raw, interactions.shadows) { s, shadows ->
        s.copy(posts = s.posts.map { slice -> slice.copy(rows = slice.rows.map { it.copy(post = interactions.apply(it.post, shadows)) }) })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ProfileUiState())

    init {
        load(refresh = false)
    }

    fun refresh() = load(refresh = true)

    fun selectTab(index: Int) = raw.update { it.copy(selectedTab = index) }

    private fun load(refresh: Boolean) {
        raw.update { it.copy(refreshing = refresh, error = null) }
        viewModelScope.launch {
            runCatching {
                val profile = async { repository.profile(actor) }
                val feed = async { loadPages { cursor -> repository.authorFeed(actor, cursor) } }
                profile.await() to feed.await()
            }.onSuccess { (profile, feed) ->
                raw.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        profile = profile,
                        posts = FeedSlices.build(feed.items),
                        reachedEnd = feed.reachedEnd,
                    )
                }
            }.onFailure { e ->
                raw.update { it.copy(loading = false, refreshing = false, error = e.message) }
            }
        }
    }

    fun toggleLike(post: PostView) = interactions.toggleLike(post, CONTEXT)

    fun toggleRepost(post: PostView) = interactions.toggleRepost(post, CONTEXT)

    private companion object {
        /** Profile feeds have no FeedFeedbackProvider in RN, so `feedDescriptor` is undefined. */
        val CONTEXT = io.nativestarter.showcase.bluesky.core.analytics.InteractionContext("FeedItem", null)
    }
}
