package io.nativestarter.showcase.bluesky.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** Cases follow the rules in RN src/lib/hooks/useTimeAgo.ts (dateDiff + formatDateDiff, short). */
class TimeFormatTest {
    private val base = Instant.parse("2026-09-15T00:00:00Z")
    private fun ago(seconds: Long) = TimeAgo.format(base.minusSeconds(seconds), base, ZoneId.of("UTC"))

    @Test fun belowFiveSecondsIsNow() {
        assertEquals("now", ago(0))
        assertEquals("now", ago(4))
    }

    @Test fun earlierAfterLaterIsNow() = assertEquals("now", TimeAgo.format(base.plusSeconds(90), base))

    @Test fun seconds() {
        assertEquals("5s", ago(5))
        assertEquals("59s", ago(59))
    }

    @Test fun minutesRoundDown() {
        assertEquals("1m", ago(60))
        assertEquals("1m", ago(119))
        assertEquals("59m", ago(3599))
    }

    @Test fun hoursRoundDown() {
        assertEquals("1h", ago(3600))
        assertEquals("23h", ago(86_399))
    }

    @Test fun daysRoundDown() {
        assertEquals("1d", ago(86_400))
        assertEquals("29d", ago(86_400L * 30 - 1))
    }

    @Test fun monthsAreThirtyDays() {
        assertEquals("1mo", ago(86_400L * 30))
        assertEquals("11mo", ago(86_400L * 30 * 12 - 1))
    }

    @Test fun twelveMonthsOrMoreIsDate() =
        assertEquals("9/20/2025", ago(86_400L * 360))

    @Test fun roundUpForFuture() =
        assertEquals("2m", TimeAgo.format(base.minusSeconds(61), base, ZoneId.of("UTC"), roundUp = true))

    @Test fun niceDateDotSeparated() =
        assertEquals("12:55 AM · Sep 15, 2026", NiceDate.dotSeparated("2026-09-15T00:55:00Z", ZoneId.of("UTC")))

    @Test fun statCount() {
        assertEquals("0", StatCount.format(0))
        assertEquals("999", StatCount.format(999))
        assertEquals("1K", StatCount.format(1000))
        assertEquals("1.2K", StatCount.format(1250))
        assertEquals("3.4M", StatCount.format(3_450_000))
    }
}
