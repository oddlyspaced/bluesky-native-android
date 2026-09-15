package io.nativestarter.showcase.bluesky.feature.thread

import io.nativestarter.showcase.bluesky.core.network.PostView
import io.nativestarter.showcase.bluesky.core.network.ThreadItemV2
import io.nativestarter.showcase.bluesky.core.network.XrpcJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.max

/** RN `ThreadItem['ui']` for a `threadPost` (src/state/queries/usePostThread/utils.ts `getThreadPostUI`). */
data class ThreadPostUi(
    val isAnchor: Boolean = false,
    val showParentReplyLine: Boolean = false,
    val showChildReplyLine: Boolean = false,
    val indent: Int = 0,
    val isLastChild: Boolean = false,
    val precedesChildReadMore: Boolean = false,
)

/** RN `ThreadItem` (src/state/queries/usePostThread/types.ts), linear view. [key] is the list key. */
sealed interface ThreadItem {
    val key: String

    data class Post(
        val uri: String,
        val depth: Int,
        val post: PostView,
        val moreParents: Boolean = false,
        val moreReplies: Int = 0,
        val opThread: Boolean = false,
        val isBlurred: Boolean = false,
        val ui: ThreadPostUi = ThreadPostUi(),
    ) : ThreadItem {
        override val key: String get() = uri
    }

    data class NotFound(val uri: String, val depth: Int) : ThreadItem {
        override val key: String get() = uri
    }

    data class Blocked(val uri: String, val depth: Int) : ThreadItem {
        override val key: String get() = uri
    }

    data class NoUnauthenticated(val uri: String, val depth: Int, val showChildReplyLine: Boolean) : ThreadItem {
        override val key: String get() = uri
    }

    data class ReadMore(val uri: String, val authorHandle: String, val moreReplies: Int, val depth: Int) : ThreadItem {
        override val key: String get() = "readMore:$uri"
    }

    data class ReadMoreUp(val uri: String, val authorHandle: String) : ThreadItem {
        override val key: String get() = "readMoreUp:$uri"
    }

    data object ReplyComposer : ThreadItem {
        override val key: String = "replyComposer"
    }

    data object ShowOtherReplies : ThreadItem {
        override val key: String = "showOtherReplies"
    }

    data class Skeleton(override val key: String, val kind: SkeletonKind) : ThreadItem

    enum class SkeletonKind { Anchor, Reply, ReplyComposer }
}

/** One `getPostThreadV2` item, decoded once. */
data class ApiThreadItem(
    val uri: String,
    val depth: Int,
    val kind: Kind,
    val post: PostView? = null,
    val moreParents: Boolean = false,
    val moreReplies: Int = 0,
    val opThread: Boolean = false,
    val hiddenByThreadgate: Boolean = false,
    val mutedByViewer: Boolean = false,
) {
    enum class Kind { Post, NotFound, Blocked, NoUnauthenticated, Unknown }

    companion object {
        /** RN `postViewToThreadPlaceholder`: a cached post shown as the anchor while the thread loads. */
        fun placeholder(post: PostView) = ApiThreadItem(uri = post.uri, depth = 0, kind = Kind.Post, post = post)
    }
}

data class AnnotatedThread(val threadItems: List<ThreadItem>, val otherThreadItems: List<ThreadItem>)

/** The visible window of the thread list (RN `PostThread` `deferParents`, `maxParentCount`, `maxChildrenCount`). */
data class ThreadWindow(
    val deferParents: Boolean = true,
    val maxParentCount: Int = ThreadTraversal.PARENT_CHUNK_SIZE,
    val maxChildrenCount: Int = ThreadTraversal.CHILDREN_CHUNK_SIZE,
)

data class ThreadSlices(val items: List<ThreadItem>, val totalParentCount: Int, val totalChildrenCount: Int)

/**
 * Port of the linear-view parts of RN `sortAndAnnotateThreadItems` and `buildThread`
 * (src/state/queries/usePostThread/traversal.ts) and the `slices` memo in src/screens/PostThread/index.tsx.
 *
 * Moderation: RN hides a reply when `moderatePost` blurs or filters it, when the author is muted, or when the
 * threadgate hides it, unless the viewer wrote it. This port reads the same server state that drives those
 * decisions for the seeded network: `hiddenByThreadgate`, `mutedByViewer` and the author block state. Label-based
 * moderation is not part of the slice.
 */
