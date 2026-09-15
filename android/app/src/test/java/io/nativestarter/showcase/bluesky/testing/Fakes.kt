package io.nativestarter.showcase.bluesky.testing

import io.nativestarter.showcase.bluesky.core.network.BskyRepository
import io.nativestarter.showcase.bluesky.core.network.FeedResponse
import io.nativestarter.showcase.bluesky.core.network.PinnedFeed
import io.nativestarter.showcase.bluesky.core.network.PostRecord
import io.nativestarter.showcase.bluesky.core.network.PostView
import io.nativestarter.showcase.bluesky.core.network.PostViewerState
import io.nativestarter.showcase.bluesky.core.network.ProfileViewBasic
import io.nativestarter.showcase.bluesky.core.network.ProfileViewDetailed
import io.nativestarter.showcase.bluesky.core.network.StrongRef
import io.nativestarter.showcase.bluesky.core.network.ThreadNode
import io.nativestarter.showcase.bluesky.core.network.ThreadOtherV2Response
import io.nativestarter.showcase.bluesky.core.network.ThreadResponse
import io.nativestarter.showcase.bluesky.core.network.ThreadV2Response
import io.nativestarter.showcase.bluesky.core.network.UserPreferences
import io.nativestarter.showcase.bluesky.core.network.XrpcJson
import kotlinx.coroutines.CompletableDeferred

object Fixtures {
    fun text(name: String): String =
        requireNotNull(javaClass.classLoader!!.getResource("fixtures/$name")) { "missing fixture $name" }.readText()

    inline fun <reified T> decode(name: String): T = XrpcJson.decodeFromString(text(name))
}

fun post(
    uri: String = "at://did:plc:a/app.bsky.feed.post/1",
    likeCount: Int = 0,
    like: String? = null,
    repostCount: Int = 0,
    repost: String? = null,
) = PostView(
    uri = uri,
    cid = "cid",
    author = ProfileViewBasic("did:plc:a", "alice.test", "Alice"),
    record = PostRecord(text = "Post"),
    likeCount = likeCount,
    repostCount = repostCount,
    indexedAt = "2026-09-15T00:00:00.000Z",
    viewer = PostViewerState(like = like, repost = repost),
)

/**
 * In-memory repository backed by fixtures captured from the seeded dev network.
 * [failWrites] makes writes fail; [failWritesAfter] lets that many writes succeed first; [gate] holds writes open.
 */
class FakeBskyRepository(
    var timelineResponse: FeedResponse = Fixtures.decode("getTimeline.json"),
    var pinned: List<PinnedFeed> = listOf(PinnedFeed("Following", isTimeline = true)),
) : BskyRepository {
    var failWrites = false
    var failWritesAfter: Int? = null
    var failReads = false
    var gate: CompletableDeferred<Unit>? = null
    var threadV2: String = "getPostThreadV2.json"
    val calls = mutableListOf<String>()
    private var next = 0
    private var writes = 0

    /** Page 2 is the real server answer for the fixture cursor (getTimelinePage2.json). */
    override suspend fun timeline(cursor: String?): FeedResponse {
        calls += if (cursor == null) "timeline" else "timeline:page2"
        if (failReads) error("offline")
        return if (cursor == null) timelineResponse else Fixtures.decode("getTimelinePage2.json")
    }

    override suspend fun authorFeed(actor: String, cursor: String?): FeedResponse {
        calls += if (cursor == null) "authorFeed:$actor" else "authorFeed:$actor:page2"
        if (failReads) error("offline")
        return Fixtures.decode(if (cursor == null) "getAuthorFeed.json" else "getAuthorFeedPage2.json")
    }

    override suspend fun postThread(uri: String): ThreadNode {
        calls += "postThread"
        if (failReads) error("offline")
        return Fixtures.decode<ThreadResponse>("getPostThread.json").thread
    }

    override suspend fun postThreadV2(anchor: String, sort: String): ThreadV2Response {
        calls += "postThreadV2:$sort"
        if (failReads) error("offline")
        return Fixtures.decode(threadV2)
    }

    override suspend fun postThreadOtherV2(anchor: String): ThreadOtherV2Response {
        calls += "postThreadOtherV2"
        if (failReads) error("offline")
        return Fixtures.decode("getPostThreadOtherV2.json")
    }

    override suspend fun profile(actor: String): ProfileViewDetailed {
        calls += "profile:$actor"
        if (failReads) error("offline")
        return Fixtures.decode("getProfile.json")
    }

    override suspend fun unreadNotificationCount(): Int = 1
    override suspend fun pinnedFeeds(): List<PinnedFeed> = pinned
    override suspend fun preferences(): UserPreferences = UserPreferences.parse(Fixtures.text("getPreferences.json"))

    override suspend fun like(subject: StrongRef, via: StrongRef?): String =
        write("like:${subject.uri}:via=${via?.uri}") { "at://did:plc:me/app.bsky.feed.like/${next++}" }

    override suspend fun deleteLike(likeUri: String) = write("deleteLike:$likeUri") {}

    override suspend fun repost(subject: StrongRef, via: StrongRef?): String =
        write("repost:${subject.uri}:via=${via?.uri}") { "at://did:plc:me/app.bsky.feed.repost/${next++}" }

    override suspend fun deleteRepost(repostUri: String) = write("deleteRepost:$repostUri") {}

    private suspend fun <T> write(call: String, result: () -> T): T {
        calls += call
        gate?.await()
        writes++
        if (failWrites) error("write failed")
        failWritesAfter?.let { if (writes > it) error("write failed") }
        return result()
    }
}
