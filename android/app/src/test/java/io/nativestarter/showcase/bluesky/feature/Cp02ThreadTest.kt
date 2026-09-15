package io.nativestarter.showcase.bluesky.feature

import io.nativestarter.showcase.bluesky.core.analytics.RecordingAnalytics
import io.nativestarter.showcase.bluesky.core.network.FeedResponse
import io.nativestarter.showcase.bluesky.core.network.PostCache
import io.nativestarter.showcase.bluesky.core.network.PostInteractions
import io.nativestarter.showcase.bluesky.core.network.PostRecord
import io.nativestarter.showcase.bluesky.core.network.PostSource
import io.nativestarter.showcase.bluesky.core.network.PostSources
import io.nativestarter.showcase.bluesky.core.network.PostView
import io.nativestarter.showcase.bluesky.core.network.PreferencesCache
import io.nativestarter.showcase.bluesky.core.network.UserPreferences
import io.nativestarter.showcase.bluesky.core.network.PostViewerState
import io.nativestarter.showcase.bluesky.core.network.ProfileViewBasic
import io.nativestarter.showcase.bluesky.core.network.ReplyRef
import io.nativestarter.showcase.bluesky.core.network.StrongRef
import io.nativestarter.showcase.bluesky.core.network.ThreadItemV2
import io.nativestarter.showcase.bluesky.core.network.ThreadOtherV2Response
import io.nativestarter.showcase.bluesky.core.network.ThreadV2Response
import io.nativestarter.showcase.bluesky.core.network.XrpcException
import io.nativestarter.showcase.bluesky.core.network.XrpcJson
import io.nativestarter.showcase.bluesky.feature.feed.FeedViewModel
import io.nativestarter.showcase.bluesky.feature.feed.PostFeedRow
import io.nativestarter.showcase.bluesky.feature.thread.ApiThreadItem
import io.nativestarter.showcase.bluesky.feature.thread.ThreadErrorCopy
import io.nativestarter.showcase.bluesky.feature.thread.ThreadItem
import io.nativestarter.showcase.bluesky.feature.thread.ThreadPostUi
import io.nativestarter.showcase.bluesky.feature.thread.ThreadTraversal
import io.nativestarter.showcase.bluesky.feature.thread.ThreadViewModel
import io.nativestarter.showcase.bluesky.feature.thread.ThreadWindow
import io.nativestarter.showcase.bluesky.testing.FakeBskyRepository
import io.nativestarter.showcase.bluesky.testing.Fixtures
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** CP-02: RN `sortAndAnnotateThreadItems`, `buildThread`, the `slices` window and the `PostThread` view model. */
@OptIn(ExperimentalCoroutinesApi::class)
class Cp02ThreadTest {
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

    private fun postView(uri: String, author: String, parent: String?, replyCount: Int, replyDisabled: Boolean = false) = PostView(
        uri = uri,
        cid = "cid",
        author = ProfileViewBasic("did:plc:$author", "$author.test"),
        record = PostRecord(text = uri.substringAfterLast('/'), reply = parent?.let { ReplyRef(StrongRef(SYNTHETIC_ROOT, "c"), StrongRef(it, "c")) }),
        replyCount = replyCount,
        indexedAt = "2026-09-15T00:00:00.000Z",
        viewer = PostViewerState(replyDisabled = replyDisabled),
    )

    private fun item(
        name: String,
        depth: Int,
        parent: String? = null,
        replyCount: Int = 0,
        moreReplies: Int = 0,
        moreParents: Boolean = false,
        author: String = "bob",
        mutedByViewer: Boolean = false,
    ): ThreadItemV2 {
        val uri = "at://did:plc:$author/app.bsky.feed.post/$name"
        return ThreadItemV2(
            uri,
            depth,
            buildJsonObject {
                put("\$type", "app.bsky.unspecced.defs#threadItemPost")
                put("post", XrpcJson.encodeToJsonElement(PostView.serializer(), postView(uri, author, parent, replyCount)))
                put("moreParents", moreParents)
                put("moreReplies", moreReplies)
                put("opThread", false)
                put("hiddenByThreadgate", false)
                put("mutedByViewer", mutedByViewer)
            },
        )
    }

