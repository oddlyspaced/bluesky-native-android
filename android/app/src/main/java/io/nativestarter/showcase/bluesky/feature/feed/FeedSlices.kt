package io.nativestarter.showcase.bluesky.feature.feed

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.nativestarter.showcase.bluesky.core.network.FeedViewPost
import io.nativestarter.showcase.bluesky.core.network.PostView
import io.nativestarter.showcase.bluesky.core.network.XrpcJson

/** Posts loaded so far, and whether the server has no more pages. */
data class LoadedFeed(val items: List<FeedViewPost>, val reachedEnd: Boolean)

/**
 * Loads the first page, then follows the cursor while pages are non-empty, up to [maxExtraPages].
 * RN reaches the same state because `onEndReached` fires at once when the list is shorter than the screen;
 * the feed "has no next page" once a page is empty or has no cursor.
 */
suspend fun loadPages(
    maxExtraPages: Int = 2,
    fetch: suspend (cursor: String?) -> io.nativestarter.showcase.bluesky.core.network.FeedResponse,
): LoadedFeed {
    var page = fetch(null)
    val items = ArrayList(page.feed)
    var extra = 0
    while (page.cursor != null && page.feed.isNotEmpty() && extra < maxExtraPages) {
        page = fetch(page.cursor)
        items += page.feed
        extra++
    }
    return LoadedFeed(items, reachedEnd = page.cursor == null || page.feed.isEmpty())
}

/** One row of a feed. [isThreadParent] draws the line below the avatar; [isThreadChild] draws it above. */
data class FeedRow(
    val post: PostView,
    val isThreadParent: Boolean = false,
    val isThreadChild: Boolean = false,
)

/** A group of rows drawn without separators between them (RN `FeedPostSlice`). */
data class FeedSlice(val key: String, val rows: List<FeedRow>)

/**
 * Port of the parts of RN `FeedTuner` (src/lib/api/feed-manip.ts) that affect the seeded data:
 * a reply is shown under its parent as one slice, and a post already shown inside an earlier slice
 * is not shown again (`FeedTuner.removeOrphans`/dedupe).
 */
object FeedSlices {
    fun build(feed: List<FeedViewPost>): List<FeedSlice> {
        val seen = HashSet<String>()
        val slices = ArrayList<FeedSlice>()
        for (item in feed) {
            if (item.post.uri in seen) continue
            val parent = item.reason?.let { null } ?: item.reply?.parent?.asPostView()
            val rows = if (parent != null && parent.uri !in seen) {
                listOf(FeedRow(parent, isThreadParent = true), FeedRow(item.post, isThreadChild = true))
            } else {
                listOf(FeedRow(item.post))
            }
            rows.forEach { seen += it.post.uri }
            slices += FeedSlice(item.post.uri, rows)
        }
        return slices
    }

    private fun JsonElement.asPostView(): PostView? {
        val obj = this as? JsonObject ?: return null
        val type = obj["\$type"]?.jsonPrimitive?.content
        if (type != null && type != "app.bsky.feed.defs#postView") return null
        return runCatching { XrpcJson.decodeFromJsonElement(PostView.serializer(), obj) }.getOrNull()
    }
}
