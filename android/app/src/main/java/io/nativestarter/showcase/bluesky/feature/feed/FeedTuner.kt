package io.nativestarter.showcase.bluesky.feature.feed

import io.nativestarter.showcase.bluesky.core.network.FeedViewPost
import io.nativestarter.showcase.bluesky.core.network.HomeFeedPrefs
import io.nativestarter.showcase.bluesky.core.network.PostView
import io.nativestarter.showcase.bluesky.core.network.ProfileViewBasic
import io.nativestarter.showcase.bluesky.core.network.XrpcJson
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Lexicon union helpers for `app.bsky.feed.defs` post, blocked and not-found references. */
object PostRefs {
    fun type(element: JsonElement?): String? = (element as? JsonObject)?.get("\$type")?.jsonPrimitive?.contentOrNull

    fun isPostView(element: JsonElement?): Boolean {
        val obj = element as? JsonObject ?: return false
        val t = type(obj)
        return t == "app.bsky.feed.defs#postView" || (t == null && "author" in obj && "record" in obj && "uri" in obj)
    }

    fun isBlocked(element: JsonElement?): Boolean {
        val obj = element as? JsonObject ?: return false
        return type(obj) == "app.bsky.feed.defs#blockedPost" || obj["blocked"]?.jsonPrimitive?.booleanOrNull == true
    }

    fun isNotFound(element: JsonElement?): Boolean {
        val obj = element as? JsonObject ?: return false
        return type(obj) == "app.bsky.feed.defs#notFoundPost" || obj["notFound"]?.jsonPrimitive?.booleanOrNull == true
    }

    fun postView(element: JsonElement?): PostView? =
        if (isPostView(element)) runCatching { XrpcJson.decodeFromJsonElement(PostView.serializer(), element!!) }.getOrNull() else null

    /** `bsky.isType(app.bsky.feed.post, record)`: a record without `$type` is accepted (server always sends it). */
    fun isValidPostRecord(post: PostView): Boolean = post.record.type == null || post.record.type == "app.bsky.feed.post"
}

/** `app.bsky.feed.defs#reasonRepost` and `#reasonPin`. */
sealed interface FeedReason {
    data class Repost(val by: ProfileViewBasic, val uri: String?, val cid: String?, val indexedAt: String?) : FeedReason
    data object Pin : FeedReason

    companion object {
        fun parse(element: JsonElement?): FeedReason? {
            val obj = element as? JsonObject ?: return null
            return when (PostRefs.type(obj)) {
                "app.bsky.feed.defs#reasonRepost" -> runCatching {
                    Repost(
                        by = XrpcJson.decodeFromJsonElement(ProfileViewBasic.serializer(), obj.getValue("by")),
                        uri = obj["uri"]?.jsonPrimitive?.contentOrNull,
                        cid = obj["cid"]?.jsonPrimitive?.contentOrNull,
                        indexedAt = obj["indexedAt"]?.jsonPrimitive?.contentOrNull,
                    )
                }.getOrNull()
                "app.bsky.feed.defs#reasonPin" -> Pin
                else -> null
            }
        }
    }
}

data class SliceItem(
    val post: PostView,
    val parentAuthor: ProfileViewBasic?,
    val isParentBlocked: Boolean,
    val isParentNotFound: Boolean,
)

const val FALLBACK_MARKER_URI = "fallback-marker-post"

/** Port of `FeedViewPostsSlice` (src/lib/api/feed-manip.ts). */
class FeedViewPostsSlice private constructor(val feedPost: FeedViewPost?) {
    val items: MutableList<SliceItem> = mutableListOf()
    var isIncompleteThread = false
        private set
    var isFallbackMarker = false
        private set
    var isOrphan = false
        private set
    val reason: FeedReason? = FeedReason.parse(feedPost?.reason)

