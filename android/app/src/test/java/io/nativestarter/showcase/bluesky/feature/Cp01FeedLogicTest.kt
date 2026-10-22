package io.nativestarter.showcase.bluesky.feature

import io.nativestarter.showcase.bluesky.core.network.ByteSlice
import io.nativestarter.showcase.bluesky.core.network.Facet
import io.nativestarter.showcase.bluesky.core.network.FeedResponse
import io.nativestarter.showcase.bluesky.core.network.FeedViewPost
import io.nativestarter.showcase.bluesky.core.network.XrpcJson
import io.nativestarter.showcase.bluesky.designsystem.text.DetectedFacet
import io.nativestarter.showcase.bluesky.designsystem.text.RichSegment
import io.nativestarter.showcase.bluesky.designsystem.text.RichTextModel
import io.nativestarter.showcase.bluesky.designsystem.text.Tlds
import io.nativestarter.showcase.bluesky.feature.feed.AuthorFeedFilter
import io.nativestarter.showcase.bluesky.feature.feed.FALLBACK_MARKER_URI
import io.nativestarter.showcase.bluesky.feature.feed.FeedUiState
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
    fun failedRefreshPutsBackTheEndOfFeedButNotTheFallbackMarker() = runTest(UnconfinedTestDispatcher()) {
        /*
         * RN `truncateAndInvalidate` keeps page 1 and its page param, then refetches. The truncation is a
         * cache write, not a successful fetch, so when the refetch fails TanStack re-emits the last
         * successful data and the truncation is undone.
         *
         * Measured on the device, not inferred. The React Native app was instrumented to log the gates it
         * renders from, and on the parity `error` flow it went, within 24 ms of the soft-reset:
         *
         *   hasNextPage=true  isError=true   pages=1
         *   hasNextPage=false isError=false  pages=2
         *
         * An earlier version of this test asserted the truncated state and named it RN's. It was wrong,
         * and it is what kept the `error home` screen failing: the port dropped the marker, held
         * `hasNextPage` true, drew no end-of-feed block and fired 44 `feed:endReached` events where React
         * Native fired none.
         */
        var failFollowing = false
        val source = HomeFeedSource { cursor ->
            if (failFollowing) error("HTTP 500")
            if (cursor == null) FeedResponse(listOf(itemFor(1)), "c1") else FeedResponse(emptyList(), null)
        }
        val pager = FeedPager(scope = this, fetch = source::fetch, selectedCount = { 1 })
        pager.refresh()
        pager.settleFill()
        assertEquals("auto-pagination loaded page 2 with the marker", 2, pager.state.value.pages.size)
        assertTrue(pager.state.value.items.any { it.post.uri == FALLBACK_MARKER_URI })
        failFollowing = true
        pager.refresh(isPullToRefresh = true)
        val s = pager.state.value
        assertEquals("page 1's posts are back", listOf(itemFor(1)), s.items)
        assertFalse(
            "the marker does not come back: RN counted 5 tuned slices before the refresh and 4 after, " +
                "and its captured screen has no \"We ran out of posts\" row",
            s.items.any { it.post.uri == FALLBACK_MARKER_URI },
        )
        assertFalse("page 2 ended Following, so there is no next page", s.hasNextPage)
        assertNull("a failed refresh does not error a feed that still has pages", s.error)
        assertFalse(s.isFetching)
        assertFalse(s.isRefreshing)
        // PostFeed rows: no retry row, and the end-of-feed block, which is what RN draws here.
        val ui = FeedUiState(
            rows = listOf(), isFetched = true, isFetching = false,
            hasNextPage = s.hasNextPage, isEmpty = false, error = s.error,
        )
        assertFalse(ui.showLoadMoreError)
        assertTrue("RN renders FollowingEndOfFeed on this screen", ui.showEndOfFeed)
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
        // RN reads `isError` off `useInfiniteQuery`, and TanStack Query reports `status: "error"`
        // only when a query has no successful data to show, so a `fetchNextPage` that fails after
        // page 1 has loaded leaves it at `success`. Confirmed against the server on the error flow:
        // the stale session gets HTTP 500 from getTimeline and 400 from refreshSession, so RN's
        // refresh fails there too, and RN still renders its cached posts with no error row.
        assertNull("a next-page failure does not error a feed that already has pages", s.error)
        assertTrue("hasNextPage stays true so the page can be tried again", s.hasNextPage)
        assertEquals(1, s.pages.size)
        assertTrue("and another attempt is allowed, as RN allows one on the next scroll", pager.canFetchNextPage())
        failNext = false
        pager.fetchNextPage()
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
        pager.settleFill()
        assertEquals("first page plus 5 attempts", 6, fetches)
        var repeated = 0
        val pager2 = FeedPager(this, fetch = { repeated++; FeedPage(emptyList(), "same") }, selectedCount = { 0 })
        pager2.refresh()
        pager2.settleFill()
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

    /**
     * Port check for `detectFacets` (@bsky/sdk dist/rich-text/detection.js), which RN runs client-side on text
     * that carries no facets, such as a profile description (review A8).
     */
    @Test
    fun detectFacetsPortsTheSdkRules() {
        val text = "see https://example.com/a, and @bob.test #tag \$nvda plus bare example.com/x"
        val bytes = text.toByteArray(Charsets.UTF_8)
        fun slice(f: DetectedFacet) = String(bytes, f.byteStart, f.byteEnd - f.byteStart, Charsets.UTF_8)
        val found = RichTextModel.detect(text)

        val mention = found.filterIsInstance<DetectedFacet.Mention>().single()
        assertEquals("bob.test", mention.handle)
        assertEquals("@bob.test", slice(mention))

        val links = found.filterIsInstance<DetectedFacet.Link>()
        assertEquals(listOf("https://example.com/a", "https://example.com/x"), links.map { it.uri })
        assertEquals("the trailing comma is not part of the link", "https://example.com/a", slice(links[0]))
        assertEquals("a bare domain keeps its text and gains the scheme", "example.com/x", slice(links[1]))

        val tags = found.filterIsInstance<DetectedFacet.Tag>()
        assertEquals("a cashtag is a tag with a \$ prefix, upper case", listOf("tag", "\$NVDA"), tags.map { it.tag })
        assertEquals("#tag", slice(tags[0]))

        // Offsets are UTF-8 bytes, so text before a facet can be wider than its UTF-16 length.
        val accented = "héllo #tag"
        val accentedTag = RichTextModel.detect(accented).filterIsInstance<DetectedFacet.Tag>().single()
        assertEquals(7, accentedTag.byteStart)
        assertEquals(
            "#tag",
            String(accented.toByteArray(Charsets.UTF_8), accentedTag.byteStart, accentedTag.byteEnd - accentedTag.byteStart, Charsets.UTF_8),
        )

        // isValidDomain gates mentions and bare domains; RN keeps ".test", which the dev network uses.
        assertTrue(Tlds.isValidDomain("example.com"))
        assertFalse("test is not an IANA TLD", Tlds.isValidDomain("bob.test"))
        assertTrue("an unknown TLD is not a mention", RichTextModel.detect("@bob.invalidtld").isEmpty())
        assertEquals("the seeded description has no facets", emptyList<DetectedFacet>(), RichTextModel.detect("Test user 1"))
    }

    private fun itemFor(n: Int): FeedViewPost = item(
        """{"post":${postJson("at://f/app.bsky.feed.post/$n", author("did:plc:friend", "friend.test", following = true))}}""",
    )

    /** What the screen's fill effect does: ask after each render until the pager stops topping up. */
    private fun FeedPager.settleFill() {
        repeat(8) { fillIfNeeded() }
    }

}
