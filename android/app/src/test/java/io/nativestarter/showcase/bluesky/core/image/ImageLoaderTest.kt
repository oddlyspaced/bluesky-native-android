package io.nativestarter.showcase.bluesky.core.image

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageLoaderTest {
    /** Encoded image: "W:H" as text. Decoded image: "W:H/sample". */
    private class FakeDecoder : ImageDecoder<String> {
        val decoded = mutableListOf<Int>()
        override fun bounds(bytes: ByteArray) = String(bytes).split(":").let { it[0].toInt() to it[1].toInt() }
        override fun decode(bytes: ByteArray, sampleSize: Int): String {
            decoded += sampleSize
            return "${String(bytes)}/$sampleSize"
        }
        override fun sizeOf(image: String) = image.length
    }

    @Test
    fun sampleSizeIsLargestPowerOfTwoAboveTarget() {
        assertEquals(1, Downsampling.sampleSize(126, 126, 126, 126))
        assertEquals(4, Downsampling.sampleSize(1000, 1000, 126, 126))
        assertEquals(8, Downsampling.sampleSize(2000, 2000, 126, 126))
        // The banner: 3000x1000 source for a 1280x450 slot keeps both sides at or above the target.
        assertEquals(2, Downsampling.sampleSize(3000, 1000, 1280, 450))
        assertEquals(1, Downsampling.sampleSize(0, 0, 10, 10))
    }

    @Test
    fun memoryCacheEvictsLeastRecentlyUsedWithinBudget() {
        val cache = MemoryCache<String>(maxBytes = 6) { it.length }
        cache.put("a", "aa")
        cache.put("b", "bb")
        cache.get("a")
        cache.put("c", "cccc")
        assertEquals(listOf("a", "c"), cache.keys())
        assertEquals(6, cache.sizeBytes())
        cache.put("huge", "1234567")
        assertNull(cache.get("huge"))
    }

    @Test
    fun loadDownsamplesCachesAndSkipsRefetch() = runTest {
        var fetches = 0
        val decoder = FakeDecoder()
        val loader = CachingImageLoader(
            fetch = { fetches++; "1000:1000".toByteArray() },
            decoder = decoder,
            cache = MemoryCache(1_000) { it.length },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        assertNull(loader.cached("https://x/a.jpg", 126, 126))
        assertEquals("1000:1000/4", loader.load("https://x/a.jpg", 126, 126))
        assertEquals("1000:1000/4", loader.load("https://x/a.jpg", 126, 126))
        assertEquals("1000:1000/4", loader.cached("https://x/a.jpg", 126, 126))
        assertEquals(1, fetches)
        // Another target size is another entry.
        assertEquals("1000:1000/1", loader.load("https://x/a.jpg", 1000, 1000))
        assertEquals(listOf(4, 1), decoder.decoded)
    }

    @Test
    fun failedFetchReturnsNullAndCachesNothing() = runTest {
        val loader = CachingImageLoader(
            fetch = { null },
            decoder = FakeDecoder(),
            cache = MemoryCache(1_000) { it.length },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        assertNull(loader.load("https://x/missing.jpg", 10, 10))
        assertNull(loader.cached("https://x/missing.jpg", 10, 10))
    }
}