    val isThreadMuted: Boolean get() = feedPost?.post?.viewer?.threadMuted ?: false
    val feedPostUri: String get() = feedPost?.post?.uri ?: FALLBACK_MARKER_URI
    val rootUri: String get() = PostRefs.postView(feedPost?.reply?.root)?.uri ?: feedPostUri
    val key: String get() {
        val repostAt = (reason as? FeedReason.Repost)?.indexedAt
        return "slice-$feedPostUri-${repostAt ?: feedPost?.post?.indexedAt}"
    }
    val isReply: Boolean get() = feedPost?.post?.record?.reply != null
    val isRepost: Boolean get() = reason is FeedReason.Repost
    val isQuotePost: Boolean get() {
        val t = PostRefs.type(feedPost?.post?.embed)
        return t == "app.bsky.embed.record#view" || t == "app.bsky.embed.recordWithMedia#view"
    }

    /** `getAuthors()`: author, parent, grandparent and root authors used by the reply tuners. */
    fun authors(): AuthorContext {
        val fp = feedPost ?: error("marker has no authors")
        return AuthorContext(
            author = fp.post.author,
            parentAuthor = PostRefs.postView(fp.reply?.parent)?.author,
            grandparentAuthor = fp.reply?.grandparentAuthor,
            rootAuthor = PostRefs.postView(fp.reply?.root)?.author,
        )
    }

    fun containsUri(uri: String) = items.any { it.post.uri == uri }

    companion object {
        fun marker(): FeedViewPostsSlice = FeedViewPostsSlice(null).apply { isFallbackMarker = true }

        fun from(feedPost: FeedViewPost): FeedViewPostsSlice = FeedViewPostsSlice(feedPost).apply { build(feedPost) }
    }

    private fun build(feedPost: FeedViewPost) {
        val post = feedPost.post
        if (post.uri == FALLBACK_MARKER_URI) {
            isFallbackMarker = true
            return
        }
        if (!PostRefs.isValidPostRecord(post)) return
        val reply = feedPost.reply
        val parent = reply?.parent
        items += SliceItem(
            post = post,
            parentAuthor = PostRefs.postView(parent)?.author,
            isParentBlocked = PostRefs.isBlocked(parent),
            isParentNotFound = PostRefs.isNotFound(parent),
        )
        if (reply == null) {
            if (post.record.reply != null) {
                // The AppView did not hydrate this reply.
                isOrphan = true
                items[0] = items[0].copy(isParentNotFound = true)
            }
            return
        }
        if (feedPost.reason != null) return
        val parentPost = PostRefs.postView(parent)
        if (parentPost == null || !PostRefs.isValidPostRecord(parentPost)) {
            isOrphan = true
            return
        }
        val root = reply.root
        val rootIsView = PostRefs.isPostView(root) || PostRefs.isBlocked(root) || PostRefs.isNotFound(root)
        val rootUri = (root as? JsonObject)?.get("uri")?.jsonPrimitive?.contentOrNull
        val grandparent = if (rootIsView && parentPost.record.reply?.parent?.uri == rootUri) root else null
        val isGrandparentBlocked = grandparent != null && PostRefs.isBlocked(grandparent)
        val isGrandparentNotFound = grandparent != null && PostRefs.isNotFound(grandparent)
        items.add(
            0,
            SliceItem(parentPost, reply.grandparentAuthor, isGrandparentBlocked, isGrandparentNotFound),
        )
        if (isGrandparentBlocked) isOrphan = true
        val rootPost = PostRefs.postView(root)
        if (rootPost == null || !PostRefs.isValidPostRecord(rootPost)) {
            isOrphan = true
            return
        }
        if (rootPost.uri == parentPost.uri) return
        items.add(0, SliceItem(rootPost, null, isParentBlocked = false, isParentNotFound = false))
        if (parentPost.record.reply?.parent?.uri != rootPost.uri) isIncompleteThread = true
    }
}

data class AuthorContext(
    val author: ProfileViewBasic,
    val parentAuthor: ProfileViewBasic?,
    val grandparentAuthor: ProfileViewBasic?,
    val rootAuthor: ProfileViewBasic?,
)

typealias FeedTunerFn = (tuner: FeedTuner, slices: List<FeedViewPostsSlice>, dryRun: Boolean) -> List<FeedViewPostsSlice>

/** Port of `FeedTuner` (src/lib/api/feed-manip.ts). A new tuner is created for every selection, as in RN. */
class FeedTuner(private val tunerFns: List<FeedTunerFn>) {
    val seenKeys = HashSet<String>()
    val seenUris = HashSet<String>()
    val seenRootUris = HashSet<String>()

