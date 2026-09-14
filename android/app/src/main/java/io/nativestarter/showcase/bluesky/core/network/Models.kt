package io.nativestarter.showcase.bluesky.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Subset of the AT Protocol lexicons this slice reads. Unknown fields are ignored by [XrpcJson]. */

@Serializable
data class CreateSessionRequest(val identifier: String, val password: String)

@Serializable
data class SessionResponse(
    val did: String,
    val handle: String,
    val accessJwt: String,
    val refreshJwt: String,
)

@Serializable
data class ProfileViewBasic(
    val did: String,
    val handle: String,
    val displayName: String? = null,
    val avatar: String? = null,
)

@Serializable
data class ProfileViewDetailed(
    val did: String,
    val handle: String,
    val displayName: String? = null,
    val description: String? = null,
    val avatar: String? = null,
    val banner: String? = null,
    val followersCount: Int = 0,
    val followsCount: Int = 0,
    val postsCount: Int = 0,
)

@Serializable
data class PostRecord(
    val text: String = "",
    val createdAt: String? = null,
    val reply: ReplyRef? = null,
)

@Serializable
data class ReplyRef(val root: StrongRef, val parent: StrongRef)

@Serializable
data class StrongRef(val uri: String, val cid: String)

@Serializable
data class PostViewerState(
    val like: String? = null,
    val repost: String? = null,
    val bookmarked: Boolean = false,
)

@Serializable
data class PostView(
    val uri: String,
    val cid: String,
    val author: ProfileViewBasic,
    val record: PostRecord,
    val replyCount: Int = 0,
    val repostCount: Int = 0,
    val likeCount: Int = 0,
    val quoteCount: Int = 0,
    val bookmarkCount: Int = 0,
    val indexedAt: String,
    val viewer: PostViewerState = PostViewerState(),
)

/** `app.bsky.feed.defs#replyRef`; root and parent may be a blocked or missing post, which this slice skips. */
@Serializable
data class FeedReplyRef(val root: JsonElement? = null, val parent: JsonElement? = null)

@Serializable
data class FeedViewPost(
    val post: PostView,
    val reply: FeedReplyRef? = null,
    val reason: JsonElement? = null,
)

@Serializable
data class FeedResponse(val feed: List<FeedViewPost>, val cursor: String? = null)

@Serializable
data class ThreadResponse(val thread: ThreadNode)

/** `app.bsky.feed.defs#threadViewPost`, or a not-found / blocked stub that has no [post]. */
@Serializable
data class ThreadNode(
    @SerialName("\$type") val type: String? = null,
    val post: PostView? = null,
    val parent: ThreadNode? = null,
    val replies: List<ThreadNode> = emptyList(),
)

@Serializable
data class NotificationView(val uri: String, val reason: String, val isRead: Boolean)

@Serializable
data class NotificationsResponse(val notifications: List<NotificationView>, val cursor: String? = null)

/** A pinned home feed tab. */
data class PinnedFeed(val displayName: String, val isTimeline: Boolean)

@Serializable
data class CreateRecordResponse(val uri: String, val cid: String)
