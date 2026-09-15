package io.nativestarter.showcase.bluesky.core.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Port of RN `TickEveryMinuteProvider` (src/state/shell/tick-every-minute.tsx):
 * `useState(Date.now())` when the provider mounts, then `setInterval(() => setTick(Date.now()), 60_000)`.
 *
 * - [now] is the clock value at creation, not the start of a minute. A post 21 s old shows "21s".
 * - It changes only every [periodMs] from creation, so between ticks relative times do not advance.
 * - RN mounts the provider in `App` (`ShellStateProvider`), outside the `key={currentAccount?.did}` fragment. So one
 *   ticker lives for the process (in `AppContainer`): sign-in and Activity recreation do not restart it.
 */
class MinuteTicker(
    scope: CoroutineScope,
    private val clock: () -> Instant = Instant::now,
    private val periodMs: Long = 60_000,
) {
    private val _now = MutableStateFlow(clock())
    val now: StateFlow<Instant> = _now.asStateFlow()

    init {
        scope.launch {
            while (isActive) {
                delay(periodMs)
                _now.value = clock()
            }
        }
    }
}