    fun tune(feed: List<FeedViewPost>, dryRun: Boolean = false): List<FeedViewPostsSlice> =
        tuneSlices(feed.map { FeedViewPostsSlice.from(it) }.filter { it.items.isNotEmpty() || it.isFallbackMarker }, dryRun)

    fun tuneSlices(input: List<FeedViewPostsSlice>, dryRun: Boolean = false): List<FeedViewPostsSlice> {
        var slices = input
        for (fn in tunerFns) slices = fn(this, slices.toList(), dryRun)
        return slices.filter { slice ->
            if (slice.isFallbackMarker) return@filter true
            if (slice.key in seenKeys) return@filter false
            var i = 0
            while (i < slice.items.size) {
                val item = slice.items[i]
                if (item.post.uri in seenUris) {
                    if (i == 0) {
                        // Omit contiguous seen leading items.
                        slice.items.removeAt(0)
                        i--
                    }
                    if (i == slice.items.size - 1) return@filter false
                } else if (!dryRun) {
                    // Reposting a reply shows it top-level; keep its parents available for later slices.
                    if (!(slice.isReply && slice.isRepost)) seenUris += item.post.uri
                }
                i++
            }
            if (!dryRun) seenKeys += slice.key
            true
        }
    }

    companion object {
        /** `useFeedTuners` for `following` (src/state/preferences/feed-tuners.tsx). */
        fun following(prefs: HomeFeedPrefs, userDid: String): FeedTuner = FeedTuner(
            buildList {
                add(Tuners.removeOrphans)
                if (prefs.hideReposts) add(Tuners.removeReposts)
                if (prefs.hideReplies) add(Tuners.removeReplies) else add(Tuners.followedRepliesOnly(userDid))
                if (prefs.hideQuotePosts) add(Tuners.removeQuotePosts)
                add(Tuners.dedupThreads)
                add(Tuners.removeMutedThreads)
            },
        )

        /** Author feeds other than `posts_with_replies` run no tuner functions, only the final seen-URI pass. */
        fun author(): FeedTuner = FeedTuner(emptyList())
    }
}

object Tuners {
    val removeReplies: FeedTunerFn = { _, slices, _ ->
        slices.filterNot { it.isReply && !it.isRepost && !areSameAuthor(it.authors()) }
    }
    val removeReposts: FeedTunerFn = { _, slices, _ -> slices.filterNot { it.isRepost } }
    val removeQuotePosts: FeedTunerFn = { _, slices, _ -> slices.filterNot { it.isQuotePost } }
    val removeOrphans: FeedTunerFn = { _, slices, _ -> slices.filterNot { it.isOrphan } }
    val removeMutedThreads: FeedTunerFn = { _, slices, _ -> slices.filterNot { it.isThreadMuted } }
    val dedupThreads: FeedTunerFn = { tuner, slices, dryRun ->
        slices.filter { slice ->
            if (slice.isFallbackMarker) return@filter true
            if (!slice.isRepost && slice.rootUri in tuner.seenRootUris) {
                false
            } else {
                if (!dryRun) tuner.seenRootUris += slice.rootUri
                true
            }
        }
    }

    fun followedRepliesOnly(userDid: String): FeedTunerFn = { _, slices, _ ->
        slices.filterNot { it.isReply && !it.isRepost && !shouldDisplayReplyInFollowing(it.authors(), userDid) }
    }

    fun areSameAuthor(authors: AuthorContext): Boolean {
        val did = authors.author.did
        return listOfNotNull(authors.parentAuthor, authors.grandparentAuthor, authors.rootAuthor).all { it.did == did }
    }

    fun shouldDisplayReplyInFollowing(authors: AuthorContext, userDid: String): Boolean {
        val author = authors.author
        if (!isSelfOrFollowing(author, userDid)) return false
        if (areSameAuthor(authors)) return true
        return listOfNotNull(authors.parentAuthor, authors.grandparentAuthor, authors.rootAuthor)
            .any { it.did != author.did && isSelfOrFollowing(it, userDid) }
    }

    private fun isSelfOrFollowing(profile: ProfileViewBasic, userDid: String) =
        profile.did == userDid || profile.viewer?.following != null
}

