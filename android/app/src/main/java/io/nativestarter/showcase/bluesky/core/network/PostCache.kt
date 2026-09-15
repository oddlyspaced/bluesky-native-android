package io.nativestarter.showcase.bluesky.core.network

/**
 * Posts the account has already loaded, newest use last. RN `getThreadPlaceholder` searches the thread, feed and
 * other query caches so a thread can show its anchor before `getPostThreadV2` answers.
 */
class PostCache(private val capacity: Int = 1000) {
    private val posts = object : LinkedHashMap<String, PostView>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PostView>?): Boolean = size > capacity
    }

    @Synchronized
    fun put(items: Iterable<PostView>) {
        items.forEach { posts[it.uri] = it }
    }

    @Synchronized
    fun get(uri: String): PostView? = posts[uri]
}

/** RN `PostSource` (src/state/unstable-post-source.tsx): the feed a post was opened from, and its repost `via`. */
data class PostSource(val feedDescriptor: String?, val via: StrongRef?)

/** RN transient post sources: set when a feed item is pressed, consumed once by the thread that opens. */
class PostSources {
    private val transient = HashMap<String, PostSource>()

    @Synchronized
    fun set(uri: String, source: PostSource) {
        transient[uri] = source
    }

    @Synchronized
    fun consume(uri: String): PostSource? = transient.remove(uri)
}
