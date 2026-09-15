package io.nativestarter.showcase.bluesky.feature

import io.nativestarter.showcase.bluesky.core.analytics.RecordingAnalytics
import io.nativestarter.showcase.bluesky.core.network.FeedResponse
import io.nativestarter.showcase.bluesky.core.network.PostCache
import io.nativestarter.showcase.bluesky.core.network.PostInteractions
import io.nativestarter.showcase.bluesky.core.network.ProfileViewDetailed
import io.nativestarter.showcase.bluesky.core.network.XrpcException
import io.nativestarter.showcase.bluesky.feature.feed.PostFeedRow
import io.nativestarter.showcase.bluesky.feature.profile.PROFILE_TABS
import io.nativestarter.showcase.bluesky.feature.profile.ProfileErrorCopy
import io.nativestarter.showcase.bluesky.feature.profile.ProfileViewModel
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** CP-03: RN `ProfileScreen` loading order, the Posts `PostFeed` states and the profile events. */
@OptIn(ExperimentalCoroutinesApi::class)
class Cp03ProfileTest {
    private val repo = FakeBskyRepository()
    private val analytics = RecordingAnalytics()
    private lateinit var interactions: PostInteractions
    private val me = "did:plc:alice"
    private val descriptor = "author|did:plc:alice|posts_and_author_threads"

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        interactions = PostInteractions(repo, CoroutineScope(Dispatchers.Main), analytics) { 2 }
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(onLoaded: (ProfileViewDetailed) -> Unit = {}) = ProfileViewModel(me, repo, interactions, analytics, PostCache(), onLoaded)

    @Test
    fun profileLoadsFirstThenThePostsFeed() = runTest {
        var loaded: ProfileViewDetailed? = null
        val s = vm { loaded = it }.state.value
        assertFalse(s.isProfileLoading)
        assertNull(s.profileError)
        assertEquals("Alice", s.profile?.displayName)
        assertEquals("the account scope receives the own profile", "Alice", loaded?.displayName)
        assertEquals("profile:did:plc:alice", repo.calls.first())
        assertTrue(repo.calls.contains("authorFeed:did:plc:alice"))
        assertEquals(listOf("Post"), s.feed.rows.filterIsInstance<PostFeedRow.SliceItemRow>().map { it.post.record.text })
        assertTrue("profiles have no composer prompt", s.feed.rows.none { it is PostFeedRow.ComposerPrompt })
        assertTrue(s.feed.showEndOfFeed)
    }

    @Test
    fun profileErrorShowsNotFoundAndRetryStartsTheFeed() = runTest {
        repo.failReads = true
        val vm = vm()
        assertEquals(ProfileErrorCopy("Not Found", "offline"), vm.state.value.profileError)
        assertFalse("the Posts section is not rendered, so its feed does not load", repo.calls.any { it.startsWith("authorFeed") })
        repo.failReads = false
        vm.retryProfile()
        assertNull(vm.state.value.profileError)
        assertEquals(1, vm.state.value.feed.rows.size)
    }

    @Test
    fun emptyAndBlockedFeedsFollowRn() = runTest {
        repo.authorFeedResponse = FeedResponse(emptyList(), null)
        val empty = vm().state.value
        assertTrue(empty.feed.showEmpty)
        assertFalse(empty.feed.showEndOfFeed)
        assertFalse(empty.isPostsHidden)

        repo.authorFeedResponse = null
        repo.authorFeedError = XrpcException(400, "BlockedActor", "Requester has blocked actor")
        val blocked = vm().state.value
        assertTrue(blocked.feed.showFirstLoadError)
        assertTrue("KnownError.Block shows Posts hidden", blocked.isPostsHidden)
    }

    @Test
    fun eventsUseTheAuthorFeedDescriptor() = runTest {
        val vm = vm()
        vm.state.value.feed.rows.filterIsInstance<PostFeedRow.SliceItemRow>().forEach(vm::onPostSeen)
        val view = analytics.events.single { it.first == "post:view" }.second
        assertEquals("FeedItem", view["logContext"])
        assertEquals(descriptor, view["feedDescriptor"])
        assertEquals(0, view["position"])

        vm.refresh()
        assertEquals(
            mapOf("feedType" to "author", "feedUrl" to descriptor, "reason" to "pull-to-refresh"),
            analytics.events.single { it.first == "feed:refresh" }.second,
        )
        vm.softReset()
        assertEquals("a soft reset truncates without an event", 1, analytics.events.count { it.first == "feed:refresh" })

        val post = vm.state.value.feed.rows.filterIsInstance<PostFeedRow.SliceItemRow>().first().post
        vm.onPostPressed(post)
        assertNull(analytics.events.single { it.first == "post:clickthroughItem" }.second["feedDescriptor"])
        vm.toggleLike(post, null)
        assertEquals("FeedItem", analytics.events.single { it.first == "post:like" }.second["logContext"])

        vm.selectTab(PROFILE_TABS.indexOf("Likes"))
        assertEquals(4, vm.state.value.selectedTab)
    }
}
