package io.nativestarter.showcase.bluesky.core.network

import io.nativestarter.showcase.bluesky.core.common.runCatchingCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Port of RN `useToggleMutationQueue` (src/lib/hooks/useToggleMutationQueue.ts).
 *
 * - At most one request runs and one desired state waits. A new toggle replaces the waiting one.
 * - A waiting toggle equal to the last requested state is skipped (no duplicate request).
 * - A failed request keeps the last confirmed server state; the loop continues with the waiting toggle.
 * - When the queue drains, [onSettled] receives the last confirmed server state. The UI shows that state,
 *   so a failed like, or a failed compensating unlike, is visible and not hidden.
 *
 * RN rejects an already settled promise when two queued toggles are equal, which leaves the new task pending
 * forever. This port has no per-task promise, so that case cannot hang.
 *
 * Not thread safe: call [toggle] from one thread (the main thread in the app).
 */
class ToggleQueue<S>(
    private val scope: CoroutineScope,
    private val request: suspend (confirmed: S, isOn: Boolean) -> S,
    private val onSettled: (S) -> Unit,
) {
    private var waiting: Boolean? = null
    private var active = false

    val isActive: Boolean get() = active

    /** [initialState] is read only when the queue is idle, as in RN. */
    fun toggle(isOn: Boolean, initialState: () -> S) {
        waiting = isOn
        if (active) return
        active = true
        val start = initialState()
        scope.launch { drain(start) }
    }

    private suspend fun drain(start: S) {
        var confirmed = start
        var lastRequested: Boolean? = null
        try {
            while (true) {
                val next = waiting ?: break
                waiting = null
                if (next == lastRequested) continue
                lastRequested = next
                runCatchingCancellable { request(confirmed, next) }.onSuccess { confirmed = it }
            }
        } finally {
            active = false
            waiting = null
            onSettled(confirmed)
        }
    }
}
