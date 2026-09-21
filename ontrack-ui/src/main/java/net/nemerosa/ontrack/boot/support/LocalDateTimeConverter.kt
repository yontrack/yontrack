package net.nemerosa.ontrack.boot.support

import net.nemerosa.ontrack.common.Time.toLocalDateTime
import org.springframework.core.convert.converter.Converter
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.ZonedDateTime

@Component
class LocalDateTimeConverter : Converter<String, LocalDateTime?> {

    override fun convert(source: String): LocalDateTime? {
        return if (source.isBlank()) {
            null
        } else {
            // Parses as a zoned date time
            val zonedDateTime = ZonedDateTime.parse(source)
            // Returns in local application time (UTC)
            toLocalDateTime(zonedDateTime)
        }
    }
}