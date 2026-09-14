package io.nativestarter.showcase.bluesky.feature

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
import io.nativestarter.showcase.bluesky.core.network.FeedResponse
import io.nativestarter.showcase.bluesky.core.network.PinnedFeed
import io.nativestarter.showcase.bluesky.core.network.PostInteractions
import io.nativestarter.showcase.bluesky.feature.feed.FeedSlices
import io.nativestarter.showcase.bluesky.feature.feed.FeedViewModel
import io.nativestarter.showcase.bluesky.feature.feed.HomeTabs
import io.nativestarter.showcase.bluesky.feature.profile.PROFILE_TABS
import io.nativestarter.showcase.bluesky.feature.profile.ProfileViewModel
import io.nativestarter.showcase.bluesky.feature.thread.ThreadViewModel
import io.nativestarter.showcase.bluesky.testing.FakeBskyRepository
import io.nativestarter.showcase.bluesky.testing.Fixtures

@OptIn(ExperimentalCoroutinesApi::class)
class FeatureViewModelTest {
    private val repo = FakeBskyRepository()
    private val interactions = PostInteractions(repo)

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun feedSlicesPutReplyUnderRootAndDropDuplicateRoot() {
        val slices = FeedSlices.build(Fixtures.decode<FeedResponse>("getTimeline.json").feed)
        assertEquals(4, slices.size)
        val thread = slices.first().rows
        assertEquals(listOf("Thread root", "Thread reply"), thread.map { it.post.record.text })
        assertTrue(thread[0].isThreadParent)
        assertTrue(thread[1].isThreadChild)
        assertEquals(listOf("carla.test", "bob.test", "alice.test"), slices.drop(1).map { it.rows.single().post.author.handle })
    }

    @Test
    fun homeTabsAddFeedsLinkOnlyWithoutCustomFeeds() {
        assertEquals(listOf("Following", "Feeds ✨"), HomeTabs.from(listOf(PinnedFeed("Following", true))))
        assertEquals(1, HomeTabs.feedsLinkIndex(listOf("Following", "Feeds ✨")))
        assertEquals(listOf("Following", "Discover"), HomeTabs.from(listOf(PinnedFeed("Following", true), PinnedFeed("Discover", false))))
        assertNull(HomeTabs.feedsLinkIndex(listOf("Following", "Discover")))
    }

    @Test
    fun feedLoadsSlicesTabsAndEndOfFeed() = runTest {
        val vm = FeedViewModel(repo, interactions)
        val s = vm.state.value
        assertFalse(s.loading)
        assertNull(s.error)
        assertEquals(4, s.slices.size)
        assertEquals(listOf("Following", "Feeds ✨"), s.feedTabs)
        assertTrue("page 2 is empty, so the feed has ended", s.reachedEnd)
        assertEquals(listOf("timeline", "timeline:page2"), repo.calls.filter { it.startsWith("timeline") })
    }

    @Test
    fun loadPagesStopsAtEmptyPageAndCapsExtraPages() = runTest {
        val first = Fixtures.decode<FeedResponse>("getTimeline.json")
        val ended = io.nativestarter.showcase.bluesky.feature.feed.loadPages { c -> if (c == null) first else FeedResponse(emptyList(), null) }
        assertTrue(ended.reachedEnd)
        assertEquals(5, ended.items.size)

        var fetches = 0
        val endless = io.nativestarter.showcase.bluesky.feature.feed.loadPages(maxExtraPages = 2) { fetches++; first }
        assertFalse(endless.reachedEnd)
        assertEquals(3, fetches)
    }

    @Test
    fun feedErrorState() = runTest {
        repo.failReads = true
        val s = FeedViewModel(repo, interactions).state.value
        assertFalse(s.loading)
        assertEquals("offline", s.error)
        assertTrue(s.slices.isEmpty())
    }

    @Test
    fun likeInFeedUpdatesEverySliceRowForThatPost() = runTest {
        val vm = FeedViewModel(repo, interactions)
        val root = vm.state.value.slices.first().rows.first().post
        vm.toggleLike(root)
        val updated = vm.state.value.slices.first().rows.first().post
        assertEquals(1, updated.likeCount)
        assertNotNull(updated.viewer.like)
    }

    @Test
    fun likeIsSharedBetweenFeedAndThread() = runTest {
        val feed = FeedViewModel(repo, interactions)
        val thread = ThreadViewModel("at://root", repo, interactions)
        val root = thread.state.value.anchor!!
        feed.toggleLike(root)
        assertEquals(1, thread.state.value.anchor!!.likeCount)
    }

    @Test
    fun threadFlattensAnchorAndReplies() = runTest {
        val s = ThreadViewModel("at://root", repo, interactions).state.value
        assertEquals("Thread root", s.anchor?.record?.text)
        assertTrue(s.parents.isEmpty())
        assertEquals(listOf("carla.test"), s.replies.map { it.author.handle })
    }

    @Test
    fun profileLoadsHeaderAndPostsTab() = runTest {
        val vm = ProfileViewModel("did:plc:alice", repo, interactions)
        val s = vm.state.value
        assertEquals("Alice", s.profile?.displayName)
        assertEquals(1, s.posts.size)
        assertEquals(0, s.selectedTab)
        assertTrue(s.reachedEnd)
        vm.selectTab(PROFILE_TABS.indexOf("Likes"))
        assertEquals(4, vm.state.value.selectedTab)
        assertTrue(repo.calls.containsAll(listOf("profile:did:plc:alice", "authorFeed:did:plc:alice")))
    }
}
