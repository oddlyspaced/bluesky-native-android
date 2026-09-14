package io.nativestarter.showcase.bluesky.core.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import io.nativestarter.showcase.bluesky.core.session.SessionManager
import java.time.Clock
import java.time.Instant

/** Everything the slice reads from or writes to the network. View models depend on this interface only. */
interface BskyRepository {
    suspend fun timeline(cursor: String? = null): FeedResponse
    suspend fun authorFeed(actor: String, cursor: String? = null): FeedResponse
    suspend fun postThread(uri: String): ThreadNode
    suspend fun profile(actor: String): ProfileViewDetailed
    /**
     * RN `countUnread` (src/state/queries/notifications/unread.tsx): unread items on the first
     * `listNotifications` page (limit 40). Returns 30 or more as-is; the badge formats "30+".
     */
    suspend fun unreadNotificationCount(): Int

    /** Pinned feeds, in order. The timeline is "Following", as in RN `usePinnedFeedsInfos`. */
    suspend fun pinnedFeeds(): List<PinnedFeed>

    /** Creates an `app.bsky.feed.like` record and returns its AT-URI. */
    suspend fun like(subject: StrongRef): String
    suspend fun deleteLike(likeUri: String)

    /** Creates an `app.bsky.feed.repost` record and returns its AT-URI. */
    suspend fun repost(subject: StrongRef): String
    suspend fun deleteRepost(repostUri: String)
}

class XrpcBskyRepository(
    private val client: XrpcClient,
    private val session: SessionManager,
    private val clock: Clock = Clock.systemUTC(),
) : BskyRepository {

    override suspend fun timeline(cursor: String?): FeedResponse = session.authed { s ->
        decode(client.query("app.bsky.feed.getTimeline", mapOf("limit" to "30", "cursor" to cursor), s.accessJwt))
    }

    override suspend fun authorFeed(actor: String, cursor: String?): FeedResponse = session.authed { s ->
        // RN profile "Posts" tab uses filter posts_and_author_threads (src/view/screens/Profile.tsx).
        decode(
            client.query(
                "app.bsky.feed.getAuthorFeed",
                mapOf("actor" to actor, "limit" to "30", "filter" to "posts_and_author_threads", "cursor" to cursor),
                s.accessJwt,
            ),
        )
    }

    override suspend fun postThread(uri: String): ThreadNode = session.authed { s ->
        decode<ThreadResponse>(client.query("app.bsky.feed.getPostThread", mapOf("uri" to uri), s.accessJwt)).thread
    }

    override suspend fun profile(actor: String): ProfileViewDetailed = session.authed { s ->
        decode(client.query("app.bsky.actor.getProfile", mapOf("actor" to actor), s.accessJwt))
    }

    override suspend fun unreadNotificationCount(): Int = session.authed { s ->
        val page = decode<NotificationsResponse>(
            client.query("app.bsky.notification.listNotifications", mapOf("limit" to "40"), s.accessJwt),
        )
        page.notifications.count { !it.isRead }
    }

    override suspend fun pinnedFeeds(): List<PinnedFeed> = session.authed { s ->
        val prefs = XrpcJson.parseToJsonElement(
            client.query("app.bsky.actor.getPreferences", emptyMap(), s.accessJwt, XrpcClient.Target.Pds),
        ).jsonObject["preferences"]?.jsonArray ?: JsonArray(emptyList())
        val saved = prefs.map { it.jsonObject }
            .firstOrNull { it["\$type"]?.jsonPrimitive?.content == "app.bsky.actor.defs#savedFeedsPrefV2" }
            ?.get("items")?.jsonArray?.map { it.jsonObject }
            ?.filter { it["pinned"]?.jsonPrimitive?.content == "true" }
            .orEmpty()
        val feedUris = saved.filter { it["type"]?.jsonPrimitive?.content == "feed" }
            .map { it.getValue("value").jsonPrimitive.content }
        val names = if (feedUris.isEmpty()) emptyMap() else queryFeedGenerators(feedUris, s.accessJwt)
        saved.mapNotNull { item ->
            when (item["type"]?.jsonPrimitive?.content) {
                "timeline" -> PinnedFeed("Following", isTimeline = true)
                "feed" -> names[item.getValue("value").jsonPrimitive.content]?.let { PinnedFeed(it, isTimeline = false) }
                else -> null
            }
        }.ifEmpty { listOf(PinnedFeed("Following", isTimeline = true)) }
    }

    private suspend fun queryFeedGenerators(uris: List<String>, accessJwt: String): Map<String, String> {
        // OkHttp encodes repeated `feeds` params; XrpcClient takes a map, so build the query by hand.
        val text = client.queryRepeated("app.bsky.feed.getFeedGenerators", "feeds", uris, accessJwt)
        return XrpcJson.parseToJsonElement(text).jsonObject["feeds"]?.jsonArray.orEmpty().associate {
            val o = it.jsonObject
            o.getValue("uri").jsonPrimitive.content to o.getValue("displayName").jsonPrimitive.content
        }
    }

    override suspend fun like(subject: StrongRef): String = createRecord("app.bsky.feed.like", subject)
    override suspend fun deleteLike(likeUri: String) = deleteRecord("app.bsky.feed.like", likeUri)
    override suspend fun repost(subject: StrongRef): String = createRecord("app.bsky.feed.repost", subject)
    override suspend fun deleteRepost(repostUri: String) = deleteRecord("app.bsky.feed.repost", repostUri)

    private suspend fun createRecord(collection: String, subject: StrongRef): String = session.authed { s ->
        val body = buildJsonObject {
            put("repo", s.did)
            put("collection", collection)
            putJsonObject("record") {
                put("\$type", collection)
                putJsonObject("subject") {
                    put("uri", subject.uri)
                    put("cid", subject.cid)
                }
                put("createdAt", Instant.now(clock).toString())
            }
        }
        decode<CreateRecordResponse>(client.procedure("com.atproto.repo.createRecord", body, s.accessJwt)).uri
    }

    private suspend fun deleteRecord(collection: String, recordUri: String) {
        session.authed { s ->
            val body = buildJsonObject {
                put("repo", s.did)
                put("collection", collection)
                put("rkey", AtUri.rkey(recordUri))
            }
            client.procedure("com.atproto.repo.deleteRecord", body, s.accessJwt)
        }
    }

    private inline fun <reified T> decode(text: String): T = XrpcJson.decodeFromString(text)
}

object AtUri {
    /** `at://did/collection/rkey` -> `rkey`. */
    fun rkey(uri: String): String = uri.substringAfterLast('/')
}

private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
