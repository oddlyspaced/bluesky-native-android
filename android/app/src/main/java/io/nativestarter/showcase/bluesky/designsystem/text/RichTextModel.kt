package io.nativestarter.showcase.bluesky.designsystem.text

import io.nativestarter.showcase.bluesky.core.network.ByteSlice
import io.nativestarter.showcase.bluesky.core.network.Facet
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLEncoder

/**
 * A facet found in plain text by [RichTextModel.detect]. A mention carries the handle only: the DID is known
 * to the AppView, so it is resolved separately, as RN's `detectFacets(client)` does.
 */
sealed interface DetectedFacet {
    /** UTF-8 byte offsets, as `app.bsky.richtext.facet` uses. */
    val byteStart: Int
    val byteEnd: Int

    data class Link(override val byteStart: Int, override val byteEnd: Int, val uri: String) : DetectedFacet
    data class Tag(override val byteStart: Int, override val byteEnd: Int, val tag: String) : DetectedFacet
    data class Mention(override val byteStart: Int, override val byteEnd: Int, val handle: String) : DetectedFacet
}

/**
 * The IANA top-level domains, as the RN app's `tlds` package ships them (MIT). Bundled as a classpath resource,
 * so the detection rules stay pure Kotlin and run in JVM tests as well as on the device.
 */
object Tlds {
    val all: Set<String> by lazy {
        val stream = Tlds::class.java.classLoader?.getResourceAsStream("tlds.txt") ?: return@lazy emptySet()
        stream.bufferedReader().use { reader -> reader.readLines() }.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.toSet()
    }

    /** Port of `isValidDomain` (@bsky/sdk dist/rich-text/detection.js): the string ends with ".<tld>". */
    fun isValidDomain(value: String): Boolean = all.any { tld ->
        val i = value.lastIndexOf(tld)
        i > 0 && value[i - 1] == '.' && i == value.length - tld.length
    }
}

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

    // Ports of @bsky/sdk dist/rich-text/util.js. Kotlin and JavaScript use the same syntax for these patterns.
    private val MENTION_REGEX = Regex("""(^|\s|\()(@)([a-zA-Z0-9.-]+)\b""")
    private val DETECT_URL_REGEX = Regex(
        """(^|\s|\()((https?://\S+)|((?<domain>[a-z][a-z0-9]*(\.[a-z0-9]+)+)\S*))""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )
    private val TRAILING_PUNCTUATION_REGEX = Regex("""\p{P}+$""")
    private val ZERO_WIDTH = "­⁠ ​‌‍⃢"
    private val TAG_REGEX = Regex(
        "(^|\\s)[#＃]((?!️)[^\\s$ZERO_WIDTH]*[^\\d\\s\\p{P}$ZERO_WIDTH]+[^\\s$ZERO_WIDTH]*)?",
    )
    private val CASHTAG_REGEX = Regex("""(^|\s|\()\$([A-Za-z][A-Za-z0-9]{0,4})(?=\s|$|[.,;:!?)"'’])""")

    /** `RichText.utf16IndexToUtf8Index`: the byte offset of a UTF-16 index. */
    private fun utf8Index(text: String, utf16Index: Int): Int =
        text.substring(0, utf16Index.coerceIn(0, text.length)).toByteArray(Charsets.UTF_8).size

    /**
     * Port of `detectFacets` (@bsky/sdk dist/rich-text/detection.js): mentions, links, tags and cashtags, in
     * that order. RN runs it client-side on text that carries no facets of its own, for example a profile
     * description (`useRichText`, src/components/hooks/useRichText.ts).
     *
     * A mention keeps its handle; [DetectedFacet.Mention] is resolved to a DID by the caller.
     */
    fun detect(text: String, isValidDomain: (String) -> Boolean = Tlds::isValidDomain): List<DetectedFacet> {
        val out = ArrayList<DetectedFacet>()
        for (match in MENTION_REGEX.findAll(text)) {
            val handle = match.groupValues[3]
            // RN keeps ".test" handles, which the dev network uses, even though "test" is not an IANA TLD.
            if (!isValidDomain(handle) && !handle.endsWith(".test")) continue
            val start = text.indexOf(handle, match.range.first) - 1
            if (start < 0) continue
            out += DetectedFacet.Mention(utf8Index(text, start), utf8Index(text, start + handle.length + 1), handle)
        }
        for (match in DETECT_URL_REGEX.findAll(text)) {
            val matched = match.groupValues[2]
            var uri = matched
            if (!uri.startsWith("http")) {
                val domain = match.groups["domain"]?.value
                if (domain == null || !isValidDomain(domain)) continue
                uri = "https://$uri"
            }
            val start = text.indexOf(matched, match.range.first)
            if (start < 0) continue
            var end = start + matched.length
            // Strip one trailing sentence mark, then one unbalanced closing bracket, as the SDK does.
            if (uri.isNotEmpty() && uri.last() in ".,;:!?") {
                uri = uri.dropLast(1)
                end--
            }
            if (uri.endsWith(")") && !uri.contains("(")) {
                uri = uri.dropLast(1)
                end--
            }
            out += DetectedFacet.Link(utf8Index(text, start), utf8Index(text, end), uri)
        }
        for (match in TAG_REGEX.findAll(text)) {
            val leading = match.groupValues[1]
            val tag = (match.groups[2]?.value ?: continue).trim().replace(TRAILING_PUNCTUATION_REGEX, "")
            // The SDK limit is 64 graphemes; UTF-16 length is never below the grapheme count.
            if (tag.isEmpty() || tag.length > 64) continue
            val index = match.range.first + leading.length
            out += DetectedFacet.Tag(utf8Index(text, index), utf8Index(text, index + 1 + tag.length), tag)
        }
        for (match in CASHTAG_REGEX.findAll(text)) {
            val leading = match.groupValues[1]
            val ticker = (match.groups[2]?.value ?: continue).uppercase()
            val index = match.range.first + leading.length
            out += DetectedFacet.Tag(utf8Index(text, index), utf8Index(text, index + 1 + ticker.length), "$$ticker")
        }
        return out
    }

    /**
     * [detect] plus the AppView handle resolution RN does, turned into `app.bsky.richtext.facet` values that
     * [segments] renders. A mention whose handle does not resolve is dropped, as `detectFacets` drops it.
     */
    suspend fun resolveFacets(text: String, resolveHandle: suspend (String) -> String?): List<Facet> =
        detect(text).mapNotNull { found ->
            val feature = when (found) {
                is DetectedFacet.Link -> feature("app.bsky.richtext.facet#link", "uri" to found.uri)
                is DetectedFacet.Tag -> feature("app.bsky.richtext.facet#tag", "tag" to found.tag)
                is DetectedFacet.Mention -> {
                    val did = resolveHandle(found.handle) ?: return@mapNotNull null
                    feature("app.bsky.richtext.facet#mention", "did" to did)
                }
            }
            Facet(ByteSlice(found.byteStart, found.byteEnd), listOf(feature))
        }

    private fun feature(type: String, vararg fields: Pair<String, String>): JsonObject =
        JsonObject((listOf("\$type" to type) + fields).associate { it.first to JsonPrimitive(it.second) })

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
