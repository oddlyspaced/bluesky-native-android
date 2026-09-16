package io.nativestarter.showcase.bluesky.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nativestarter.showcase.bluesky.core.common.runCatchingCancellable
import io.nativestarter.showcase.bluesky.core.network.BskyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Rules from RN src/state/queries/notifications/unread.tsx. Pure, so they are unit tested. */
object UnreadPolicy {
    const val UPDATE_INTERVAL_MS = 30_000L

    /** A poll is skipped when the cached count is not 0 and it is 30+ or a coin flip says so (half of polls). */
    fun skipPoll(cachedCount: Int, random: Double): Boolean =
        cachedCount != 0 && (cachedCount >= 30 || random >= 0.5)

    /** Badge text: `>= 30` gives "30+", 0 gives "", otherwise the number. */
    fun badge(count: Int): String = when {
        count >= 30 -> "30+"
        count == 0 -> ""
        else -> count.toString()
    }

    /** Accessibility hint for the notifications button (BottomBar.tsx): "" or "# unread item(s)". */
    fun hint(badge: String): String = when {
        badge.isEmpty() -> ""
        badge.contains('+') -> "$badge unread items"
        badge == "1" -> "1 unread item"
        else -> "$badge unread items"
    }
}

class UnreadNotificationsViewModel(
    private val repository: BskyRepository,
    private val random: () -> Double = { Math.random() },
) : ViewModel() {
    private val _badge = MutableStateFlow("")
    val badge: StateFlow<String> = _badge.asStateFlow()
    private var cachedCount = 0
    private var inFlight = false

    init {
        check(isPoll = false)
    }

    /** RN `checkUnread({isPoll})`. The caller skips it while the app is not active. */
    fun check(isPoll: Boolean) {
        if (inFlight) return
        if (isPoll && UnreadPolicy.skipPoll(cachedCount, random())) return
        inFlight = true
        viewModelScope.launch {
            try {
                runCatchingCancellable { repository.unreadNotificationCount() }
                    .onSuccess { count ->
                        cachedCount = count
                        _badge.value = UnreadPolicy.badge(count)
                    }
            } finally {
                // Cancellation must not leave the flag set, or the badge never updates again (review B22).
                inFlight = false
            }
        }
    }
}
