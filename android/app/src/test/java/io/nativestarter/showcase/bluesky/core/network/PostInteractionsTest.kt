package io.nativestarter.showcase.bluesky.core.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import io.nativestarter.showcase.bluesky.testing.FakeBskyRepository
import io.nativestarter.showcase.bluesky.testing.post

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PostInteractionsTest {
    private val repo = FakeBskyRepository()
    private val interactions = PostInteractions(repo)

    @Test
    fun likeIsOptimisticThenStoresRecordUri() = runTest(UnconfinedTestDispatcher()) {
        val p = post(likeCount = 3)
        val gate = CompletableDeferred<Unit>()
        repo.gate = gate
        val job = launch { interactions.toggleLike(p) }

        val pending = p.withShadow(interactions.shadows.value)
        assertEquals(4, pending.likeCount)
        assertEquals(PENDING_URI, pending.viewer.like)

        gate.complete(Unit)
        job.join()
        val done = p.withShadow(interactions.shadows.value)
        assertEquals(4, done.likeCount)
        assertEquals("at://did:plc:me/app.bsky.feed.like/0", done.viewer.like)
        assertEquals(listOf("like:${p.uri}"), repo.calls)
    }

    @Test
    fun unlikeDeletesTheExistingRecord() = runTest {
        val p = post(likeCount = 1, like = "at://did:plc:me/app.bsky.feed.like/abc")
        interactions.toggleLike(p)
        val s = p.withShadow(interactions.shadows.value)
        assertEquals(0, s.likeCount)
        assertNull(s.viewer.like)
        assertEquals(listOf("deleteLike:at://did:plc:me/app.bsky.feed.like/abc"), repo.calls)
    }

    @Test
    fun failedLikeRevertsToServerState() = runTest {
        repo.failWrites = true
        val p = post(likeCount = 2)
        interactions.toggleLike(p)
        val s = p.withShadow(interactions.shadows.value)
        assertEquals(2, s.likeCount)
        assertNull(s.viewer.like)
    }

    @Test
    fun repostThenRemoveRepost() = runTest {
        val p = post()
        interactions.toggleRepost(p)
        val reposted = p.withShadow(interactions.shadows.value)
        assertEquals(1, reposted.repostCount)
        assertEquals("at://did:plc:me/app.bsky.feed.repost/0", reposted.viewer.repost)

        interactions.toggleRepost(reposted)
        val removed = p.withShadow(interactions.shadows.value)
        assertEquals(0, removed.repostCount)
        assertNull(removed.viewer.repost)
        assertEquals(
            listOf("repost:${p.uri}", "deleteRepost:at://did:plc:me/app.bsky.feed.repost/0"),
            repo.calls,
        )
    }

    @Test
    fun failedRemoveRepostRestoresRepost() = runTest {
        val p = post().copy(repostCount = 1, viewer = PostViewerState(repost = "at://did:plc:me/app.bsky.feed.repost/x"))
        repo.failWrites = true
        interactions.toggleRepost(p)
        val s = p.withShadow(interactions.shadows.value)
        assertEquals(1, s.repostCount)
        assertEquals("at://did:plc:me/app.bsky.feed.repost/x", s.viewer.repost)
    }

    @Test
    fun rkeyIsLastUriSegment() = assertEquals("3mvitvk7tgc22", AtUri.rkey("at://did:plc:x/app.bsky.feed.like/3mvitvk7tgc22"))
}