    private fun uriOf(name: String, author: String = "bob") = "at://did:plc:$author/app.bsky.feed.post/$name"

    private fun tombstone(name: String, depth: Int, type: String) =
        ThreadItemV2(uriOf(name), depth, buildJsonObject { put("\$type", "app.bsky.unspecced.defs#$type") })

    private fun build(thread: List<ThreadItemV2>, visible: Boolean = false, hasOther: Boolean = false): List<ThreadItem> {
        val annotated = ThreadTraversal.sortAndAnnotate(ThreadTraversal.parse(thread), me)
        return ThreadTraversal.buildThread(annotated.threadItems, annotated.otherThreadItems, emptyList(), false, visible, hasOther)
    }

    private fun List<ThreadItem>.shape() = map {
        when (it) {
            is ThreadItem.Post -> "post(${it.depth}) ${it.uri.substringAfterLast('/')}"
            is ThreadItem.NotFound -> "notFound"
            is ThreadItem.Blocked -> "blocked"
            is ThreadItem.NoUnauthenticated -> "noUnauthenticated"
            is ThreadItem.ReadMore -> "readMore ${it.moreReplies}"
            is ThreadItem.ReadMoreUp -> "readMoreUp"
            is ThreadItem.ReplyComposer -> "replyComposer"
            is ThreadItem.ShowOtherReplies -> "showOtherReplies"
            is ThreadItem.Skeleton -> "skeleton ${it.kind}"
        }
    }

    @Test
    fun rootAnchorFixtureBuildsAnchorComposerAndReply() {
        val items = build(Fixtures.decode<ThreadV2Response>("getPostThreadV2.json").thread)
        assertEquals(listOf("post(0) 3mvjvgylorj2e", "replyComposer", "post(1) 3mvjvgylsoj2e"), items.shape())
        assertEquals(ThreadPostUi(isAnchor = true), (items[0] as ThreadItem.Post).ui)
        assertEquals("a reply after the anchor has no parent line (prev depth 0)", ThreadPostUi(indent = 1, isLastChild = true), (items[2] as ThreadItem.Post).ui)
    }

    @Test
    fun replyAnchorFixturePutsTheParentFirstWithAChildLine() {
        val items = build(Fixtures.decode<ThreadV2Response>("getPostThreadV2-reply.json").thread)
        assertEquals(listOf("post(-1) 3mvjvgylorj2e", "post(0) 3mvjvgylsoj2e", "replyComposer"), items.shape())
        assertEquals(ThreadPostUi(showChildReplyLine = true, indent = -1), (items[0] as ThreadItem.Post).ui)
    }

    @Test
    fun notFoundAnchorFixtureIsATombstoneView() {
        val items = build(Fixtures.decode<ThreadV2Response>("getPostThreadV2-notfound.json").thread)
        assertEquals(listOf("notFound"), items.shape())
        assertTrue(ThreadTraversal.isTombstoneView(items))
        assertFalse(ThreadTraversal.isTombstoneView(build(Fixtures.decode<ThreadV2Response>("getPostThreadV2.json").thread)))
    }

    @Test
    fun blockedParentStopsTheWalkUp() {
        val items = build(
            listOf(
                item("p2", -2),
                tombstone("p1", -1, "threadItemBlocked"),
                item("a", 0, parent = uriOf("p1")),
            ),
        )
        assertEquals(listOf("blocked", "post(0) a", "replyComposer"), items.shape())
    }

    @Test
    fun moreParentsAddsContinueThreadAboveTheFirstParent() {
        val items = build(listOf(item("p1", -1, moreParents = true), item("a", 0, parent = uriOf("p1"))))
        assertEquals(listOf("readMoreUp", "post(-1) p1", "post(0) a", "replyComposer"), items.shape())
        assertTrue((items[1] as ThreadItem.Post).ui.showParentReplyLine)
    }