object ThreadTraversal {
    /** RN `PARENT_CHUNK_SIZE` on native. */
    const val PARENT_CHUNK_SIZE = 5
    const val CHILDREN_CHUNK_SIZE = 50

    private const val POST = "app.bsky.unspecced.defs#threadItemPost"
    private const val NOT_FOUND = "app.bsky.unspecced.defs#threadItemNotFound"
    private const val BLOCKED = "app.bsky.unspecced.defs#threadItemBlocked"
    private const val NO_UNAUTHENTICATED = "app.bsky.unspecced.defs#threadItemNoUnauthenticated"

    fun parse(thread: List<ThreadItemV2>): List<ApiThreadItem> = thread.map(::parse)

    fun parse(item: ThreadItemV2): ApiThreadItem {
        val value = item.value
        fun flag(name: String) = value[name]?.jsonPrimitive?.booleanOrNull ?: false
        val kind = when (value["\$type"]?.jsonPrimitive?.contentOrNull) {
            POST -> ApiThreadItem.Kind.Post
            NOT_FOUND -> ApiThreadItem.Kind.NotFound
            BLOCKED -> ApiThreadItem.Kind.Blocked
            NO_UNAUTHENTICATED -> ApiThreadItem.Kind.NoUnauthenticated
            else -> ApiThreadItem.Kind.Unknown
        }
        val post = if (kind == ApiThreadItem.Kind.Post) decodePost(value) else null
        return ApiThreadItem(
            uri = item.uri,
            depth = item.depth,
            kind = if (kind == ApiThreadItem.Kind.Post && post == null) ApiThreadItem.Kind.Unknown else kind,
            post = post,
            moreParents = flag("moreParents"),
            moreReplies = value["moreReplies"]?.jsonPrimitive?.intOrNull ?: 0,
            opThread = flag("opThread"),
            hiddenByThreadgate = flag("hiddenByThreadgate"),
            mutedByViewer = flag("mutedByViewer"),
        )
    }

    private fun decodePost(value: JsonObject): PostView? =
        runCatching { XrpcJson.decodeFromJsonElement(PostView.serializer(), value.getValue("post")) }.getOrNull()

    /** Mutable traversal state, shared by reference between a post and its replies as in RN. */
    private class Meta(
        val depth: Int,
        val uri: String,
        val authorHandle: String,
        val parent: Meta?,
        val repliesCount: Int,
        val repliesUnhydrated: Int,
        var prevItemDepth: Int?,
        var nextItemDepth: Int?,
    ) {
        var isLastChild = false
        var isLastSibling = false
        var isPartOfLastBranchFromDepth: Int? = if (depth == 1) 1 else null
        var precedesChildReadMore = false
        var followsReadMoreUp = false
        var repliesSeenCounter = 0
        var replyIndex = 0
        var skippedIndentIndices: Set<Int> = emptySet()
        var upcomingParentReadMore: Meta? = null
    }

    private fun meta(item: ApiThreadItem, prev: ApiThreadItem?, next: ApiThreadItem?, parent: Meta?) = Meta(
        depth = item.depth,
        uri = item.uri,
        authorHandle = item.post!!.author.handle,
        parent = parent,
        repliesCount = item.post.replyCount,
        repliesUnhydrated = item.moreReplies,
        prevItemDepth = prev?.depth,
        nextItemDepth = next?.depth,
    )

    /** JavaScript `Array.prototype.at`: a negative index counts from the end. */
    private fun <T> List<T>.jsAt(index: Int): T? = if (index < 0) getOrNull(size + index) else getOrNull(index)

    private fun ApiThreadItem.isTombstone() = kind == ApiThreadItem.Kind.NoUnauthenticated ||
        kind == ApiThreadItem.Kind.NotFound || kind == ApiThreadItem.Kind.Blocked

    private fun ApiThreadItem.toPost(userDid: String?): ThreadItem.Post {
        val post = post!!
        val viewer = post.author.viewer
        val authorBlocked = viewer != null && (viewer.blocking != null || viewer.blockedBy || viewer.blockingByList != null)
        val isOwnPost = post.author.did == userDid
        return ThreadItem.Post(
            uri = uri,
            depth = depth,
            post = post,
            moreParents = moreParents,
            moreReplies = moreReplies,
            opThread = opThread,
            isBlurred = (hiddenByThreadgate || mutedByViewer || authorBlocked || viewer?.muted == true) && !isOwnPost,
        )
    }

