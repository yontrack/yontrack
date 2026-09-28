package net.nemerosa.ontrack.common.doc

/**
 * This annotation is used to annotate the definition of a metrics meter.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class MetricsMeterDocumentation(
    /**
     * Type of meter
     */
    val type: MetricsMeterType,
    /**
     * Tags
     */
    val tags: Array<MetricsMeterTag> = [],
    /**
     * Fields, for the [exported][MetricsMeterType.EXPORTED] metrics
     */
    val fields: Array<MetricsMeterField> = [],
)

annotation class MetricsMeterTag(
    val name: String,
    val description: String = ""
)

annotation class MetricsMeterField(
    val name: String,
    val description: String = ""
)

enum class MetricsMeterType(val type: String) {
    GAUGE("gauge"),
    COUNT("count"),
    TIMER("timer"),
    DISTRIBUTION_SUMMARY("distribution summary"),

    /**
     * Not a Micrometer meter: a metric sent to the metrics backends (InfluxDB, Elastic) through
     * the metrics export, with tags, fields and a timestamp.
     */
    EXPORTED("exported"),
}