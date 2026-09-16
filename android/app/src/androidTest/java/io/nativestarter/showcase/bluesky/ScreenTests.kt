package io.nativestarter.showcase.bluesky

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.nativestarter.showcase.bluesky.core.analytics.RecordingAnalytics
import io.nativestarter.showcase.bluesky.feature.HomeFeedDisplayedEffect
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import io.nativestarter.showcase.bluesky.core.network.PostRecord
import io.nativestarter.showcase.bluesky.core.network.PostView
import io.nativestarter.showcase.bluesky.core.network.PostViewerState
import io.nativestarter.showcase.bluesky.core.network.ProfileViewBasic
import io.nativestarter.showcase.bluesky.core.network.ProfileViewDetailed
import io.nativestarter.showcase.bluesky.designsystem.post.PostActions
import io.nativestarter.showcase.bluesky.feature.feed.FeedUiState
import io.nativestarter.showcase.bluesky.feature.feed.HomeScreen
import io.nativestarter.showcase.bluesky.feature.feed.HomeCallbacks
import io.nativestarter.showcase.bluesky.feature.feed.PostFeedRows
import io.nativestarter.showcase.bluesky.feature.feed.FeedTuner
import io.nativestarter.showcase.bluesky.core.network.HomeFeedPrefs
import io.nativestarter.showcase.bluesky.core.network.FeedViewPost
import io.nativestarter.showcase.bluesky.feature.profile.ProfileScreen
import io.nativestarter.showcase.bluesky.feature.profile.ProfileUiState
import io.nativestarter.showcase.bluesky.feature.profile.ProfileCallbacks
import io.nativestarter.showcase.bluesky.feature.profile.ProfileErrorCopy
import io.nativestarter.showcase.bluesky.feature.thread.ThreadScreen
import io.nativestarter.showcase.bluesky.feature.thread.ThreadUiState
import io.nativestarter.showcase.bluesky.feature.thread.ThreadCallbacks
import io.nativestarter.showcase.bluesky.feature.thread.ThreadErrorCopy
import io.nativestarter.showcase.bluesky.feature.thread.ThreadItem
import io.nativestarter.showcase.bluesky.feature.thread.ThreadPostUi
import io.nativestarter.showcase.bluesky.feature.thread.ThreadViewModel
import io.nativestarter.showcase.bluesky.shell.BottomBar
import io.nativestarter.showcase.bluesky.shell.Routes

private fun post(handle: String, name: String, text: String, like: String? = null, likeCount: Int = 0) = PostView(
    uri = "at://did:plc:$handle/app.bsky.feed.post/$text",
    cid = "cid",
    author = ProfileViewBasic("did:plc:$handle", handle, name),
    record = PostRecord(text),
    likeCount = likeCount,
    indexedAt = "2026-09-15T00:00:00.000Z",
    viewer = PostViewerState(like = like),
)

/** A lifecycle this test drives by hand. `createUnsafe` skips the main-thread check of the setter. */
private class FakeLifecycleOwner : LifecycleOwner {
    val registry: LifecycleRegistry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
    override val lifecycle: Lifecycle get() = registry
}

/** Compose UI tests: one per screen, asserting the RN testIDs the shared Maestro flow relies on. */
@RunWith(AndroidJUnit4::class)
class ScreenTests {
    @get:Rule val rule = createComposeRule()

    private val opened = mutableListOf<String>()
    private val liked = mutableListOf<String>()
    private val actions = PostActions(
        onOpen = { opened += it.record.text },
        onToggleLike = { liked += it.record.text },
        onToggleRepost = {},
    )

