package io.nativestarter.showcase.bluesky.core.network

import io.nativestarter.showcase.bluesky.core.analytics.InteractionContext
import io.nativestarter.showcase.bluesky.core.analytics.RecordingAnalytics
import io.nativestarter.showcase.bluesky.testing.FakeBskyRepository
import io.nativestarter.showcase.bluesky.testing.post
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PostInteractionsTest {
    private val repo = FakeBskyRepository()
    private val analytics = RecordingAnalytics()
    private val feed = InteractionContext("FeedItem", "following")

    private fun TestScope.interactions(followers: Int? = 2) = PostInteractions(repo, this, analytics) { followers }

    @Test
    fun likeIsOptimisticThenConfirmedWithRnEvent() = runTest(UnconfinedTestDispatcher()) {
        val interactions = interactions()
        val p = post(likeCount = 3)
        val gate = CompletableDeferred<Unit>()
        repo.gate = gate

        interactions.toggleLike(p, feed)
        var shown = interactions.apply(p)
        assertEquals(4, shown.likeCount)
        assertEquals(PENDING_URI, shown.viewer.like)
        assertTrue(interactions.isPending(p.uri))

        gate.complete(Unit)
        shown = interactions.apply(p)
        assertEquals(4, shown.likeCount)
        assertEquals("at://did:plc:me/app.bsky.feed.like/0", shown.viewer.like)
        assertFalse(interactions.isPending(p.uri))
        assertEquals(listOf("like:${p.uri}:via=null"), repo.calls)

        val (name, props) = analytics.events.single()
        assertEquals("post:like", name)
        assertEquals(p.uri, props["uri"])
        assertEquals("FeedItem", props["logContext"])
        assertEquals("following", props["feedDescriptor"])
        assertEquals("toClout(2) = round(ln 2) = 1", 1, props["likerClout"])
        assertEquals("toClout(3 + 0 + 0) = 1", 1, props["postClout"])
        assertFalse("author.viewer is absent, so the follow flags are undefined", props.containsKey("doesPosterFollowLiker"))
    }

    @Test
    fun unlikeDeletesTheExistingRecordAndLogsUnlike() = runTest(UnconfinedTestDispatcher()) {
        val interactions = interactions()
        val p = post(likeCount = 1, like = "at://did:plc:me/app.bsky.feed.like/abc")
        interactions.toggleLike(p, feed)
        val shown = interactions.apply(p)
        assertEquals(0, shown.likeCount)
        assertNull(shown.viewer.like)
        assertEquals(listOf("deleteLike:at://did:plc:me/app.bsky.feed.like/abc"), repo.calls)
        assertEquals(listOf("post:unlike"), analytics.names())
    }

    @Test
    fun failedLikeShowsTheServerStateAgain() = runTest(UnconfinedTestDispatcher()) {
        val interactions = interactions()
        repo.failWrites = true
        val p = post(likeCount = 2)
        interactions.toggleLike(p, feed)
        val shown = interactions.apply(p)
        assertEquals(2, shown.likeCount)
        assertNull(shown.viewer.like)
    }

    @Test
    fun failedCompensatingUnlikeKeepsTheConfirmedLikeVisible() = runTest(UnconfinedTestDispatcher()) {
        val interactions = interactions()
        val p = post(likeCount = 0)
        val gate = CompletableDeferred<Unit>()
        repo.gate = gate
        repo.failWritesAfter = 1
        interactions.toggleLike(p, feed)
        interactions.toggleLike(interactions.apply(p), feed)
        gate.complete(Unit)
        val shown = interactions.apply(p)
        assertEquals("at://did:plc:me/app.bsky.feed.like/0", shown.viewer.like)
        assertEquals(1, shown.likeCount)
        assertEquals(2, repo.calls.size)
    }

    @Test
    fun freshServerDataReplacesASettledShadow() = runTest(UnconfinedTestDispatcher()) {
        val interactions = interactions()
        val p = post(likeCount = 0)
        interactions.toggleLike(p, feed)
        val fresh = p.copy(likeCount = 7, viewer = p.viewer.copy(like = "at://server/like"))
        assertEquals("fresh data wins once no request is running", fresh, interactions.apply(fresh))
        assertEquals("the post the shadow was made from still shows the shadow", 1, interactions.apply(p).likeCount)
    }

    @Test
    fun pendingRequestStillAppliesToFreshServerData() = runTest(UnconfinedTestDispatcher()) {
        val interactions = interactions()
        val p = post(likeCount = 0)
        val gate = CompletableDeferred<Unit>()
        repo.gate = gate
        interactions.toggleLike(p, feed)
        val refetched = p.copy(likeCount = 5)
        val shown = interactions.apply(refetched)
        assertEquals(PENDING_URI, shown.viewer.like)
        assertEquals("count delta applies to the refetched count", 6, shown.likeCount)
        gate.complete(Unit)
    }

    @Test
    fun repostThroughARepostSendsViaAndRemoveRepostDeletesIt() = runTest(UnconfinedTestDispatcher()) {
        val interactions = interactions()
        val p = post()
        val via = StrongRef("at://did:plc:bob/app.bsky.feed.repost/r1", "rc1")
        interactions.toggleRepost(p, feed, via)
        val reposted = interactions.apply(p)
        assertEquals(1, reposted.repostCount)
        assertEquals("at://did:plc:me/app.bsky.feed.repost/0", reposted.viewer.repost)

        interactions.toggleRepost(reposted, feed, via)
        val removed = interactions.apply(p)
        assertEquals(0, removed.repostCount)
        assertNull(removed.viewer.repost)
        assertEquals(
            listOf("repost:${p.uri}:via=${via.uri}", "deleteRepost:at://did:plc:me/app.bsky.feed.repost/0"),
            repo.calls,
        )
        assertEquals(listOf("post:repost", "post:unrepost"), analytics.names())
    }

    @Test
    fun rkeyAndRepoComeFromTheRecordUri() {
        assertEquals("3mvitvk7tgc22", AtUri.rkey("at://did:plc:x/app.bsky.feed.like/3mvitvk7tgc22"))
        assertEquals("did:plc:x", AtUri.repo("at://did:plc:x/app.bsky.feed.like/3mvitvk7tgc22"))
        assertNull(AtUri.repo("pending"))
    }
}
