package io.nativestarter.showcase.bluesky.core.network

import io.nativestarter.showcase.bluesky.core.analytics.Analytics
import io.nativestarter.showcase.bluesky.core.analytics.InteractionContext
import io.nativestarter.showcase.bluesky.core.analytics.NoopAnalytics
import io.nativestarter.showcase.bluesky.core.analytics.toClout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

const val PENDING_URI = "pending"

/** Server state a shadow was created from. A post whose server state differs is fresh data (review A8, P21). */
data class ShadowBase(val like: String?, val repost: String?, val likeCount: Int, val repostCount: Int) {
    companion object {
        fun of(post: PostView) = ShadowBase(post.viewer.like, post.viewer.repost, post.likeCount, post.repostCount)
    }
}

/** The viewer's like and repost URIs as the UI shows them; `pending` while a create request runs. */
data class PostShadow(val base: ShadowBase, val likeUri: String?, val repostUri: String?)

/**
 * Optimistic like and repost state shared by every screen: RN `usePostShadow` (src/state/cache/post-shadow.ts),
 * `usePostLikeMutationQueue` and `usePostRepostMutationQueue` (src/state/queries/post.ts).
 *
 * - The UI flips at once. One [ToggleQueue] per post and action sends the requests; its last confirmed server state
 *   becomes the shadow when it drains, so a failed request or a failed compensating request is visible (P22).
 * - RN keys shadows by post object, so a refetched post starts clean. Here a shadow applies only while the server
 *   post still matches the state the shadow was created from, or while a request for that post is running.
 * - Analytics fire inside the request, as RN fires them in `mutationFn`.
 */
class PostInteractions(
    private val repository: BskyRepository,
    private val scope: CoroutineScope,
    private val analytics: Analytics = NoopAnalytics,
    /** Followers of the signed-in account (RN `findProfileQueryData`), for `likerClout`. Null before the profile loads. */
    private val ownFollowersCount: () -> Int? = { null },
) {
    private val _shadows = MutableStateFlow<Map<String, PostShadow>>(emptyMap())
    val shadows: StateFlow<Map<String, PostShadow>> = _shadows.asStateFlow()
    private val likeQueues = HashMap<String, ToggleQueue<String?>>()
    private val repostQueues = HashMap<String, ToggleQueue<String?>>()

    fun isPending(uri: String): Boolean = likeQueues[uri]?.isActive == true || repostQueues[uri]?.isActive == true

    /** Merges the shadow into a server post (RN `mergeShadow`). */
    fun apply(post: PostView, shadows: Map<String, PostShadow> = _shadows.value): PostView = post.withShadow(shadows, ::isPending)

    /** [post] is the post as displayed. [via] is `reasonRepost` `{uri, cid}` when the post came through a repost. */
    fun toggleLike(post: PostView, context: InteractionContext, via: StrongRef? = null) {
        val entry = entryFor(post)
        val liked = entry.likeUri != null
        // RN reads `initialState` from the post as shown when the queue is idle: the last confirmed like URI.
        val confirmedBefore = entry.likeUri.takeIf { it != PENDING_URI }
        set(post.uri, entry.copy(likeUri = if (liked) null else PENDING_URI))
        val queue = likeQueues.getOrPut(post.uri) {
            ToggleQueue(
                scope = scope,
                request = { confirmed, isOn ->
                    if (isOn) {
                        analytics.metric("post:like", likeProps(post, context))
                        repository.like(StrongRef(post.uri, post.cid), via)
                    } else {
                        if (confirmed != null) {
                            analytics.metric("post:unlike", basicProps(post, context))
                            repository.deleteLike(confirmed)
                        }
                        null
                    }
                },
                onSettled = { confirmed ->
                    likeQueues.remove(post.uri)
                    _shadows.value[post.uri]?.let { set(post.uri, it.copy(likeUri = confirmed)) }
                },
            )
        }
        queue.toggle(!liked) { confirmedBefore }
    }

    fun toggleRepost(post: PostView, context: InteractionContext, via: StrongRef? = null) {
        val entry = entryFor(post)
        val reposted = entry.repostUri != null
        val confirmedBefore = entry.repostUri.takeIf { it != PENDING_URI }
        set(post.uri, entry.copy(repostUri = if (reposted) null else PENDING_URI))
        val queue = repostQueues.getOrPut(post.uri) {
            ToggleQueue(
                scope = scope,
                request = { confirmed, isOn ->
                    if (isOn) {
                        analytics.metric("post:repost", basicProps(post, context))
                        repository.repost(StrongRef(post.uri, post.cid), via)
                    } else {
                        if (confirmed != null) {
                            analytics.metric("post:unrepost", basicProps(post, context))
                            repository.deleteRepost(confirmed)
                        }
                        null
                    }
                },
                onSettled = { confirmed ->
                    repostQueues.remove(post.uri)
                    _shadows.value[post.uri]?.let { set(post.uri, it.copy(repostUri = confirmed)) }
                },
            )
        }
        queue.toggle(!reposted) { confirmedBefore }
    }

    private fun entryFor(displayed: PostView): PostShadow {
        val existing = _shadows.value[displayed.uri]
        if (existing != null) return existing
        return PostShadow(ShadowBase.of(displayed), displayed.viewer.like, displayed.viewer.repost)
    }

    private fun set(uri: String, shadow: PostShadow) = _shadows.update { it + (uri to shadow) }

    private fun basicProps(post: PostView, context: InteractionContext): Map<String, Any?> = linkedMapOf(
        "uri" to post.uri,
        "authorDid" to post.author.did,
        "logContext" to context.logContext,
        "feedDescriptor" to context.feedDescriptor,
    )

    /** RN `post:like` props (src/state/queries/post.ts). */
    private fun likeProps(post: PostView, context: InteractionContext): Map<String, Any?> {
        val authorViewer = post.author.viewer
        return linkedMapOf(
            "uri" to post.uri,
            "authorDid" to post.author.did,
            "logContext" to context.logContext,
            "doesPosterFollowLiker" to authorViewer?.let { it.followedBy != null },
            "doesLikerFollowPoster" to authorViewer?.let { it.following != null },
            "likerClout" to toClout(ownFollowersCount()),
            "postClout" to toClout(post.likeCount + post.repostCount + post.replyCount),
            "feedDescriptor" to context.feedDescriptor,
        )
    }
}

/** Applies a shadow on top of server data, with RN count deltas (`wasLiked` vs `isLiked`). */
fun PostView.withShadow(shadows: Map<String, PostShadow>, isPending: (String) -> Boolean = { false }): PostView {
    val shadow = shadows[uri] ?: return this
    if (shadow.base != ShadowBase.of(this) && !isPending(uri)) return this
    fun delta(was: String?, now: String?) = (if (now != null) 1 else 0) - (if (was != null) 1 else 0)
    return copy(
        likeCount = (likeCount + delta(viewer.like, shadow.likeUri)).coerceAtLeast(0),
        repostCount = (repostCount + delta(viewer.repost, shadow.repostUri)).coerceAtLeast(0),
        viewer = viewer.copy(like = shadow.likeUri, repost = shadow.repostUri),
    )
}
