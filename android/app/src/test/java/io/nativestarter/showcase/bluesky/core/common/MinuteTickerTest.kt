package io.nativestarter.showcase.bluesky.core.common

import io.nativestarter.showcase.bluesky.designsystem.TimeAgo
import io.nativestarter.showcase.bluesky.designsystem.TimeElapsed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * RN `TickEveryMinuteProvider` + `TimeElapsed` + `useGetTimeAgo` semantics (review P4). The clock starts inside a
 * minute (12:00:21), so a tick aligned to the start of the minute would show "now" where RN shows "21s".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MinuteTickerTest {
    private val posted = Instant.parse("2026-09-15T12:00:00Z")
    private val start = Instant.parse("2026-09-15T12:00:21Z")
    private val utc = ZoneId.of("UTC")

    @Test
    fun firstValueIsTheClockAtCreationNotTheStartOfTheMinute() = runTest {
        val ticker = MinuteTicker(backgroundScope, clock = { start.plusMillis(currentTime) })
        assertEquals(start, ticker.now.value)
        assertEquals("21s", TimeElapsed.text(posted.toString(), ticker.now.value, utc))
    }

    @Test
    fun valueChangesOnlyEverySixtySecondsFromCreation() = runTest {
        val ticker = MinuteTicker(backgroundScope, clock = { start.plusMillis(currentTime) })
        runCurrent()
        // 12:00:59 wall clock: RN still renders with the mount tick.
        advanceTimeBy(38_000)
        runCurrent()
        assertEquals("21s", TimeElapsed.text(posted.toString(), ticker.now.value, utc))
        // 12:01:20: still no tick; a minute-boundary ticker would have ticked at 12:01:00.
        advanceTimeBy(21_000)
        runCurrent()
        assertEquals(start, ticker.now.value)
        // 12:01:21: first tick, 60 s after creation.
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(Instant.parse("2026-09-15T12:01:21Z"), ticker.now.value)
        assertEquals("1m", TimeElapsed.text(posted.toString(), ticker.now.value, utc))
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals("2m", TimeElapsed.text(posted.toString(), ticker.now.value, utc))
    }

    @Test
    fun secondsInsideTheSameMinute() {
        assertEquals("now", TimeAgo.format(posted, posted.plusSeconds(4), utc))
        assertEquals("5s", TimeAgo.format(posted, posted.plusSeconds(5), utc))
        assertEquals("21s", TimeAgo.format(posted, start, utc))
        assertEquals("59s", TimeAgo.format(posted, posted.plusMillis(59_999), utc))
        assertEquals("1m", TimeAgo.format(posted, posted.plusSeconds(60), utc))
    }
}
