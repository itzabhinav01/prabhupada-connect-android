package com.prabhupadaconnect.vedabase.core.util

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Fixed-width, millisecond-precision ISO-8601 UTC timestamp formatting.
 *
 * User-database timestamp columns are compared both as [Instant] values (in
 * Kotlin, for LWW conflict resolution) and as raw strings (in SQL, for the
 * `updatedUtc > ?` incremental-sync watermark filter) - a fixed width with
 * millisecond precision guarantees those two comparisons always agree,
 * unlike [Instant.toString] which trims trailing zero fractional digits.
 */
object IsoTime {
    private val formatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

    fun format(instant: Instant): String = formatter.format(instant)

    fun parse(text: String): Instant = Instant.parse(text)

    fun now(): String = format(Instant.now())
}
