package io.nativestarter.showcase.bluesky.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Optimistic like/repost state shared by every screen, the native equivalent of RN `usePostShadow`
 * (src/state/cache/post-shadow.ts) plus `usePostLikeMutationQueue` / `usePostRepostMutationQueue`.
 *
 * The UI flips immediately; the network call follows; on failure the shadow reverts to its previous value.
 */
data class PostShadow(
    val likeUri: String?,
    val likeCount: Int,
    val repostUri: String?,
    val repostCount: Int,
)

const val PENDING_URI = "pending"

class PostInteractions(private val repository: BskyRepository) {
    private val _shadows = MutableStateFlow<Map<String, PostShadow>>(emptyMap())
    val shadows: StateFlow<Map<String, PostShadow>> = _shadows.asStateFlow()

    /** Drops shadows so fresh server data wins, as RN does after a refetch. */
    fun reset() = _shadows.update { emptyMap() }

    suspend fun toggleLike(post: PostView) {
        val before = shadowOf(post)
        if (before.likeUri != null) {
            set(post.uri, before.copy(likeUri = null, likeCount = (before.likeCount - 1).coerceAtLeast(0)))
            runCatching { if (before.likeUri != PENDING_URI) repository.deleteLike(before.likeUri) }
                .onFailure { set(post.uri, before) }
        } else {
            set(post.uri, before.copy(likeUri = PENDING_URI, likeCount = before.likeCount + 1))
            runCatching { repository.like(StrongRef(post.uri, post.cid)) }
                .onSuccess { uri -> _shadows.update { it + (post.uri to it.getValue(post.uri).copy(likeUri = uri)) } }
                .onFailure { set(post.uri, before) }
        }
    }

    suspend fun toggleRepost(post: PostView) {
        val before = shadowOf(post)
        if (before.repostUri != null) {
            set(post.uri, before.copy(repostUri = null, repostCount = (before.repostCount - 1).coerceAtLeast(0)))
            runCatching { if (before.repostUri != PENDING_URI) repository.deleteRepost(before.repostUri) }
                .onFailure { set(post.uri, before) }
        } else {
            set(post.uri, before.copy(repostUri = PENDING_URI, repostCount = before.repostCount + 1))
            runCatching { repository.repost(StrongRef(post.uri, post.cid)) }
                .onSuccess { uri -> _shadows.update { it + (post.uri to it.getValue(post.uri).copy(repostUri = uri)) } }
                .onFailure { set(post.uri, before) }
        }
    }

    private fun shadowOf(post: PostView): PostShadow = _shadows.value[post.uri]
        ?: PostShadow(post.viewer.like, post.likeCount, post.viewer.repost, post.repostCount)

    private fun set(uri: String, shadow: PostShadow) = _shadows.update { it + (uri to shadow) }
}

/** Applies a shadow on top of server data. */
fun PostView.withShadow(shadows: Map<String, PostShadow>): PostView {
    val s = shadows[uri] ?: return this
    return copy(
        likeCount = s.likeCount,
        repostCount = s.repostCount,
        viewer = viewer.copy(like = s.likeUri, repost = s.repostUri),
    )
}
