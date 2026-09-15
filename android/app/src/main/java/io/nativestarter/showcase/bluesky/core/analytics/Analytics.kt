package io.nativestarter.showcase.bluesky.core.analytics

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Analytics events with the React Native names and properties (`ax.metric(name, payload)`).
 * A property whose value is null is left out, as `JSON.stringify` drops `undefined` in RN.
 */
interface Analytics {
    fun metric(name: String, props: Map<String, Any?> = emptyMap())
}

object NoopAnalytics : Analytics {
    override fun metric(name: String, props: Map<String, Any?>) = Unit
}

val LocalAnalytics = androidx.compose.runtime.staticCompositionLocalOf<Analytics> { NoopAnalytics }

/**
 * Debug event log: one `{"name": "...", "props": {...}}` JSON object per line, appended to [file]
 * (`files/events.jsonl`). The Migration Pack event diff reads this format. Writes run on the IO dispatcher,
 * in call order.
 */
class JsonlEventLog(private val file: File, scope: CoroutineScope) : Analytics {
    private val lines = Channel<String>(Channel.UNLIMITED)

    init {
        scope.launch(Dispatchers.IO) {
            for (line in lines) file.appendText(line + "\n")
        }
    }

    override fun metric(name: String, props: Map<String, Any?>) {
        lines.trySend(encode(name, props))
    }

    companion object {
        fun encode(name: String, props: Map<String, Any?>): String = buildJsonObject {
            put("name", name)
            put(
                "props",
                buildJsonObject {
                    props.forEach { (key, value) ->
                        when (value) {
                            null -> Unit
                            is String -> put(key, value)
                            is Boolean -> put(key, value)
                            is Number -> put(key, value)
                            else -> put(key, JsonPrimitive(value.toString()))
                        }
                    }
                },
            )
        }.toString()
    }
}

/** Test double that keeps events in memory. */
class RecordingAnalytics : Analytics {
    val events = mutableListOf<Pair<String, Map<String, Any?>>>()
    override fun metric(name: String, props: Map<String, Any?>) {
        events += name to props.filterValues { it != null }
    }
    fun names() = events.map { it.first }
}

/** RN `toClout` (src/analytics/metrics/utils.ts): `Math.max(0, Math.round(Math.log(n)))`, undefined for null. */
fun toClout(n: Int?): Int? {
    if (n == null) return null
    if (n <= 0) return 0
    return max(0L, ln(n.toDouble()).roundToLong()).toInt()
}

/** Context carried by post interactions, as RN passes `logContext` and the feed feedback `feedDescriptor`. */
data class InteractionContext(val logContext: String, val feedDescriptor: String?)
