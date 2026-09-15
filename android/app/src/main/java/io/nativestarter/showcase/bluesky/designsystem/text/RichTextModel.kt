package io.nativestarter.showcase.bluesky.designsystem.text

import io.nativestarter.showcase.bluesky.core.network.Facet
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLEncoder

/** A run of post text, plain or with one facet feature (`RichTextSegment` in @bsky/sdk rich-text). */
sealed interface RichSegment {
    val text: String

    data class Plain(override val text: String) : RichSegment
    data class Link(override val text: String, val uri: String) : RichSegment
    data class Mention(override val text: String, val did: String) : RichSegment
    data class Tag(override val text: String, val tag: String) : RichSegment
}

/** Port of `RichText.segments()` (@bsky/sdk dist/rich-text/rich-text.js) with UTF-8 byte offsets. */
object RichTextModel {
    private val URL_REGEX = Regex("""(^|\s|\()((https?://\S+)|((?<domain>[a-z][a-z0-9]*(\.[a-z0-9]+)+)\S*))""", RegexOption.IGNORE_CASE)

    fun segments(text: String, facets: List<Facet>): List<RichSegment> {
        if (facets.isEmpty()) return listOf(RichSegment.Plain(text))
        val bytes = text.toByteArray(Charsets.UTF_8)
        fun slice(start: Int, end: Int): String =
            String(bytes, start.coerceIn(0, bytes.size), (end.coerceIn(0, bytes.size) - start.coerceIn(0, bytes.size)).coerceAtLeast(0), Charsets.UTF_8)

        val sorted = facets.filter { it.index.byteStart <= it.index.byteEnd }.sortedBy { it.index.byteStart }
        val out = ArrayList<RichSegment>()
        var textCursor = 0
        var facetCursor = 0
        while (facetCursor < sorted.size) {
            val facet = sorted[facetCursor]
            val start = facet.index.byteStart
            val end = facet.index.byteEnd
            if (textCursor < start) {
                out += RichSegment.Plain(slice(textCursor, start))
            } else if (textCursor > start) {
                // Overlapping facet: dropped.
                facetCursor++
                continue
            }
            if (start < end) {
                val subtext = slice(start, end)
                out += if (subtext.isBlank()) RichSegment.Plain(subtext) else withFeature(subtext, facet)
            }
            textCursor = end
            facetCursor++
        }
        if (textCursor < bytes.size) out += RichSegment.Plain(slice(textCursor, bytes.size))
        return out
    }

    /** The first feature whose `$type` matches, as the SDK's `link` / `mention` / `tag` getters. */
    private fun withFeature(text: String, facet: Facet): RichSegment {
        for (feature in facet.features) {
            val type = feature["\$type"]?.jsonPrimitive?.contentOrNull
            when (type) {
                "app.bsky.richtext.facet#link" -> {
                    val uri = feature["uri"]?.jsonPrimitive?.contentOrNull ?: continue
                    // RichText.tsx renders a link only when URL_REGEX matches the URI.
                    return if (URL_REGEX.containsMatchIn(uri)) RichSegment.Link(text, uri) else RichSegment.Plain(toShortUrl(text))
                }
                "app.bsky.richtext.facet#mention" -> {
                    val did = feature["did"]?.jsonPrimitive?.contentOrNull ?: continue
                    return if (did.isNotEmpty()) RichSegment.Mention(text, did) else RichSegment.Plain(text)
                }
                "app.bsky.richtext.facet#tag" -> {
                    val tag = feature["tag"]?.jsonPrimitive?.contentOrNull ?: continue
                    return RichSegment.Tag(text, tag)
                }
            }
        }
        return RichSegment.Plain(text)
    }

    /** Port of `toShortUrl` (src/lib/strings/url-helpers.ts). */
    fun toShortUrl(url: String): String = try {
        val u = URI(url)
        if (u.scheme != "http" && u.scheme != "https") {
            url
        } else {
            val host = u.rawAuthority ?: return url
            val path = (if (u.rawPath == "/" || u.rawPath.isNullOrEmpty()) "" else u.rawPath) +
                (u.rawQuery?.let { "?$it" } ?: "") + (u.rawFragment?.let { "#$it" } ?: "")
            if (path.length > 15) host + path.substring(0, 13) + "..." else host + path
        }
    } catch (e: Exception) {
        url
    }

    /** `createProxiedUrl`: RN opens post links through `https://go.bsky.app/redirect?u=...` (`shouldProxyLinks`). */
    fun proxiedUrl(url: String): String =
        if (url.startsWith("https://go.bsky.app/")) url else "https://go.bsky.app/redirect?u=" + URLEncoder.encode(url, "UTF-8")

    /** `countLines`: newline count only (src/lib/strings/helpers.ts). */
    fun countLines(text: String): Int = text.count { it == '\n' }

    /** `MAX_POST_LINES` (src/lib/constants.ts). */
    const val MAX_POST_LINES = 25

    /**
     * `isOnlyEmoji` (src/alf/typography.tsx): at most 15 UTF-16 units and only emoji, joiners, variation selectors
     * and spaces.
     */
    fun isOnlyEmoji(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || text.length > 15) return false
        var i = 0
        while (i < trimmed.length) {
            val cp = trimmed.codePointAt(i)
            val ok = cp == 0x200D || cp in 0xFE00..0xFE0F || Character.isWhitespace(cp) || cp in 0x1F000..0x1FAFF ||
                cp in 0x2600..0x27BF || cp in 0x1F1E6..0x1F1FF || cp in 0xE0020..0xE007F || cp == 0x20E3 ||
                Character.getType(cp) == Character.OTHER_SYMBOL.toInt()
            if (!ok) return false
            i += Character.charCount(cp)
        }
        return true
    }
}