    @Test
    fun unhydratedRepliesAddReadMoreAfterTheLastChild() {
        val items = build(
            listOf(
                item("a", 0, replyCount = 3),
                item("r1", 1, parent = uriOf("a"), replyCount = 2, moreReplies = 2),
            ),
        )
        assertEquals(listOf("post(0) a", "replyComposer", "post(1) r1", "readMore 2"), items.shape())
        val reply = items[2] as ThreadItem.Post
        assertTrue(reply.ui.precedesChildReadMore)
        assertTrue(reply.ui.showChildReplyLine)
    }

    @Test
    fun mutedTopLevelReplyMovesBehindShowMoreReplies() {
        val thread = listOf(
            item("a", 0, replyCount = 3),
            item("r1", 1, parent = uriOf("a"), author = "carla", mutedByViewer = true),
            item("r2", 1, parent = uriOf("a"), author = "dan"),
            item("own", 1, parent = uriOf("a"), author = "alice", mutedByViewer = true),
        )
        assertEquals(listOf("post(0) a", "replyComposer", "post(1) r2", "post(1) own", "showOtherReplies"), build(thread).shape())
        assertEquals(listOf("post(0) a", "replyComposer", "post(1) r2", "post(1) own", "post(1) r1"), build(thread, visible = true).shape())
        assertEquals(
            "hasOtherReplies alone shows the button",
            "showOtherReplies",
            build(listOf(item("a", 0)), hasOther = true).shape().last(),
        )
    }

    @Test
    fun loadingShowsTheCachedAnchorOrSkeletons() {
        val cached = ThreadTraversal.sortAndAnnotate(listOf(ApiThreadItem.placeholder(postView(uriOf("a"), "bob", null, replyCount = 2))), me)
        assertEquals(
            listOf("post(0) a", "replyComposer", "skeleton Reply", "skeleton Reply"),
            ThreadTraversal.buildThread(cached.threadItems, emptyList(), emptyList(), true, false, false).shape(),
        )
        val disabled = ThreadTraversal.sortAndAnnotate(listOf(ApiThreadItem.placeholder(postView(uriOf("a"), "bob", null, 0, replyDisabled = true))), me)
        assertEquals(listOf("post(0) a"), ThreadTraversal.buildThread(disabled.threadItems, emptyList(), emptyList(), true, false, false).shape())
        assertEquals(
            listOf("skeleton Anchor", "skeleton ReplyComposer") + List(4) { "skeleton Reply" },
            ThreadTraversal.buildThread(emptyList(), emptyList(), emptyList(), true, false, false).shape(),
        )
    }

    @Test
    fun slicesDeferParentsAndLimitParentsAndChildren() {
        val items = build(
            listOf(
                item("p3", -3),
                item("p2", -2, parent = uriOf("p3")),
                item("p1", -1, parent = uriOf("p2")),
                item("a", 0, parent = uriOf("p1")),
                item("r1", 1, parent = uriOf("a")),
            ),
        )
        val deferred = ThreadTraversal.slices(items, ThreadWindow(deferParents = true))
        assertEquals(listOf("post(0) a", "replyComposer", "post(1) r1"), deferred.items.shape())
        assertEquals(3, deferred.totalParentCount)
        assertEquals(2, deferred.totalChildrenCount)
        assertEquals(
            "RN limit = max(0, start - maxParentCount): maxParentCount + 1 parents",
            listOf("post(-2) p2", "post(-1) p1", "post(0) a", "replyComposer", "post(1) r1"),
            ThreadTraversal.slices(items, ThreadWindow(deferParents = false, maxParentCount = 1)).items.shape(),
        )
        assertEquals(
            listOf("post(0) a", "replyComposer"),
            ThreadTraversal.slices(items, ThreadWindow(deferParents = true, maxChildrenCount = 0)).items.shape(),
        )
    }

