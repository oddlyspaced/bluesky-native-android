package io.nativestarter.showcase.bluesky.feature

import io.nativestarter.showcase.bluesky.core.network.ByteSlice
import io.nativestarter.showcase.bluesky.core.network.Facet
import io.nativestarter.showcase.bluesky.core.network.FeedResponse
import io.nativestarter.showcase.bluesky.core.network.FeedViewPost
import io.nativestarter.showcase.bluesky.core.network.XrpcJson
import io.nativestarter.showcase.bluesky.designsystem.text.RichSegment
import io.nativestarter.showcase.bluesky.designsystem.text.RichTextModel
import io.nativestarter.showcase.bluesky.feature.feed.AuthorFeedFilter
import io.nativestarter.showcase.bluesky.feature.feed.FeedPage
import io.nativestarter.showcase.bluesky.feature.feed.FeedPager
import io.nativestarter.showcase.bluesky.feature.feed.FeedReason
import io.nativestarter.showcase.bluesky.feature.feed.FeedTuner
import io.nativestarter.showcase.bluesky.core.network.HomeFeedPrefs
import io.nativestarter.showcase.bluesky.feature.feed.HomeFeedSource
import io.nativestarter.showcase.bluesky.feature.feed.PostFeedRow
import io.nativestarter.showcase.bluesky.feature.feed.PostFeedRows
import io.nativestarter.showcase.bluesky.testing.Fixtures
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Cp01FeedLogicTest {
    private val me = "did:plc:alice"

    private fun author(did: String, handle: String, following: Boolean = false) =
        """{"did":"$did","handle":"$handle","viewer":{${if (following) "\"following\":\"at://$me/app.bsky.graph.follow/x\"" else ""}}}"""

    private fun postJson(uri: String, authorJson: String, text: String = "t", replyParent: String? = null, replyRoot: String? = null) =
        """{"${'$'}type":"app.bsky.feed.defs#postView","uri":"$uri","cid":"c","author":$authorJson,
           "record":{"${'$'}type":"app.bsky.feed.post","text":"$text"${if (replyParent != null) ",\"reply\":{\"parent\":{\"uri\":\"$replyParent\",\"cid\":\"c\"},\"root\":{\"uri\":\"${replyRoot ?: replyParent}\",\"cid\":\"c\"}}" else ""}},
           "indexedAt":"2026-09-15T00:00:00.000Z"}"""

    private fun item(json: String): FeedViewPost = XrpcJson.decodeFromString(FeedViewPost.serializer(), json)

    @Test
    fun seededTimelineBuildsRnRows() {
        val feed = Fixtures.decode<FeedResponse>("getTimeline.json").feed
        val slices = FeedTuner.following(HomeFeedPrefs(), userDid = "did:plc:unused").tune(feed)
        assertEquals(4, slices.size)
        assertEquals(listOf("Thread root", "Thread reply"), slices[0].items.map { it.post.record.text })
        val rows = PostFeedRows.build(slices, withComposerPrompt = true)
        assertTrue(rows.first() is PostFeedRow.ComposerPrompt)
        val first = rows[1] as PostFeedRow.SliceItemRow
        val second = rows[2] as PostFeedRow.SliceItemRow
        assertTrue(first.isThreadParent && !first.isThreadChild)
        assertTrue(second.isThreadChild && second.isThreadLastChild)
        assertFalse("row 0 is the composer prompt, so the first post keeps its top border", first.hideTopBorder)
        assertTrue(first.showReplyTo)
        assertFalse(second.showReplyTo)
    }

    @Test
    fun followedRepliesOnlyHidesRepliesToStrangers() {
        val stranger = author("did:plc:stranger", "stranger.test")
        val friend = author("did:plc:friend", "friend.test", following = true)
        val strangerRoot = postJson("at://s/app.bsky.feed.post/1", stranger)
        val replyToStranger = item(
            """{"post":${postJson("at://f/app.bsky.feed.post/2", friend, replyParent = "at://s/app.bsky.feed.post/1")},
               "reply":{"root":$strangerRoot,"parent":$strangerRoot}}""",
        )
        val selfThread = item(
            """{"post":${postJson("at://f/app.bsky.feed.post/3", friend, replyParent = "at://f/app.bsky.feed.post/4")},
               "reply":{"root":${postJson("at://f/app.bsky.feed.post/4", friend)},"parent":${postJson("at://f/app.bsky.feed.post/4", friend)}}}""",
        )
        val tuned = FeedTuner.following(HomeFeedPrefs(), me).tune(listOf(replyToStranger, selfThread))
        assertEquals(listOf("at://f/app.bsky.feed.post/3"), tuned.map { it.feedPostUri })
        val withHideReplies = FeedTuner.following(HomeFeedPrefs(hideReplies = true), me).tune(listOf(replyToStranger, selfThread))
        assertEquals("removeReplies keeps same-author threads", listOf("at://f/app.bsky.feed.post/3"), withHideReplies.map { it.feedPostUri })
    }

    @Test
    fun orphansRepostsAndDedupFollowRn() {
        val friend = author("did:plc:friend", "friend.test", following = true)
        val orphan = item("""{"post":${postJson("at://f/app.bsky.feed.post/9", friend, replyParent = "at://x/app.bsky.feed.post/0")}}""")
        val repost = item(
            """{"post":${postJson("at://f/app.bsky.feed.post/5", friend)},
               "reason":{"${'$'}type":"app.bsky.feed.defs#reasonRepost","by":${author(me, "alice.test")},"uri":"at://$me/app.bsky.feed.repost/r","cid":"rc","indexedAt":"2026-09-15T01:00:00.000Z"}}""",
        )
        val sameRootA = item("""{"post":${postJson("at://f/app.bsky.feed.post/6", friend)}}""")
        val tuned = FeedTuner.following(HomeFeedPrefs(), me).tune(listOf(orphan, repost, sameRootA, sameRootA))
        assertEquals(listOf("at://f/app.bsky.feed.post/5", "at://f/app.bsky.feed.post/6"), tuned.map { it.feedPostUri })
        val reason = tuned[0].reason as FeedReason.Repost
        assertEquals("at://$me/app.bsky.feed.repost/r", reason.uri)
        assertEquals("rc", reason.cid)
        assertEquals(0, FeedTuner.following(HomeFeedPrefs(hideReposts = true), me).tune(listOf(repost)).size)
    }

    @Test
    fun incompleteThreadAddsViewFullThreadRow() {
        val friend = author("did:plc:friend", "friend.test", following = true)
        val root = postJson("at://f/app.bsky.feed.post/r", friend)
        val parent = postJson("at://f/app.bsky.feed.post/p", friend, replyParent = "at://f/app.bsky.feed.post/mid", replyRoot = "at://f/app.bsky.feed.post/r")
        val child = item(
            """{"post":${postJson("at://f/app.bsky.feed.post/c", friend, replyParent = "at://f/app.bsky.feed.post/p", replyRoot = "at://f/app.bsky.feed.post/r")},
               "reply":{"root":$root,"parent":$parent,"grandparentAuthor":$friend}}""",
        )
        val slices = FeedTuner.following(HomeFeedPrefs(), me).tune(listOf(child))
        assertTrue(slices.single().isIncompleteThread)
        val rows = PostFeedRows.build(slices, withComposerPrompt = false)
        assertEquals(4, rows.size)
        assertTrue(rows[1] is PostFeedRow.ViewFullThread)
        assertTrue("first row of the feed hides its top border", (rows[0] as PostFeedRow.SliceItemRow).hideTopBorder)
    }

    @Test
    fun authorFeedKeepsOnlyTheActorsOwnReplyChains() {
        val alice = author(me, "alice.test")
        val bob = author("did:plc:bob", "bob.test")
        val own = item("""{"post":${postJson("at://a/app.bsky.feed.post/1", alice)}}""")
        val replyToBob = item(
            """{"post":${postJson("at://a/app.bsky.feed.post/2", alice, replyParent = "at://b/app.bsky.feed.post/1")},
               "reply":{"root":${postJson("at://b/app.bsky.feed.post/1", bob)},"parent":${postJson("at://b/app.bsky.feed.post/1", bob)}}}""",
        )
        assertEquals(listOf("at://a/app.bsky.feed.post/1"), AuthorFeedFilter.filter(listOf(own, replyToBob), me).map { it.post.uri })
    }

    @Test
    fun homeSourceAddsFallbackMarkerWhenFollowingEnds() = runTest {
        val source = HomeFeedSource { cursor -> if (cursor == null) FeedResponse(emptyList(), null) else error("no page 2") }
        val page = source.fetch(null)
        assertNull(page.cursor)
        val slices = FeedTuner.following(HomeFeedPrefs(), me).tune(page.items)
        assertTrue("an empty Following feed still shows the marker row", slices.single().isFallbackMarker)
    }

    @Test
    fun refreshCancelsAPendingNextPageAndDropsItsResult() = runTest(UnconfinedTestDispatcher()) {
        val slowPage2 = CompletableDeferred<FeedPage>()
        var fetches = 0
        val pager = FeedPager(
            scope = this,
            fetch = { cursor ->
                fetches++
                if (cursor == null) FeedPage(listOf(itemFor(fetches)), "c1") else slowPage2.await()
            },
            selectedCount = { Int.MAX_VALUE },
        )
        pager.refresh()
        assertEquals(1, pager.state.value.pages.size)
        pager.fetchNextPage()
        assertTrue(pager.state.value.isFetchingNextPage)
        pager.refresh(isPullToRefresh = true)
        slowPage2.complete(FeedPage(listOf(itemFor(99)), null))
        val s = pager.state.value
        assertEquals("the stale page 2 was not appended", 1, s.pages.size)
        assertEquals("c1", s.cursor)
        assertFalse(s.isFetching)
    }

    @Test
    fun failedNextPageKeepsRowsAndHasNextPageForRetry() = runTest(UnconfinedTestDispatcher()) {
        var failNext = true
        val pager = FeedPager(
            scope = this,
            fetch = { cursor ->
                when {
                    cursor == null -> FeedPage(listOf(itemFor(1)), "c1")
                    failNext -> error("offline")
                    else -> FeedPage(listOf(itemFor(2)), null)
                }
            },
            selectedCount = { Int.MAX_VALUE },
        )
        pager.refresh()
        pager.fetchNextPage()
        var s = pager.state.value
        assertNotNull(s.error)
        assertTrue("hasNextPage stays true so the retry row can load it", s.hasNextPage)
        assertEquals(1, s.pages.size)
        assertFalse("onEndReached does nothing while in error", pager.canFetchNextPage())
        failNext = false
        pager.fetchNextPage(fromRetry = true)
        s = pager.state.value
        assertNull(s.error)
        assertEquals(2, s.pages.size)
        assertFalse(s.hasNextPage)
    }

    @Test
    fun autoPaginationStopsAtMaxAttemptsOrRepeatedCursor() = runTest(UnconfinedTestDispatcher()) {
        var fetches = 0
        val pager = FeedPager(this, fetch = { fetches++; FeedPage(emptyList(), "c${fetches}") }, selectedCount = { 0 })
        pager.refresh()
        assertEquals("first page plus 5 attempts", 6, fetches)
        var repeated = 0
        val pager2 = FeedPager(this, fetch = { repeated++; FeedPage(emptyList(), "same") }, selectedCount = { 0 })
        pager2.refresh()
        assertEquals("a repeated cursor ends pagination", 2, repeated)
        assertFalse(pager2.state.value.hasNextPage)
    }

    @Test
    fun richTextSegmentsUseUtf8ByteOffsets() {
        val text = "héllo @bob.test see https://example.com/some/long/path #tag"
        fun bytes(s: String) = s.toByteArray(Charsets.UTF_8).size
        val mentionStart = bytes("héllo ")
        val mentionEnd = mentionStart + bytes("@bob.test")
        val linkStart = bytes("héllo @bob.test see ")
        val linkEnd = linkStart + bytes("https://example.com/some/long/path")
        val tagStart = linkEnd + 1
        val tagEnd = tagStart + bytes("#tag")
        fun feature(vararg pairs: Pair<String, String>) = JsonObject(pairs.associate { it.first to JsonPrimitive(it.second) })
        val facets = listOf(
            Facet(ByteSlice(linkStart, linkEnd), listOf(feature("\$type" to "app.bsky.richtext.facet#link", "uri" to "https://example.com/some/long/path"))),
            Facet(ByteSlice(mentionStart, mentionEnd), listOf(feature("\$type" to "app.bsky.richtext.facet#mention", "did" to "did:plc:bob"))),
            Facet(ByteSlice(tagStart, tagEnd), listOf(feature("\$type" to "app.bsky.richtext.facet#tag", "tag" to "tag"))),
        )
        val segments = RichTextModel.segments(text, facets)
        assertEquals(
            listOf(
                RichSegment.Plain("héllo "),
                RichSegment.Mention("@bob.test", "did:plc:bob"),
                RichSegment.Plain(" see "),
                RichSegment.Link("https://example.com/some/long/path", "https://example.com/some/long/path"),
                RichSegment.Plain(" "),
                RichSegment.Tag("#tag", "tag"),
            ),
            segments,
        )
        // RN shortens only when the path is longer than 15 characters.
        assertEquals("example.com/some/long/path", RichTextModel.toShortUrl("https://example.com/some/long/path"))
        assertEquals("example.com/some/longer/...", RichTextModel.toShortUrl("https://example.com/some/longer/path"))
        assertEquals("example.com", RichTextModel.toShortUrl("https://example.com/"))
        assertEquals("mailto:x@y.z", RichTextModel.toShortUrl("mailto:x@y.z"))
        assertEquals("https://go.bsky.app/redirect?u=https%3A%2F%2Fexample.com%2F", RichTextModel.proxiedUrl("https://example.com/"))
        assertEquals(2, RichTextModel.countLines("a\nb\nc"))
    }

    private fun itemFor(n: Int): FeedViewPost = item(
        """{"post":${postJson("at://f/app.bsky.feed.post/$n", author("did:plc:friend", "friend.test", following = true))}}""",
    )
}
