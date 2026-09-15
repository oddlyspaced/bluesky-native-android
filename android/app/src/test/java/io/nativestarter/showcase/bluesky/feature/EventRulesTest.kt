package io.nativestarter.showcase.bluesky.feature

import io.nativestarter.showcase.bluesky.core.analytics.RecordingAnalytics
import io.nativestarter.showcase.bluesky.core.network.PostInteractions
import io.nativestarter.showcase.bluesky.core.network.UserPreferences
import io.nativestarter.showcase.bluesky.feature.feed.FeedViewModel
import io.nativestarter.showcase.bluesky.feature.feed.PostFeedRow
import io.nativestarter.showcase.bluesky.feature.feed.PostViewVisibility
import io.nativestarter.showcase.bluesky.shell.NavigationReadyEvents
import io.nativestarter.showcase.bluesky.testing.FakeBskyRepository
import io.nativestarter.showcase.bluesky.testing.Fixtures
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
import org.junit.Before
import org.junit.Test

/** Cross-platform event rules: shell remount (App.tsx key), ordered `post:view` (N6) and raw thread preferences. */
@OptIn(ExperimentalCoroutinesApi::class)
class EventRulesTest {
    private val analytics = RecordingAnalytics()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun navigationReadyLogsOnEveryAccountKeyChange() {
        var ms = 100L
        val events = NavigationReadyEvents(analytics) { ms }
        events.onAccountKey(null)
        events.onAccountKey(null)
        assertEquals("launch, then a recomposition with the same key", listOf("router:navigate", "init"), analytics.names())
        ms = 900L
        analytics.metric("account:loggedIn")
        events.onAccountKey("did:plc:alice")
        events.onAccountKey("did:plc:alice")
        assertEquals(
            "sign-in remounts the tree under key={currentAccount?.did}",
            listOf("router:navigate", "init", "account:loggedIn", "router:navigate", "init"),
            analytics.names(),
        )
        assertEquals(mapOf<String, Any?>("initMs" to 900L), analytics.events.last().second)
        assertEquals(emptyMap<String, Any?>(), analytics.events[3].second)
        events.onAccountKey(null)
        assertEquals("sign-out remounts again", 7, analytics.events.size)
    }

    @Test
    fun visibleRowsAreReportedTopToBottomAboveFortyPercent() {
        val items = listOf(
            PostViewVisibility.Item("c", index = 3, offset = 600, size = 200),
            PostViewVisibility.Item("a", index = 1, offset = 0, size = 300),
            PostViewVisibility.Item("d", index = 4, offset = 800, size = 250),
            PostViewVisibility.Item("b", index = 2, offset = 300, size = 300),
            PostViewVisibility.Item("top", index = 0, offset = -200, size = 300),
        )
        // Viewport 0..900: "d" shows 100 of 250 px (40%), "top" shows 100 of 300 px (33%).
        assertEquals(listOf("a", "b", "c", "d"), PostViewVisibility.visibleKeys(items, 0, 900))
        assertEquals(listOf("a", "b", "c"), PostViewVisibility.visibleKeys(items, 0, 899))
    }

    @Test
    fun orderedVisibilityGivesOrderedFeedPositions() = runTest {
        val repo = FakeBskyRepository()
        val interactions = PostInteractions(repo, CoroutineScope(Dispatchers.Main), analytics) { 2 }
        val vm = FeedViewModel(repo, interactions, analytics, "did:plc:alice")
        val rows = vm.state.value.rows
        val laidOut = rows.mapIndexed { i, row -> PostViewVisibility.Item(row.key, i, i * 100, 100) }.shuffled()
        val byKey = rows.filterIsInstance<PostFeedRow.SliceItemRow>().associateBy { it.key }
        PostViewVisibility.visibleKeys(laidOut, 0, rows.size * 100).forEach { key -> byKey[key]?.let(vm::onPostSeen) }
        assertEquals(listOf(0, 1, 2, 3), analytics.events.filter { it.first == "post:view" }.map { it.second["position"] })
    }

    @Test
    fun homeIsReadyOnlyAfterPreferencesAndPinnedFeedsLoad() = runTest {
        val repo = FakeBskyRepository()
        val interactions = PostInteractions(repo, CoroutineScope(Dispatchers.Main), analytics) { 2 }
        assertEquals(true, FeedViewModel(repo, interactions, analytics, "did:plc:alice").homeReady.value)
        val failing = FakeBskyRepository().apply { failPreferences = true }
        val failingInteractions = PostInteractions(failing, CoroutineScope(Dispatchers.Main), analytics) { 2 }
        assertFalse(
            "RN keeps HomeScreenReady unmounted without preferences, so home:feedDisplayed is not logged",
            FeedViewModel(failing, failingInteractions, analytics, "did:plc:alice").homeReady.value,
        )
    }

    @Test
    fun threadPreferencesKeepTheRawSortAndTreeFlag() {
        val none = UserPreferences.parse(Fixtures.text("getPreferences.json"))
        assertEquals("hotness", none.threadSortRaw)
        assertEquals("top", none.threadSort)
        assertFalse(none.treeViewEnabled)
        val set = UserPreferences.parse(
            """{"preferences":[{"${'$'}type":"app.bsky.actor.defs#threadViewPref","sort":"oldest","lab_treeViewEnabled":true}]}""",
        )
        assertEquals("oldest", set.threadSortRaw)
        assertEquals("oldest", set.threadSort)
        assertEquals(true, set.treeViewEnabled)
    }
}
