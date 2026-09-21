package net.nemerosa.ontrack.json

import tools.jackson.core.Version
import tools.jackson.core.json.JsonWriteFeature
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.cfg.EnumFeature
import tools.jackson.databind.cfg.JsonNodeFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.ext.javatime.deser.DurationDeserializer
import tools.jackson.databind.ext.javatime.ser.DurationSerializer
import tools.jackson.module.kotlin.KotlinModule
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
    fun create(): JsonMapper = builder().build()

    /**
     * The builder of [create], for a caller which must add to the configuration.
     */
    @JvmStatic
    fun builder(): JsonMapper.Builder =
        JsonMapper.builderWithJackson2Defaults()
            // Support for JDK 8 times
            .addModule(jdkTimeModule())
            // Support for Kotlin
            .addModule(KotlinModule.Builder().build())
            // Common features
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            // Jackson 2 defaults, which Jackson 3 changes. `builderWithJackson2Defaults` sets most
            // of them already; they are repeated so that the whole configuration reads here.
            .disable(DateTimeFeature.ONE_BASED_MONTHS)
            .enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
            .enable(DateTimeFeature.WRITE_UTC_AS_OFFSET)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(EnumFeature.READ_ENUMS_USING_TO_STRING)
            .disable(EnumFeature.WRITE_ENUMS_USING_TO_STRING)
            .enable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
            .enable(MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS)
            .disable(MapperFeature.DETECT_PARAMETER_NAMES)
            .disable(MapperFeature.FIX_FIELD_NAME_UPPER_CASE_PREFIX)
            .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(MapperFeature.USE_GETTERS_AS_SETTERS)
            .enable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            // ... and the ones `builderWithJackson2Defaults` does not set
            .enable(MapperFeature.DEFAULT_VIEW_INCLUSION)
            .disable(JsonWriteFeature.ESCAPE_FORWARD_SLASHES)
            .disable(JsonWriteFeature.COMBINE_UNICODE_SURROGATES_IN_UTF8)

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