    /** RN `getBranch(...).end`: the last index of the sub-tree that starts at [start]. */
    fun branchEnd(thread: List<ApiThreadItem>, start: Int, depth: Int): Int {
        var end = start
        for (ci in start + 1 until thread.size) {
            if (thread[ci].depth > depth) end = ci else return ci - 1
        }
        return end
    }

    fun sortAndAnnotate(thread: List<ApiThreadItem>, userDid: String?, skipModerationHandling: Boolean = false): AnnotatedThread {
        val threadItems = ArrayList<ThreadItem>()
        val otherThreadItems = ArrayList<ThreadItem>()
        val metas = HashMap<String, Meta>()

        var i = 0
        traversal@ while (i < thread.size) {
            val item = thread[i]
            var parentMeta: Meta? = null
            var itemMeta: Meta? = null
            if (item.kind == ApiThreadItem.Kind.Post) {
                parentMeta = metas[item.post!!.record.reply?.parent?.uri.orEmpty()]
                itemMeta = meta(item, prev = thread.jsAt(i - 1), next = thread.getOrNull(i + 1), parent = parentMeta)
                metas[item.uri] = itemMeta
            }

            if (item.depth == 0) {
                when (item.kind) {
                    ApiThreadItem.Kind.NoUnauthenticated -> threadItems += ThreadItem.NoUnauthenticated(item.uri, item.depth, showChildReplyLine = false)
                    ApiThreadItem.Kind.NotFound -> threadItems += ThreadItem.NotFound(item.uri, item.depth)
                    ApiThreadItem.Kind.Blocked -> threadItems += ThreadItem.Blocked(item.uri, item.depth)
                    ApiThreadItem.Kind.Post -> {
                        threadItems += item.toPost(userDid)
                        var pi = i - 1
                        parents@ while (pi >= 0) {
                            val parent = thread[pi]
                            when (parent.kind) {
                                ApiThreadItem.Kind.NoUnauthenticated -> {
                                    threadItems.add(0, ThreadItem.NoUnauthenticated(parent.uri, parent.depth, showChildReplyLine = parent.depth < 0))
                                    break@parents
                                }
                                ApiThreadItem.Kind.NotFound -> {
                                    threadItems.add(0, ThreadItem.NotFound(parent.uri, parent.depth))
                                    break@parents
                                }
                                ApiThreadItem.Kind.Blocked -> {
                                    threadItems.add(0, ThreadItem.Blocked(parent.uri, parent.depth))
                                    break@parents
                                }
                                ApiThreadItem.Kind.Post -> threadItems.add(0, parent.toPost(userDid))
                                ApiThreadItem.Kind.Unknown -> Unit
                            }
                            pi--
                        }
                    }
                    ApiThreadItem.Kind.Unknown -> Unit
                }
            } else if (item.depth > 0) {
                if (item.isTombstone()) {
                    // The API sends no unavailable replies today; RN drops the branch if it ever does.
                    i = branchEnd(thread, i, item.depth) + 1
                    continue@traversal
                } else if (item.kind == ApiThreadItem.Kind.Post) {
                    if (parentMeta != null) itemMeta!!.replyIndex = parentMeta.repliesSeenCounter
                    val post = item.toPost(userDid)
                    if (!post.isBlurred || skipModerationHandling) {
                        threadItems += post
                        parentMeta?.let { it.repliesSeenCounter += 1 }
                    } else {
                        val branch = branchEnd(thread, i, item.depth)
                        if (post.depth == 1) {
                            otherThreadItems += post
                            var ci = i + 1
                            while (ci <= branch) {
                                val child = thread[ci]
                                if (child.kind != ApiThreadItem.Kind.Post) break
                                val childParentMeta = metas[child.post!!.record.reply?.parent?.uri.orEmpty()]
                                val childMeta = meta(child, prev = thread.getOrNull(ci - 1), next = thread.getOrNull(ci + 1), parent = childParentMeta)
                                metas[child.uri] = childMeta
                                if (childParentMeta != null) childMeta.replyIndex = childParentMeta.repliesSeenCounter
                                val childPost = child.toPost(userDid)
                                if (childPost.isBlurred) {
                                    // A moderated child and its sub-branch are dropped; opening the parent shows them.
                                    ci = branchEnd(thread, ci, child.depth)
                                } else {
                                    otherThreadItems += childPost
                                    childParentMeta?.let { it.repliesSeenCounter += 1 }
                                }
                                ci++
                            }
                        }
                        i = branch + 1
                        continue@traversal
                    }
                }
            }
            // depth < 0: parents are added while walking up from the anchor.
            i++
        }

        for (subset in listOf(threadItems, otherThreadItems)) {
            var j = 0
            while (j < subset.size) {
                val item = subset[j]
                val itemMeta = (item as? ThreadItem.Post)?.let { metas[it.uri] }
                if (item !is ThreadItem.Post || itemMeta == null) {
                    j++
                    continue
                }
                val prevItem = subset.jsAt(j - 1)
                val nextItem = subset.getOrNull(j + 1)
                val parent = itemMeta.parent
                if (parent != null) {
                    if (prevItem is ThreadItem.Post) itemMeta.prevItemDepth = prevItem.depth
                    if (nextItem is ThreadItem.Post) itemMeta.nextItemDepth = nextItem.depth
                    val isLastSiblingDueToMissingReplies = itemMeta.replyIndex == parent.repliesSeenCounter - 1
                    val next = itemMeta.nextItemDepth
                    val isImplicitlyLastSibling = next == null || next < itemMeta.depth
                    itemMeta.isLastSibling = isImplicitlyLastSibling || isLastSiblingDueToMissingReplies
                    itemMeta.isLastChild = next == null || next <= itemMeta.depth
                    if (itemMeta.isLastSibling) itemMeta.isPartOfLastBranchFromDepth = itemMeta.depth
                    if (parent.repliesUnhydrated > 0 && itemMeta.isLastSibling) itemMeta.upcomingParentReadMore = parent
                    parent.upcomingParentReadMore?.let { itemMeta.upcomingParentReadMore = it }
                    itemMeta.skippedIndentIndices = parent.skippedIndentIndices + if (parent.repliesUnhydrated <= 0 && itemMeta.isLastSibling) {
                        setOf(item.depth - 2)
                    } else {
                        emptySet()
                    }
                }
                var index = j
                if (itemMeta.repliesUnhydrated > 0 && itemMeta.isLastChild) {
                    itemMeta.precedesChildReadMore = true
                    subset.add(index + 1, ThreadItem.ReadMore(itemMeta.uri, itemMeta.authorHandle, itemMeta.repliesUnhydrated, itemMeta.depth))
                    index++
                }
                // The tree-view "read more" for an upcoming parent is not used by the linear view.
                if (item.moreParents) {
                    itemMeta.followsReadMoreUp = true
                    subset.add(index, ThreadItem.ReadMoreUp(itemMeta.uri, itemMeta.authorHandle))
                    index++
                }
                val position = subset.indexOfFirst { it is ThreadItem.Post && it.uri == item.uri }
                subset[position] = item.copy(ui = uiOf(itemMeta))
                j = index + 1
            }
        }
        return AnnotatedThread(threadItems, otherThreadItems)
    }

