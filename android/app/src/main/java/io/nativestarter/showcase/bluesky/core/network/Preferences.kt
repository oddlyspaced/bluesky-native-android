package io.nativestarter.showcase.bluesky.core.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** `feedViewPref` for the "home" feed (defaults from src/state/queries/preferences/const.ts). */
data class HomeFeedPrefs(
    val hideReplies: Boolean = false,
    val hideReposts: Boolean = false,
    val hideQuotePosts: Boolean = false,
)

/** The parts of `app.bsky.actor.getPreferences` the slice reads. */
data class UserPreferences(
    val homeFeed: HomeFeedPrefs = HomeFeedPrefs(),
    /** `threadViewPref.sort`, normalised by `normalizeSort`: "oldest" and "newest" are kept, anything else is "top". */
    val threadSort: String = "top",
) {
    companion object {
        fun parse(text: String): UserPreferences {
            val prefs = (XrpcJson.parseToJsonElement(text).jsonObject["preferences"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            fun typed(type: String) = prefs.filter { it["\$type"]?.jsonPrimitive?.contentOrNull == type }
            val home = typed("app.bsky.actor.defs#feedViewPref").firstOrNull { it["feed"]?.jsonPrimitive?.contentOrNull == "home" }
            fun flag(name: String) = home?.get(name)?.jsonPrimitive?.booleanOrNull ?: false
            val sort = typed("app.bsky.actor.defs#threadViewPref").firstOrNull()?.get("sort")?.jsonPrimitive?.contentOrNull
            return UserPreferences(
                homeFeed = HomeFeedPrefs(hideReplies = flag("hideReplies"), hideReposts = flag("hideReposts"), hideQuotePosts = flag("hideQuotePosts")),
                threadSort = normalizeSort(sort),
            )
        }

        fun normalizeSort(sort: String?): String = if (sort == "oldest" || sort == "newest") sort else "top"

        private fun JsonArray?.orEmpty() = this?.toList() ?: emptyList()
    }
}
