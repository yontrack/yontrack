package net.nemerosa.ontrack.json

import com.fasterxml.jackson.core.Version
import com.fasterxml.jackson.core.json.JsonWriteFeature
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.deser.DurationDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.DurationSerializer
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.time.*

/**
 * The one JSON mapper configuration of Yontrack.
 *
 * Every setting whose default differs between Jackson 2 and Jackson 3 is written out, at its
 * Jackson 2 value: the JSON Yontrack stores and exchanges must not change with the Jackson version
 * (see ADR 0016). Adopting a Jackson 3 default is a decision of its own.
 *
 * To write with a JSON view, use `create().writerWithView(view)`.
 */
object ObjectMapperFactory {

    private val JSON_MODULE_VERSION: Version = Version(
        1,
        0,
        0,
        null,
        "net.nemerosa.ontrack",
        "ontrack-json"
    )

    @JvmStatic
    fun create(): ObjectMapper =
        JsonMapper.builder()
            // Support for JDK 8 times
            .addModule(jdkTimeModule())
            // Support for Kotlin
            .addModule(KotlinModule.Builder().build())
            // Common features
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            // Jackson 2 defaults, which Jackson 3 changes
            .enable(MapperFeature.DEFAULT_VIEW_INCLUSION)
            .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(MapperFeature.USE_GETTERS_AS_SETTERS)
            .enable(MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS)
            .disable(MapperFeature.FIX_FIELD_NAME_UPPER_CASE_PREFIX)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
            .enable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .disable(JsonWriteFeature.ESCAPE_FORWARD_SLASHES)
            .build()

    private fun jdkTimeModule(): SimpleModule {
        val jdkTimeModule = SimpleModule(
            "JDKTimeModule",
            JSON_MODULE_VERSION
        )
        // LocalDateTime
        jdkTimeModule.addSerializer(LocalDateTime::class.java, JDKLocalDateTimeSerializer())
        jdkTimeModule.addDeserializer(LocalDateTime::class.java, JDKLocalDateTimeDeserializer())
        // LocalTime
        jdkTimeModule.addSerializer(LocalTime::class.java, JDKLocalTimeSerializer())
        jdkTimeModule.addDeserializer(LocalTime::class.java, JDKLocalTimeDeserializer())
        // LocalDate
        jdkTimeModule.addSerializer(LocalDate::class.java, JDKLocalDateSerializer())
        jdkTimeModule.addDeserializer(LocalDate::class.java, JDKLocalDateDeserializer())
        // YearMonth
        jdkTimeModule.addSerializer(YearMonth::class.java, JDKYearMonthSerializer())
        jdkTimeModule.addDeserializer(YearMonth::class.java, JDKYearMonthDeserializer())
        // Support for durations
        jdkTimeModule.addSerializer(Duration::class.java, DurationSerializer.INSTANCE)
        jdkTimeModule.addDeserializer(Duration::class.java, DurationDeserializer.INSTANCE)
        // OK
        return jdkTimeModule
    }
}
