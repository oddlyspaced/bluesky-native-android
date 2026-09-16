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

/**
 * The `threadViewPref` values the thread screen reads. RN `useThreadPreferences` compares
 * `preferences.threadViewPrefs` only, so a change to an unrelated preference is not a thread preference change
 * (review A13).
 */
data class ThreadViewPrefs(val sortRaw: String, val treeViewEnabled: Boolean)

/** The parts of `app.bsky.actor.getPreferences` the slice reads. */
data class UserPreferences(
    val homeFeed: HomeFeedPrefs = HomeFeedPrefs(),
    /** `threadViewPref.sort`, normalised by `normalizeSort`: "oldest" and "newest" are kept, anything else is "top". */
    val threadSort: String = "top",
    /** `threadViewPrefs.sort` as RN holds it: the server value over `DEFAULT_THREAD_VIEW_PREFS` (sort "hotness"). */
    val threadSortRaw: String = DEFAULT_THREAD_SORT,
    /** `threadViewPrefs.lab_treeViewEnabled`. */
    val treeViewEnabled: Boolean = false,
) {
    /** RN `preferences.threadViewPrefs`, the only part `useThreadPreferences` watches. */
    val threadView: ThreadViewPrefs get() = ThreadViewPrefs(threadSortRaw, treeViewEnabled)

    companion object {
        fun parse(text: String): UserPreferences {
            val prefs = (XrpcJson.parseToJsonElement(text).jsonObject["preferences"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            fun typed(type: String) = prefs.filter { it["\$type"]?.jsonPrimitive?.contentOrNull == type }
            val home = typed("app.bsky.actor.defs#feedViewPref").firstOrNull { it["feed"]?.jsonPrimitive?.contentOrNull == "home" }
            fun flag(name: String) = home?.get(name)?.jsonPrimitive?.booleanOrNull ?: false
            val thread = typed("app.bsky.actor.defs#threadViewPref").firstOrNull()
            val sort = thread?.get("sort")?.jsonPrimitive?.contentOrNull
            return UserPreferences(
                homeFeed = HomeFeedPrefs(hideReplies = flag("hideReplies"), hideReposts = flag("hideReposts"), hideQuotePosts = flag("hideQuotePosts")),
                threadSort = normalizeSort(sort ?: DEFAULT_THREAD_SORT),
                threadSortRaw = sort ?: DEFAULT_THREAD_SORT,
                treeViewEnabled = thread?.get("lab_treeViewEnabled")?.jsonPrimitive?.booleanOrNull ?: false,
            )
        }

        /** `DEFAULT_THREAD_VIEW_PREFS.sort` (src/state/queries/preferences/const.ts). */
        const val DEFAULT_THREAD_SORT = "hotness"

        fun normalizeSort(sort: String?): String = if (sort == "oldest" || sort == "newest") sort else "top"

        private fun JsonArray?.orEmpty() = this?.toList() ?: emptyList()
    }
}

/**
 * RN `usePreferencesQuery` cache for one account (`gcTime` infinity, `structuralSharing: replaceEqualDeep`).
 * A reload with equal values keeps the old value, so a screen sees a change only when a value differs.
 */
class PreferencesCache {
    private val _state = kotlinx.coroutines.flow.MutableStateFlow<UserPreferences?>(null)

    /** Every screen that reads preferences observes this, as RN screens observe one React Query cache entry. */
    val state: kotlinx.coroutines.flow.StateFlow<UserPreferences?> = _state

    val value: UserPreferences? get() = _state.value

    fun put(preferences: UserPreferences) {
        if (preferences != _state.value) _state.value = preferences
    }
}
