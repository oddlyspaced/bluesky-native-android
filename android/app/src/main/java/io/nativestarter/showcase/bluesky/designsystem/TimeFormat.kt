package io.nativestarter.showcase.bluesky.designsystem

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Port of bluesky-social-app src/lib/hooks/useTimeAgo.ts (`dateDiff` + `formatDateDiff`, short format, English).
 *
 * - All months are exactly 30 days.
 * - `earlier` after `later` returns "now".
 * - Values round down unless [roundUp] (RN `future: true`).
 * - 12 months or more returns the date (RN `i18n.date(earlier)`, en-US short: M/D/YYYY).
 */
object TimeAgo {
    private const val NOW = 5L
    private const val MINUTE = 60L
    private const val HOUR = MINUTE * 60
    private const val DAY = HOUR * 24
    private const val MONTH_30 = DAY * 30

    fun format(earlier: Instant, later: Instant, zone: ZoneId = ZoneId.systemDefault(), roundUp: Boolean = false): String {
        // date-fns differenceInSeconds truncates toward zero.
        val diffSeconds = (later.toEpochMilli() - earlier.toEpochMilli()) / 1000
        fun div(unit: Long): Long {
            val v = diffSeconds.toDouble() / unit
            return if (roundUp) ceil(v).toLong() else floor(v).toLong()
        }
        return when {
            diffSeconds < NOW -> "now"
            diffSeconds < MINUTE -> "${diffSeconds}s"
            diffSeconds < HOUR -> "${div(MINUTE)}m"
            diffSeconds < DAY -> "${div(HOUR)}h"
            diffSeconds < MONTH_30 -> "${div(DAY)}d"
            else -> {
                val months = div(MONTH_30)
                if (months < 12) "${months}mo"
                else DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US).format(earlier.atZone(zone))
            }
        }
    }

    fun format(earlierIso: String, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        format(Instant.parse(earlierIso), now, zone)
}

/**
 * Port of `niceDate(i18n, date, 'dot separated')` in src/lib/strings/time.ts for en-US:
 * `${timeStyle short} · ${dateStyle medium}`, for example "12:55 AM · Sep 15, 2026".
 */
object NiceDate {
    private val time = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val date = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

    fun dotSeparated(iso: String, zone: ZoneId = ZoneId.systemDefault()): String {
        val z = Instant.parse(iso).atZone(zone)
        return "${time.format(z)} · ${date.format(z)}"
    }
}

/** Port of useFormatPostStatCount for counts below 1000 (the seeded data); larger counts use compact form. */
object StatCount {
    fun format(count: Int): String = when {
        count < 1000 -> count.toString()
        count < 1_000_000 -> trim(count / 1000.0) + "K"
        else -> trim(count / 1_000_000.0) + "M"
    }

    private fun trim(v: Double): String {
        val s = String.format(Locale.US, "%.1f", floor(v * 10) / 10)
        return s.removeSuffix(".0")
    }
}
