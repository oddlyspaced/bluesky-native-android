package io.nativestarter.showcase.bluesky.core.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okhttp3.Request

/** `BitmapFactory` decoding: bounds first (`inJustDecodeBounds`), then pixels at `inSampleSize`. */
object BitmapImageDecoder : ImageDecoder<Bitmap> {
    override fun bounds(bytes: ByteArray): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return if (options.outWidth > 0 && options.outHeight > 0) options.outWidth to options.outHeight else null
    }

    override fun decode(bytes: ByteArray, sampleSize: Int): Bitmap? =
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sampleSize })

    override fun sizeOf(image: Bitmap): Int = image.allocationByteCount
}

/** The app's image loader: OkHttp fetch, `BitmapFactory` downsampling, and a memory cache of 1/8 of the heap. */
fun androidImageLoader(http: OkHttpClient = OkHttpClient()): ImageLoader<Bitmap> = CachingImageLoader(
    fetch = { url ->
        runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { if (it.isSuccessful) it.body?.bytes() else null }
        }.getOrNull()
    },
    decoder = BitmapImageDecoder,
    cache = MemoryCache(Runtime.getRuntime().maxMemory() / 8) { it.allocationByteCount },
    ioDispatcher = Dispatchers.IO,
)

/** No loader: every image shows its fallback (previews and UI tests). */
object NoImageLoader : ImageLoader<Bitmap> {
    override fun cached(url: String, targetWidthPx: Int, targetHeightPx: Int): Bitmap? = null
    override suspend fun load(url: String, targetWidthPx: Int, targetHeightPx: Int): Bitmap? = null
}

val LocalImageLoader = staticCompositionLocalOf<ImageLoader<Bitmap>> { NoImageLoader }

/**
 * A remote image loaded at its laid-out pixel size. [fallback] shows while it loads, when [url] is null, and when
 * loading fails.
 */
@Composable
fun RemoteImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: @Composable () -> Unit,
) {
    val loader = LocalImageLoader.current
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url, size) {
        if (url == null || size.width == 0 || size.height == 0) return@LaunchedEffect
        bitmap = loader.cached(url, size.width, size.height) ?: loader.load(url, size.width, size.height)
    }
    Box(modifier.onSizeChanged { size = it }) {
        val image = bitmap
        if (image == null) {
            fallback()
        } else {
            Image(image.asImageBitmap(), contentDescription = null, contentScale = contentScale, modifier = Modifier.matchParentSize())
        }
    }
}