    @Test
    fun threadLoadsWithThePreferredSortAndLogsTheAnchorView() = runTest {
        val vm = ThreadViewModel(ROOT, repo, interactions, analytics, me, PostCache(), PostSource("following", null))
        val s = vm.state.value
        assertEquals("the preferences fixture has no threadViewPref, so RN normalizes to top", listOf("postThreadV2:top"), repo.calls.filter { it.startsWith("postThread") })
        assertFalse(s.isPlaceholderData)
        assertNull(s.error)
        assertEquals(listOf("post(0) 3mvjvgylorj2e", "replyComposer", "post(1) 3mvjvgylsoj2e"), s.items.shape())
        val view = analytics.events.single { it.first == "post:view" }.second
        assertEquals(
            mapOf("uri" to ROOT, "authorDid" to "did:plc:k66ibq3qlbxvxrjlhkv4u2oc", "isReply" to false, "logContext" to "Post", "feedDescriptor" to "following"),
            view,
        )
    }

    @Test
    fun cachedPostIsThePlaceholderUntilTheThreadArrives() = runTest {
        val cache = PostCache().apply { put(Fixtures.decode<FeedResponse>("getTimeline.json").feed.map { it.post }) }
        val gate = CompletableDeferred<Unit>()
        repo.readGate = gate
        val vm = ThreadViewModel(ROOT, repo, interactions, analytics, me, cache, null)
        assertTrue(vm.state.value.isPlaceholderData)
        assertEquals("replyCount 1 gives one reply skeleton", listOf("post(0) 3mvjvgylorj2e", "replyComposer", "skeleton Reply"), vm.state.value.items.shape())
        assertEquals(1, analytics.events.count { it.first == "post:view" })
        gate.complete(Unit)
        assertFalse(vm.state.value.isPlaceholderData)
        assertEquals(listOf("post(0) 3mvjvgylorj2e", "replyComposer", "post(1) 3mvjvgylsoj2e"), vm.state.value.items.shape())
        assertEquals("the same anchor logs once", 1, analytics.events.count { it.first == "post:view" })
    }

    @Test
    fun errorShowsRnCopyAndRetryRecovers() = runTest {
        repo.failReads = true
        val vm = ThreadViewModel(ROOT, repo, interactions, analytics, me)
        assertEquals(ThreadErrorCopy("Error loading post", ThreadViewModel.DEFAULT_MESSAGE), vm.state.value.error)
        assertTrue(vm.state.value.items.isEmpty())
        repo.failReads = false
        vm.retry()
        assertNull(vm.state.value.error)
        assertEquals(3, vm.state.value.items.size)
        assertEquals(
            ThreadErrorCopy("Post not found", "Post not found: at://x"),
            ThreadViewModel.errorCopy(XrpcException(400, "NotFound", "Post not found: at://x")),
        )
    }

    @Test
    fun showMoreRepliesLoadsOtherRepliesAndLogs() = runTest {
        repo.threadV2Response = Fixtures.decode<ThreadV2Response>("getPostThreadV2.json").copy(hasOtherReplies = true)
        repo.otherV2Response = ThreadOtherV2Response(listOf(item("other", 1, parent = ROOT, author = "dan")))
        val vm = ThreadViewModel(ROOT, repo, interactions, analytics, me)
        assertEquals("showOtherReplies", vm.state.value.items.shape().last())
        vm.showOtherReplies()
        assertTrue(repo.calls.contains("postThreadOtherV2"))
        assertEquals("post(1) other", vm.state.value.items.shape().last())
        assertEquals(listOf("thread:click:showOtherReplies"), analytics.names().filter { it.startsWith("thread:click") })
    }

    @Test
    fun likeInTheThreadIsSharedWithTheFeed() = runTest {
        val feed = FeedViewModel(repo, interactions, analytics, me)
        val thread = ThreadViewModel(ROOT, repo, interactions, analytics, me, PostCache(), PostSource("following", null))
        thread.toggleLike(thread.state.value.anchor!!)
        assertEquals(1, thread.state.value.anchor!!.likeCount)
        val feedRoot = feed.state.value.rows.filterIsInstance<PostFeedRow.SliceItemRow>().first { it.post.uri == ROOT }.post
        assertEquals(1, feedRoot.likeCount)
        assertNotNull(feedRoot.viewer.like)
        val like = analytics.events.single { it.first == "post:like" }.second
        assertEquals("PostThreadItem", like["logContext"])
        assertEquals("following", like["feedDescriptor"])
    }

