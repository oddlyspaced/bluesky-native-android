package io.nativestarter.showcase.bluesky.feature

import io.nativestarter.showcase.bluesky.core.analytics.RecordingAnalytics
import io.nativestarter.showcase.bluesky.core.network.PinnedFeed
import io.nativestarter.showcase.bluesky.core.network.PostInteractions
import io.nativestarter.showcase.bluesky.feature.feed.FeedViewModel
import io.nativestarter.showcase.bluesky.feature.feed.HomeTabs
import io.nativestarter.showcase.bluesky.feature.feed.PostFeedRow
import io.nativestarter.showcase.bluesky.testing.FakeBskyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeatureViewModelTest {
    private val repo = FakeBskyRepository()
    private val analytics = RecordingAnalytics()
    private lateinit var interactions: PostInteractions
    private val me = "did:plc:alice"

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        interactions = PostInteractions(repo, CoroutineScope(Dispatchers.Main), analytics) { 2 }
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun feedVm() = FeedViewModel(repo, interactions, analytics, me)

    @Test
    fun homeTabsAddFeedsLinkOnlyWithoutCustomFeeds() {
        assertEquals(listOf("Following", "Feeds ✨"), HomeTabs.from(listOf(PinnedFeed("Following", true))))
        assertEquals(1, HomeTabs.feedsLinkIndex(listOf("Following", "Feeds ✨")))
        assertEquals(listOf("Following", "Discover"), HomeTabs.from(listOf(PinnedFeed("Following", true), PinnedFeed("Discover", false))))
        assertNull(HomeTabs.feedsLinkIndex(listOf("Following", "Discover")))
    }

    @Test
    fun feedBuildsRnRowsFallbackMarkerAndEndOfFeed() = runTest {
        val s = feedVm().state.value
        assertTrue(s.isFetched)
        assertNull(s.error)
        assertTrue(s.rows.first() is PostFeedRow.ComposerPrompt)
        assertTrue("Following ran out on page 2, so the fallback marker follows the posts", s.rows.last() is PostFeedRow.FallbackMarker)
        val posts = s.rows.filterIsInstance<PostFeedRow.SliceItemRow>()
        assertEquals(listOf("Thread root", "Thread reply", "Post", "Post", "Post"), posts.map { it.post.record.text })
        assertEquals(listOf("bob.test", "carla.test", "carla.test", "bob.test", "alice.test"), posts.map { it.post.author.handle })
        assertTrue(s.showEndOfFeed)
        assertFalse(s.showLoadMoreError)
        assertFalse(s.showEmpty)
        assertEquals("auto-pagination loads page 2 because 5 posts < 30", listOf("timeline", "timeline:page2"), repo.calls.filter { it.startsWith("timeline") })
    }

    @Test
    fun feedFirstLoadErrorShowsTheBannerNotTheEndOfFeed() = runTest {
        repo.failReads = true
        val s = feedVm().state.value
        assertTrue(s.showFirstLoadError)
        assertFalse(s.showEndOfFeed)
        assertTrue(s.rows.isEmpty())
    }

    @Test
    fun postViewEventsUseRnPositions() = runTest {
        val vm = feedVm()
        vm.state.value.rows.filterIsInstance<PostFeedRow.SliceItemRow>().forEach(vm::onPostSeen)
        vm.state.value.rows.filterIsInstance<PostFeedRow.SliceItemRow>().forEach(vm::onPostSeen)
        val views = analytics.events.filter { it.first == "post:view" }
        assertEquals("the thread root is not the slice's feed post; each URI logs once", 4, views.size)
        // RN getPostPosition(slice.items[0]._reactKey): root rows before the slice's first row. The first slice is
        // Thread root + Thread reply; its feed post is the reply, which still logs position 0.
        assertEquals(listOf(0, 1, 2, 3), views.map { it.second["position"] })
        assertEquals(true, views.first().second["isReply"])
        assertEquals("following", views.first().second["feedDescriptor"])
    }

    @Test
    fun refreshAndEndReachedEventsFollowRn() = runTest {
        val vm = feedVm()
        vm.onEndReached()
        assertFalse("no next page, so no endReached event", analytics.names().contains("feed:endReached"))
        vm.refresh(FeedViewModel.REASON_PULL_TO_REFRESH)
        val refresh = analytics.events.single { it.first == "feed:refresh" }.second
        assertEquals(mapOf("feedType" to "following", "feedUrl" to "following", "reason" to "pull-to-refresh"), refresh)
        vm.onComposerPressed()
        assertTrue(analytics.names().contains("composerPrompt:press"))
    }

    @Test
    fun likeInFeedUpdatesTheRowAndLogsTheEvent() = runTest {
        val feed = feedVm()
        val root = feed.state.value.rows.filterIsInstance<PostFeedRow.SliceItemRow>().first().post
        feed.toggleLike(root, null)
        val updated = feed.state.value.rows.filterIsInstance<PostFeedRow.SliceItemRow>().first().post
        assertEquals(1, updated.likeCount)
        assertNotNull(updated.viewer.like)
        assertTrue(analytics.names().contains("post:like"))
        feed.toggleLike(updated, null)
        val removed = feed.state.value.rows.filterIsInstance<PostFeedRow.SliceItemRow>().first().post
        assertEquals("unlike after a confirmed like deletes it", 0, removed.likeCount)
        assertTrue(repo.calls.any { it.startsWith("deleteLike:") })
    }

    @Test
    fun viaComesFromARepostReasonWithUriAndCid() {
        val reason = io.nativestarter.showcase.bluesky.feature.feed.FeedReason.Repost(
            by = io.nativestarter.showcase.bluesky.core.network.ProfileViewBasic("did:plc:bob", "bob.test"),
            uri = "at://did:plc:bob/app.bsky.feed.repost/r",
            cid = "rc",
            indexedAt = null,
        )
        assertEquals("at://did:plc:bob/app.bsky.feed.repost/r", FeedViewModel.viaOf(reason)?.uri)
        assertNull(FeedViewModel.viaOf(reason.copy(cid = null)))
        assertNull(FeedViewModel.viaOf(io.nativestarter.showcase.bluesky.feature.feed.FeedReason.Pin))
    }

}
