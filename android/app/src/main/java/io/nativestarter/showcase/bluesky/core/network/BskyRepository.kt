package io.nativestarter.showcase.bluesky.core.network

import io.nativestarter.showcase.bluesky.core.session.SessionManager
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.Clock
import java.time.Instant

/** Everything the slice reads from or writes to the network. View models depend on this interface only. */
interface BskyRepository {
    suspend fun timeline(cursor: String? = null): FeedResponse
    suspend fun authorFeed(actor: String, cursor: String? = null): FeedResponse

    /** RN `usePostThread` (src/state/queries/usePostThread): linear view, `branchingFactor` 1, `below` 10. */
    suspend fun postThreadV2(anchor: String, sort: String): ThreadV2Response

    /** RN other replies (`getPostThreadOtherV2`), fetched only after "Show more replies". */
    suspend fun postThreadOtherV2(anchor: String): ThreadOtherV2Response

    suspend fun profile(actor: String): ProfileViewDetailed

    /**
     * RN `countUnread` (src/state/queries/notifications/unread.tsx): unread items on the first
     * `listNotifications` page (limit 40). Returns 30 or more as-is; the badge formats "30+".
     */
    suspend fun unreadNotificationCount(): Int

    /** Pinned feeds, in order. The timeline is "Following", as in RN `usePinnedFeedsInfos`. */
    suspend fun pinnedFeeds(): List<PinnedFeed>

    suspend fun preferences(): UserPreferences

    /** Creates an `app.bsky.feed.like` record and returns its AT-URI. [via] is the repost the like came through. */
    suspend fun like(subject: StrongRef, via: StrongRef? = null): String
    suspend fun deleteLike(likeUri: String)

    /** Creates an `app.bsky.feed.repost` record and returns its AT-URI. [via] is the repost the repost came through. */
    suspend fun repost(subject: StrongRef, via: StrongRef? = null): String
    suspend fun deleteRepost(repostUri: String)
}

class XrpcBskyRepository(
    private val client: XrpcClient,
    private val session: SessionManager,
    /** Every call is scoped to this account; a call made after an account switch fails. */
    private val accountDid: String? = null,
    private val clock: Clock = Clock.systemUTC(),
) : BskyRepository {

    override suspend fun timeline(cursor: String?): FeedResponse = session.authed(accountDid) { s ->
        decode(client.query("app.bsky.feed.getTimeline", mapOf("limit" to "30", "cursor" to cursor), s.accessJwt))
    }

    override suspend fun authorFeed(actor: String, cursor: String?): FeedResponse = session.authed(accountDid) { s ->
        // RN profile "Posts" tab uses filter posts_and_author_threads with pins (src/lib/api/feed/author.ts).
        decode(
            client.query(
                "app.bsky.feed.getAuthorFeed",
                mapOf(
                    "actor" to actor,
                    "limit" to "30",
                    "filter" to "posts_and_author_threads",
                    "includePins" to "true",
                    "cursor" to cursor,
                ),
                s.accessJwt,
            ),
        )
    }

    override suspend fun postThreadV2(anchor: String, sort: String): ThreadV2Response = session.authed(accountDid) { s ->
        decode(
            client.query(
                "app.bsky.unspecced.getPostThreadV2",
                mapOf("anchor" to anchor, "branchingFactor" to "1", "below" to "10", "sort" to sort),
                s.accessJwt,
            ),
        )
    }

    override suspend fun postThreadOtherV2(anchor: String): ThreadOtherV2Response = session.authed(accountDid) { s ->
        decode(client.query("app.bsky.unspecced.getPostThreadOtherV2", mapOf("anchor" to anchor), s.accessJwt))
    }

    override suspend fun profile(actor: String): ProfileViewDetailed = session.authed(accountDid) { s ->
        decode(client.query("app.bsky.actor.getProfile", mapOf("actor" to actor), s.accessJwt))
    }

    override suspend fun unreadNotificationCount(): Int = session.authed(accountDid) { s ->
        val page = decode<NotificationsResponse>(
            client.query("app.bsky.notification.listNotifications", mapOf("limit" to "40"), s.accessJwt),
        )
        page.notifications.count { !it.isRead }
    }

    override suspend fun preferences(): UserPreferences = session.authed(accountDid) { s ->
        UserPreferences.parse(client.query("app.bsky.actor.getPreferences", emptyMap(), s.accessJwt, XrpcClient.Target.Pds))
    }

    override suspend fun pinnedFeeds(): List<PinnedFeed> = session.authed(accountDid) { s ->
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

    override suspend fun like(subject: StrongRef, via: StrongRef?): String = createRecord("app.bsky.feed.like", subject, via)
    override suspend fun deleteLike(likeUri: String) = deleteRecord("app.bsky.feed.like", likeUri)
    override suspend fun repost(subject: StrongRef, via: StrongRef?): String = createRecord("app.bsky.feed.repost", subject, via)
    override suspend fun deleteRepost(repostUri: String) = deleteRecord("app.bsky.feed.repost", repostUri)

    /** RN @bsky/sdk `client.create(schema, {subject, createdAt, via})`; the server generates the TID record key. */
    private suspend fun createRecord(collection: String, subject: StrongRef, via: StrongRef?): String = session.authed(accountDid) { s ->
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
                if (via != null) {
                    putJsonObject("via") {
                        put("uri", via.uri)
                        put("cid", via.cid)
                    }
                }
            }
        }
        decode<CreateRecordResponse>(client.procedure("com.atproto.repo.createRecord", body, s.accessJwt)).uri
    }

    /** RN `deleteLike` / `deleteRepost`: the repo is the DID in the record URI, the rkey its last segment. */
    private suspend fun deleteRecord(collection: String, recordUri: String) {
        session.authed(accountDid) { s ->
            val body = buildJsonObject {
                put("repo", AtUri.repo(recordUri) ?: s.did)
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

    /** `at://did/collection/rkey` -> `did`, or null when the URI has no authority. */
    fun repo(uri: String): String? = uri.removePrefix("at://").substringBefore('/').takeIf { uri.startsWith("at://") && it.isNotEmpty() }
}

private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