    @Test
    fun repliesLogPostViewOnceAndOnlyTheAnchorUsesVia() = runTest {
        val via = StrongRef("at://did:plc:carla/app.bsky.feed.repost/r", "rc")
        val vm = ThreadViewModel(ROOT, repo, interactions, analytics, me, PostCache(), PostSource("following", via))
        val posts = vm.state.value.items.filterIsInstance<ThreadItem.Post>()
        posts.forEach(vm::onItemSeen)
        posts.forEach(vm::onItemSeen)
        val views = analytics.events.filter { it.first == "post:view" && it.second["logContext"] == "PostThreadItem" }
        assertEquals(1, views.size)
        assertEquals(true, views.single().second["isReply"])
        vm.toggleRepost(posts[0].post)
        vm.toggleRepost(posts[1].post)
        assertEquals(
            listOf("repost:$ROOT:via=${via.uri}", "repost:${posts[1].uri}:via=null"),
            repo.calls.filter { it.startsWith("repost:") },
        )
    }

    @Test
    fun preferencesLoadedByTheThreadLogOnceWithTheRawSort() = runTest {
        val cache = PreferencesCache()
        val vm = ThreadViewModel(ROOT, repo, interactions, analytics, me, PostCache(), PostSource("following", null), cache)
        val load = analytics.events.filter { it.first == "thread:preferences:load" }
        // The fixture has no threadViewPref: RN merges DEFAULT_THREAD_VIEW_PREFS, so sort is "hotness"; the request uses top.
        assertEquals(listOf(mapOf<String, Any?>("sort" to "hotness", "view" to "linear")), load.map { it.second })
        assertTrue(repo.calls.contains("postThreadV2:top"))
        vm.retry()
        assertEquals("once per screen", 1, analytics.events.count { it.first == "thread:preferences:load" })
        assertNotNull(cache.value)
    }

    @Test
    fun preferencesAlreadyLoadedByTheFeedLogNothing() = runTest {
        val cache = PreferencesCache()
        FeedViewModel(repo, interactions, analytics, me, PostCache(), cache)
        assertNotNull(cache.value)
        ThreadViewModel(ROOT, repo, interactions, analytics, me, PostCache(), null, cache)
        // RN: useState(serverPrefs) starts with the cached object and replaceEqualDeep keeps it on reload.
        assertFalse(analytics.names().contains("thread:preferences:load"))
    }

    @Test
    fun changedPreferencesOnReloadLogTheNewValues() = runTest {
        val cache = PreferencesCache().apply { put(UserPreferences()) }
        repo.preferencesResponse = UserPreferences(threadSort = "newest", threadSortRaw = "newest", treeViewEnabled = true)
        ThreadViewModel(ROOT, repo, interactions, analytics, me, PostCache(), null, cache)
        assertEquals(
            listOf(mapOf<String, Any?>("sort" to "newest", "view" to "tree")),
            analytics.events.filter { it.first == "thread:preferences:load" }.map { it.second },
        )
        assertTrue(repo.calls.contains("postThreadV2:newest"))
    }

    @Test
    fun failedPreferencesLogNothing() = runTest {
        repo.failPreferences = true
        ThreadViewModel(ROOT, repo, interactions, analytics, me, PostCache(), null, PreferencesCache())
        assertFalse("review N22", analytics.names().contains("thread:preferences:load"))
    }

    @Test
    fun postSourcesAreConsumedOnce() {
        val sources = PostSources()
        val source = PostSource("following", null)
        sources.set(ROOT, source)
        assertEquals(source, sources.consume(ROOT))
        assertNull(sources.consume(ROOT))
    }

    private companion object {
        const val ROOT = "at://did:plc:k66ibq3qlbxvxrjlhkv4u2oc/app.bsky.feed.post/3mvjvgylorj2e"
        const val SYNTHETIC_ROOT = "at://did:plc:bob/app.bsky.feed.post/root"
    }
}