/**
 * `AuthorFeedAPI._filter` (src/lib/api/feed/author.ts) for `posts_and_author_threads`: keep a post without a reply,
 * a repost or a pin, or a reply that is part of the actor's own reply chain on the same page.
 */
object AuthorFeedFilter {
    fun filter(feed: List<FeedViewPost>, actorDid: String): List<FeedViewPost> {
        val byUri = feed.associateBy { it.post.uri }
        return feed.filter { item ->
            if (item.reply == null) return@filter true
            if (item.reason != null) return@filter true
            isAuthorReplyChain(actorDid, item.post, byUri)
        }
    }

    private fun isAuthorReplyChain(actorDid: String, post: PostView, byUri: Map<String, FeedViewPost>): Boolean {
        if (post.author.did != actorDid) return false
        val item = byUri[post.uri] ?: return true
        val parent = item.reply?.parent ?: return true
        val parentPost = PostRefs.postView(parent) ?: return true
        if (parentPost.author.did != actorDid) return false
        val parentItem = byUri[parentPost.uri] ?: return true
        return isAuthorReplyChain(actorDid, parentItem.post, byUri)
    }
}

/** One row of `PostFeed` (src/view/com/posts/PostFeed.tsx). */
sealed interface PostFeedRow {
    val key: String

    data object ComposerPrompt : PostFeedRow { override val key = "composerPrompt" }
    data object FallbackMarker : PostFeedRow { override val key = "fallbackMarker" }
    data class ViewFullThread(val uri: String, override val key: String) : PostFeedRow
    data class SliceItemRow(
        val post: PostView,
        val slice: FeedViewPostsSlice,
        val indexInSlice: Int,
        val showReplyTo: Boolean,
        val isThreadParent: Boolean,
        val isThreadChild: Boolean,
        val isThreadLastChild: Boolean,
        val hideTopBorder: Boolean,
        val reason: FeedReason?,
        val parentAuthor: ProfileViewBasic?,
        val isParentBlocked: Boolean,
        val isParentNotFound: Boolean,
        override val key: String,
    ) : PostFeedRow
}

object PostFeedRows {
    /** Builds rows from tuned slices. [withComposerPrompt] is true for the Following feed. */
    fun build(slices: List<FeedViewPostsSlice>, withComposerPrompt: Boolean): List<PostFeedRow> {
        val rows = ArrayList<PostFeedRow>()
        if (withComposerPrompt && slices.isNotEmpty()) rows += PostFeedRow.ComposerPrompt
        for (slice in slices) {
            if (slice.isFallbackMarker) {
                rows += PostFeedRow.FallbackMarker
                continue
            }
            val items = slice.items
            fun rowFor(index: Int, showReplyTo: Boolean): PostFeedRow.SliceItemRow {
                val item = items[index]
                val isChild = items.size > 1 && index > 0
                return PostFeedRow.SliceItemRow(
                    post = item.post,
                    slice = slice,
                    indexInSlice = index,
                    showReplyTo = showReplyTo,
                    isThreadParent = items.size > 1 && index < items.size - 1,
                    isThreadChild = isChild,
                    isThreadLastChild = isChild && items.size == index + 1,
                    hideTopBorder = rows.isEmpty() && index == 0,
                    reason = if (index == 0) slice.reason else null,
                    parentAuthor = item.parentAuthor,
                    isParentBlocked = item.isParentBlocked,
                    isParentNotFound = item.isParentNotFound,
                    key = "${slice.key}-$index",
                )
            }
            if (slice.isIncompleteThread && items.size >= 3) {
                val beforeLast = items.size - 2
                val last = items.size - 1
                rows += rowFor(0, showReplyTo = false)
                rows += PostFeedRow.ViewFullThread(items[last].post.uri, "${slice.key}-viewFullThread")
                rows += rowFor(beforeLast, showReplyTo = items[beforeLast].parentAuthor?.did != items[beforeLast].post.author.did)
                rows += rowFor(last, showReplyTo = false)
            } else {
                items.indices.forEach { rows += rowFor(it, showReplyTo = it == 0) }
            }
        }
        return rows
    }
}

@Suppress("unused")
private fun JsonElement.asObjectOrNull(): JsonObject? = runCatching { jsonObject }.getOrNull()
