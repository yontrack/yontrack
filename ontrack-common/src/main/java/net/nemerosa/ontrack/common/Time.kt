package net.nemerosa.ontrack.common

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.util.*

object Time: TimeServer {

    private val TIME_STORAGE_FORMAT: DateTimeFormatter = DateTimeFormatterBuilder()
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 4, true)
            .toFormatter(Locale.ENGLISH)

    /**
     * Keeps only the 4 first digits of the nano seconds field.
     */
    private val DATE_TIME_STORAGE_FORMAT: DateTimeFormatter = DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .appendLiteral('T')
            .append(TIME_STORAGE_FORMAT)
            .toFormatter(Locale.ENGLISH)

    /**
     * Keeps only the first 4 digits of the nanoseconds field.
     */
    fun truncate(time: LocalDateTime): LocalDateTime {
        val nano = time.nano
        val base = time.truncatedTo(ChronoUnit.SECONDS)
        val truncatedNano = nano / 100000 * 100000
        return base.with(ChronoField.NANO_OF_SECOND, truncatedNano.toLong())
    }

    @JvmStatic
    fun now(): LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)

    override val now: LocalDateTime get() = now()

    fun toLocalDateTime(zonedDateTime: ZonedDateTime): LocalDateTime =
            zonedDateTime.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()

    /**
     * Keeps only the 4 first digits of the nano seconds field.
     */
    @JvmStatic
    fun store(time: LocalDateTime): String = time.format(DATE_TIME_STORAGE_FORMAT)

    /**
     * Keeps only the 4 first digits of the nano seconds field.
     */
    @JvmStatic
    fun fromStorage(value: String?): LocalDateTime? =
            if (value.isNullOrBlank()) {
                null
            } else {
                LocalDateTime.parse(value, DATE_TIME_STORAGE_FORMAT.withZone(ZoneOffset.UTC))
            }

    @JvmStatic
    fun from(date: Date?, defaultValue: LocalDateTime?): LocalDateTime? =
            if (date != null) {
                LocalDateTime.ofInstant(Instant.ofEpochMilli(date.time), ZoneOffset.UTC)
            } else {
                defaultValue
            }

    /**
     * Returns a UTC local date/time from an Epoch time in milliseconds
     *
     * @param epochMillis Epoch time in milliseconds
     * @return UTC local date time
     */
    fun from(epochMillis: Long): LocalDateTime =
            LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC)

    /**
     * Converts a date/time to Epoch time in milliseconds.
     *
     * @param time Epoch time in milliseconds
     * @return UTC local date time
     */
    fun toEpochMillis(time: LocalDateTime): Long = time.toInstant(ZoneOffset.UTC).toEpochMilli()
}