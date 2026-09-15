package io.nativestarter.showcase.bluesky.core.image

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Loads remote images at a target size (review A15). Screens depend on this interface, not on an HTTP client. */
interface ImageLoader<T : Any> {
    /** The cached image for [url] at this target size, or null. Never does I/O. */
    fun cached(url: String, targetWidthPx: Int, targetHeightPx: Int): T?

    /** Fetches, downsamples and caches. Returns null when the request or the decoding fails. */
    suspend fun load(url: String, targetWidthPx: Int, targetHeightPx: Int): T?
}

/** Decodes encoded image bytes. The Android implementation uses `BitmapFactory`; tests use a fake. */
interface ImageDecoder<T : Any> {
    /** Width and height of the encoded image, without decoding the pixels. */
    fun bounds(bytes: ByteArray): Pair<Int, Int>?

    fun decode(bytes: ByteArray, sampleSize: Int): T?

    /** Memory size of a decoded image in bytes, for the cache budget. */
    fun sizeOf(image: T): Int
}

object Downsampling {
    /**
     * The largest power of two that keeps both decoded sides at or above the target
     * (the `inSampleSize` rule from the Android "Loading large bitmaps efficiently" guide).
     */
    fun sampleSize(sourceWidth: Int, sourceHeight: Int, targetWidth: Int, targetHeight: Int): Int {
        if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) return 1
        var sample = 1
        while (sourceWidth / (sample * 2) >= targetWidth && sourceHeight / (sample * 2) >= targetHeight) sample *= 2
        return sample
    }
}

/** A least-recently-used memory cache with a byte budget. */
class MemoryCache<T : Any>(private val maxBytes: Long, private val sizeOf: (T) -> Int) {
    private val map = LinkedHashMap<String, T>(16, 0.75f, true)
    private var bytes = 0L

    @Synchronized
    fun get(key: String): T? = map[key]

    @Synchronized
    fun put(key: String, value: T) {
        val size = sizeOf(value).toLong()
        if (size > maxBytes) return
        map.put(key, value)?.let { bytes -= sizeOf(it) }
        bytes += size
        val iterator = map.entries.iterator()
        while (bytes > maxBytes && iterator.hasNext()) {
            val eldest = iterator.next()
            bytes -= sizeOf(eldest.value)
            iterator.remove()
        }
    }

    @Synchronized
    fun sizeBytes(): Long = bytes

    @Synchronized
    fun keys(): List<String> = map.keys.toList()
}

/**
 * Fetches bytes with [fetch], decodes them downsampled to the target size, and keeps the result in [cache].
 * Concurrent requests for the same key share one fetch.
 */
class CachingImageLoader<T : Any>(
    private val fetch: suspend (String) -> ByteArray?,
    private val decoder: ImageDecoder<T>,
    private val cache: MemoryCache<T>,
    private val ioDispatcher: CoroutineDispatcher,
) : ImageLoader<T> {
    private val inFlight = HashMap<String, Deferred<T?>>()

    override fun cached(url: String, targetWidthPx: Int, targetHeightPx: Int): T? = cache.get(key(url, targetWidthPx, targetHeightPx))

    override suspend fun load(url: String, targetWidthPx: Int, targetHeightPx: Int): T? {
        val key = key(url, targetWidthPx, targetHeightPx)
        cache.get(key)?.let { return it }
        val deferred = coroutineScope {
            synchronized(inFlight) {
                inFlight.getOrPut(key) {
                    async(ioDispatcher) {
                        try {
                            val bytes = fetch(url) ?: return@async null
                            val (w, h) = decoder.bounds(bytes) ?: return@async null
                            decoder.decode(bytes, Downsampling.sampleSize(w, h, targetWidthPx, targetHeightPx))
                                ?.also { cache.put(key, it) }
                        } finally {
                            synchronized(inFlight) { inFlight.remove(key) }
                        }
                    }
                }
            }
        }
        return withContext(ioDispatcher) { deferred.await() }
    }

    companion object {
        fun key(url: String, width: Int, height: Int) = "$url@${width}x$height"
    }
}