    private fun uiOf(m: Meta): ThreadPostUi {
        val isReplyAndHasReplies = m.depth > 0 && m.repliesCount > 0 &&
            (m.repliesCount - m.repliesUnhydrated == m.repliesSeenCounter || m.repliesSeenCounter > 0)
        val prev = m.prevItemDepth
        return ThreadPostUi(
            isAnchor = m.depth == 0,
            // JavaScript `!!prevItemDepth` is false for 0.
            showParentReplyLine = m.followsReadMoreUp || (prev != null && prev != 0 && prev < m.depth),
            showChildReplyLine = m.depth < 0 || isReplyAndHasReplies,
            indent = m.depth,
            isLastChild = m.isLastChild,
            precedesChildReadMore = m.precedesChildReadMore,
        )
    }

    /** RN `buildThread`, signed in. */
    fun buildThread(
        threadItems: List<ThreadItem>,
        otherThreadItems: List<ThreadItem>,
        serverOtherThreadItems: List<ThreadItem>,
        isLoading: Boolean,
        otherItemsVisible: Boolean,
        hasOtherThreadItems: Boolean,
    ): List<ThreadItem> {
        val items = threadItems.toMutableList()
        if (isLoading) {
            val anchorFromCache = items.firstOrNull() as? ThreadItem.Post
            val skeletonReplies = anchorFromCache?.post?.replyCount ?: 4
            if (items.isEmpty()) items += ThreadItem.Skeleton("anchor-skeleton", ThreadItem.SkeletonKind.Anchor)
            if (anchorFromCache != null) {
                if (!anchorFromCache.post.viewer.replyDisabled) items += ThreadItem.ReplyComposer
            } else {
                items += ThreadItem.Skeleton("replyComposer", ThreadItem.SkeletonKind.ReplyComposer)
            }
            repeat(skeletonReplies) { items += ThreadItem.Skeleton("anchor-skeleton-reply-$it", ThreadItem.SkeletonKind.Reply) }
        } else {
            val anchorIndex = items.indexOfFirst { it is ThreadItem.Post && it.depth == 0 && !it.post.viewer.replyDisabled }
            if (anchorIndex >= 0) items.add(anchorIndex + 1, ThreadItem.ReplyComposer)
            if (otherThreadItems.isNotEmpty() || hasOtherThreadItems) {
                if (otherItemsVisible) {
                    items += otherThreadItems
                    items += serverOtherThreadItems
                } else {
                    items += ThreadItem.ShowOtherReplies
                }
            }
        }
        return items
    }