    @Test
    fun homeFeedShowsRnTestTagsAndOpensThread() {
        val feed = listOf(
            FeedViewPost(post = post("bob.test", "Bob", "Thread root")),
            FeedViewPost(post = post("carla.test", "Carla", "Thread reply")),
            FeedViewPost(post = post("alice.test", "Alice", "Post")),
        )
        val slices = FeedTuner.following(HomeFeedPrefs(), "did:plc:me").tune(feed)
        val state = FeedUiState(rows = PostFeedRows.build(slices, withComposerPrompt = true), isFetched = true, isFetching = false)
        rule.setContent { HomeScreen(state, HomeCallbacks(postActions = { actions })) }

        // RN: `<Layout.Screen testID="HomeScreen">` wraps `<Pager testID="homeScreen">` (review A7).
        rule.onNodeWithTag("HomeScreen").assertIsDisplayed()
        rule.onNodeWithTag("homeScreen").assertIsDisplayed()
        rule.onNodeWithTag("homeScreenFeedTabs").assertIsDisplayed()
        // RN `<ContentHider testID="contentHider-post">`, once per feed row (review A7).
        rule.onAllNodes(hasTestTag("contentHider-post"), useUnmergedTree = true).assertCountEquals(3)
        rule.onNodeWithTag("feedItem-by-bob.test").assertIsDisplayed()
        rule.onNodeWithTag("feedItem-by-carla.test").assertIsDisplayed()
        rule.onNodeWithTag("feedItem-by-alice.test").assertIsDisplayed()
        rule.onNodeWithText("What's up?", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Thread root", useUnmergedTree = true).performClick()
        assertEquals(listOf("Thread root"), opened)
    }

    @Test
    fun homeFirstLoadErrorShowsRetryBanner() {
        var retried = 0
        val state = FeedUiState(isFetched = true, isFetching = false, isEmpty = true, error = java.io.IOException("offline"))
        rule.setContent { HomeScreen(state, HomeCallbacks(onRetry = { retried++ })) }
        rule.onNodeWithTag("errorMessageView").assertIsDisplayed()
        rule.onNodeWithText("Unable to connect. Please check your internet connection and try again.", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("errorMessageTryAgainButton").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun threadShowsAnchorRepliesComposePromptAndLikes() {
        val anchor = post("bob.test", "Bob", "Thread root")
        val reply = post("carla.test", "Carla", "Thread reply")
        val state = ThreadUiState(
            items = listOf(
                ThreadItem.Post(anchor.uri, 0, anchor, ui = ThreadPostUi(isAnchor = true)),
                ThreadItem.ReplyComposer,
                ThreadItem.Post(reply.uri, 1, reply, ui = ThreadPostUi(indent = 1, isLastChild = true)),
            ),
            anchor = anchor,
            isFetching = false,
            isPlaceholderData = false,
            deferParents = false,
        )
        var replied = 0
        rule.setContent { ThreadScreen(state, ThreadCallbacks(onReplyToAnchor = { replied++ }, postActions = { actions })) }

        rule.onNodeWithTag("postThreadItem-by-bob.test").assertIsDisplayed()
        rule.onNodeWithTag("postThreadItem-by-carla.test").assertIsDisplayed()
        rule.onNodeWithText("Everybody can reply", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Post").assertIsDisplayed()
        rule.onAllNodes(hasTestTag("likeBtn"), useUnmergedTree = true)[0].performClick()
        assertEquals(listOf("Thread root"), liked)
        rule.onNodeWithText("Write your reply", useUnmergedTree = true).performClick()
        assertEquals(1, replied)
    }

    @Test
    fun threadErrorRetries() {
        var retried = 0
        val state = ThreadUiState(
            error = ThreadErrorCopy("Error loading post", ThreadViewModel.DEFAULT_MESSAGE),
            isFetching = false,
            isPlaceholderData = false,
        )
        rule.setContent { ThreadScreen(state, ThreadCallbacks(onRetry = { retried++ })) }
        rule.onNodeWithText("Error loading post", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText(ThreadViewModel.DEFAULT_MESSAGE, useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Retry", useUnmergedTree = true).performClick()
        assertEquals(1, retried)
    }

    @Test
    fun threadTombstoneAndShowMoreReplies() {
        var shown = 0
        val state = ThreadUiState(
            items = listOf(ThreadItem.NotFound("at://did:plc:x/app.bsky.feed.post/gone", 0), ThreadItem.ShowOtherReplies),
            isFetching = false,
            isPlaceholderData = false,
            deferParents = false,
        )
        rule.setContent { ThreadScreen(state, ThreadCallbacks(onShowOtherReplies = { shown++ })) }
        rule.onNodeWithText("Post not found", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Show more replies", useUnmergedTree = true).performClick()
        assertEquals(1, shown)
    }

    private val aliceProfile = ProfileViewDetailed(
        did = "did:plc:alice", handle = "alice.test", displayName = "Alice", description = "Test user 1",
        followersCount = 2, followsCount = 2, postsCount = 1,
    )

    @Test
    fun profileShowsHeaderPostsTabAndEndOfFeed() {
        val slices = FeedTuner.author().tune(listOf(FeedViewPost(post = post("alice.test", "Alice", "Post"))))
        val state = ProfileUiState(
            profile = aliceProfile,
            isProfileLoading = false,
            feed = FeedUiState(rows = PostFeedRows.build(slices, withComposerPrompt = false), isFetched = true, isFetching = false),
        )
        rule.setContent { ProfileScreen(state, ProfileCallbacks(postActions = { actions }), currentAccountDid = "did:plc:alice") }

        rule.onNodeWithTag("profileScreen").assertIsDisplayed()
        rule.onNodeWithTag("profileView").assertIsDisplayed()
        rule.onNodeWithTag("postsFeed-flatlist").assertIsDisplayed()
        rule.onNodeWithTag("profileHeaderDisplayName", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("profileHeaderEditProfileButton").assertIsDisplayed()
        rule.onNodeWithTag("profileHeaderDescription", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("profileHeaderFollowersButton").assertIsDisplayed()
        rule.onNodeWithText("Starter Packs", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("feedItem-by-alice.test").assertIsDisplayed()
        rule.onNodeWithText("End of feed", useUnmergedTree = true).assertExists()
    }

    @Test
    fun profileErrorScreenRetries() {
        var retried = 0
        val state = ProfileUiState(isProfileLoading = false, profileError = ProfileErrorCopy("Not Found", "Profile not found"))
        rule.setContent { ProfileScreen(state, ProfileCallbacks(onRetryProfile = { retried++ })) }
        rule.onNodeWithTag("profileErrorScreen").assertIsDisplayed()
        rule.onNodeWithText("Not Found", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("errorScreenTryAgainButton").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun profileEmptyPostsOffersWriteAPost() {
        val opened = mutableListOf<String>()
        val state = ProfileUiState(
            profile = aliceProfile,
            isProfileLoading = false,
            feed = FeedUiState(isFetched = true, isFetching = false, isEmpty = true),
        )
        rule.setContent { ProfileScreen(state, ProfileCallbacks(onNotInShowcase = { opened += it }), currentAccountDid = "did:plc:alice") }
        rule.onNodeWithText("No posts yet", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Write a post", useUnmergedTree = true).performClick()
        assertEquals(listOf("New post"), opened)
    }

    /**
     * RN logs `home:feedDisplayed {reason:"focus"}` from `useFocusEffect`, which is navigation focus, not the
     * Android lifecycle. Returning from the device Home key must log nothing (review A1).
     */
    @Test
    fun homeFeedDisplayedFiresOnNavigationFocusNotOnLifecycleResume() {
        val analytics = RecordingAnalytics()
        val owner = FakeLifecycleOwner()
        var entryId by mutableStateOf(0)
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                // The shell composes only the current back-stack entry, under `key(entry.id)`.
                key(entryId) { HomeFeedDisplayedEffect(homeReady = true, selectedTab = 0, analytics = analytics) }
            }
        }
        rule.runOnIdle { assertEquals(listOf("home:feedDisplayed"), analytics.names()) }
        rule.runOnIdle { owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE) }
        rule.runOnIdle { owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME) }
        rule.runOnIdle { assertEquals("an Activity resume is not a navigation focus", 1, analytics.events.size) }
        // Navigating back to Home composes the entry again, which is a focus.
        rule.runOnIdle { entryId = 1 }
        rule.runOnIdle {
            assertEquals(2, analytics.events.size)
            assertEquals(
                mapOf<String, Any?>("index" to 0, "feedType" to "following", "feedUrl" to "following", "reason" to "focus"),
                analytics.events.last().second,
            )
        }
    }

    @Test
    fun homeFeedDisplayedWaitsForPreferencesAndPinnedFeeds() {
        val analytics = RecordingAnalytics()
        var ready by mutableStateOf(false)
        rule.setContent { HomeFeedDisplayedEffect(homeReady = ready, selectedTab = 0, analytics = analytics) }
        rule.runOnIdle { assertEquals(0, analytics.events.size) }
        rule.runOnIdle { ready = true }
        rule.runOnIdle { assertEquals(1, analytics.events.size) }
    }

    @Test
    fun bottomBarHasRnTestTagsAndBadge() {
        val selected = mutableListOf<String>()
        rule.setContent { BottomBar(selected = Routes.HOME, badge = "1", onSelect = { selected += it }) }
        listOf("bottomBarHomeBtn", "bottomBarSearchBtn", "bottomBarMessagesBtn", "bottomBarNotificationsBtn", "bottomBarProfileBtn")
            .forEach { rule.onNodeWithTag(it).assertIsDisplayed() }
        rule.onNodeWithText("1", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("bottomBarProfileBtn").performClick()
        assertEquals(listOf(Routes.PROFILE), selected)
    }
}
