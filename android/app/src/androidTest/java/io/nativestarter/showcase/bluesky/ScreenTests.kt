package io.nativestarter.showcase.bluesky

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import io.nativestarter.showcase.bluesky.feature.feed.FeedRow
import io.nativestarter.showcase.bluesky.feature.feed.FeedSlice
import io.nativestarter.showcase.bluesky.feature.feed.FeedUiState
import io.nativestarter.showcase.bluesky.feature.feed.HomeScreen
import io.nativestarter.showcase.bluesky.feature.profile.ProfileScreen
import io.nativestarter.showcase.bluesky.feature.profile.ProfileUiState
import io.nativestarter.showcase.bluesky.feature.thread.ThreadScreen
import io.nativestarter.showcase.bluesky.feature.thread.ThreadUiState
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
        val root = post("bob.test", "Bob", "Thread root")
        val reply = post("carla.test", "Carla", "Thread reply")
        val state = FeedUiState(
            loading = false,
            slices = listOf(
                FeedSlice("t", listOf(FeedRow(root, isThreadParent = true), FeedRow(reply, isThreadChild = true))),
                FeedSlice("a", listOf(FeedRow(post("alice.test", "Alice", "Post")))),
            ),
            reachedEnd = true,
        )
        rule.setContent { HomeScreen(state, actions, onRefresh = {}, onSelectTab = {}, onNotInShowcase = {}) }

        rule.onNodeWithTag("homeScreen").assertIsDisplayed()
        rule.onNodeWithTag("homeScreenFeedTabs").assertIsDisplayed()
        rule.onNodeWithTag("feedItem-by-bob.test").assertIsDisplayed()
        rule.onNodeWithTag("feedItem-by-carla.test").assertIsDisplayed()
        rule.onNodeWithTag("feedItem-by-alice.test").assertIsDisplayed()
        rule.onNodeWithText("What's up?").assertIsDisplayed()
        rule.onNodeWithText("Thread root", useUnmergedTree = true).performClick()
        assertEquals(listOf("Thread root"), opened)
    }

    @Test
    fun threadShowsAnchorRepliesAndLikes() {
        val anchor = post("bob.test", "Bob", "Thread root")
        val state = ThreadUiState(loading = false, anchor = anchor, replies = listOf(post("carla.test", "Carla", "Thread reply")))
        rule.setContent { ThreadScreen(state, actions, onBack = {}, onRefresh = {}) }

        rule.onNodeWithTag("postThreadItem-by-bob.test").assertIsDisplayed()
        rule.onNodeWithTag("postThreadItem-by-carla.test").assertIsDisplayed()
        rule.onNodeWithText("Everybody can reply", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Post").assertIsDisplayed()
        rule.onAllNodes(hasTestTag("likeBtn"), useUnmergedTree = true)[0].performClick()
        assertEquals(listOf("Thread root"), liked)
    }

    @Test
    fun profileShowsHeaderAndPostsTab() {
        val profile = ProfileViewDetailed(
            did = "did:plc:alice", handle = "alice.test", displayName = "Alice", description = "Test user 1",
            followersCount = 2, followsCount = 2, postsCount = 1,
        )
        val state = ProfileUiState(
            loading = false,
            profile = profile,
            posts = listOf(FeedSlice("p", listOf(FeedRow(post("alice.test", "Alice", "Post"))))),
            reachedEnd = true,
        )
        rule.setContent { ProfileScreen(state, actions, onRefresh = {}, onSelectTab = {}) }

        rule.onNodeWithTag("profileView").assertIsDisplayed()
        rule.onNodeWithTag("profileHeaderDisplayName", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("profileHeaderEditProfileButton").assertIsDisplayed()
        rule.onNodeWithTag("profileHeaderDescription", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Starter Packs", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("feedItem-by-alice.test").assertIsDisplayed()
    }

    @Test
    fun bottomBarHasRnTestTagsAndBadge() {
        val selected = mutableListOf<String>()
        rule.setContent { BottomBar(selected = Routes.HOME, unread = 1, onSelect = { selected += it }) }
        listOf("bottomBarHomeBtn", "bottomBarSearchBtn", "bottomBarMessagesBtn", "bottomBarNotificationsBtn", "bottomBarProfileBtn")
            .forEach { rule.onNodeWithTag(it).assertIsDisplayed() }
        rule.onNodeWithText("1", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("bottomBarProfileBtn").performClick()
        assertEquals(listOf(Routes.PROFILE), selected)
    }
}