    /** RN `usePostThread` other replies while `getPostThreadOtherV2` loads. */
    fun otherReplySkeletons(): List<ThreadItem> = List(2) { ThreadItem.Skeleton("other-reply-$it", ThreadItem.SkeletonKind.Reply) }

    /** RN `anchor` and `hasParents` memo: the first depth-0 post, and whether anything comes before it. */
    fun anchorOf(items: List<ThreadItem>): Pair<ThreadItem.Post?, Boolean> {
        var hasParents = false
        for (item in items) {
            if (item is ThreadItem.Post && item.depth == 0) return item to hasParents
            hasParents = true
        }
        return null to hasParents
    }

    private fun ThreadItem.depthOrNull(): Int? = when (this) {
        is ThreadItem.Post -> depth
        is ThreadItem.NotFound -> depth
        is ThreadItem.Blocked -> depth
        is ThreadItem.NoUnauthenticated -> depth
        is ThreadItem.ReadMore -> depth
        else -> null
    }

    /**
     * RN `slices` memo: the anchor first, parents only after the anchor has been laid out, at most
     * `maxParentCount + 1` parents and `maxChildrenCount + 1` children.
     *
     * Difference: RN also treats a depth-0 "read more" as an anchor, which would add the parents twice. Compose list
     * keys must be unique, so here only posts and tombstones can be the anchor, and a repeated key is skipped.
     */
    fun slices(items: List<ThreadItem>, window: ThreadWindow): ThreadSlices {
        val results = ArrayList<ThreadItem>()
        if (items.isEmpty()) return ThreadSlices(results, 0, 0)
        var totalParentCount = 0
        var totalChildrenCount = items.size
        var childrenCount = 0
        for (i in items.indices) {
            val item = items[i]
            val depth = item.depthOrNull()
            if (depth == 0 && item !is ThreadItem.ReadMore) {
                results += item
                totalParentCount = i
                totalChildrenCount = items.size - 1 - i
                if (!window.deferParents) {
                    val start = i - 1
                    if (start >= 0) {
                        val limit = max(0, start - window.maxParentCount)
                        for (pi in start downTo limit) results.add(0, items[pi])
                    }
                }
            } else {
                if (item is ThreadItem.ReadMoreUp || (depth != null && depth < 0)) continue
                if (childrenCount > window.maxChildrenCount) break
                results += item
                childrenCount++
            }
        }
        val seen = HashSet<String>()
        return ThreadSlices(results.filter { seen.add(it.key) }, totalParentCount, totalChildrenCount)
    }

    /** RN `isTombstoneView`: at most one item, and every item is a blocked or not-found tombstone. */
    fun isTombstoneView(slices: List<ThreadItem>): Boolean =
        slices.size <= 1 && slices.all { it is ThreadItem.Blocked || it is ThreadItem.NotFound }

    fun postsOf(thread: List<ApiThreadItem>): List<PostView> = thread.mapNotNull { it.post }
}
