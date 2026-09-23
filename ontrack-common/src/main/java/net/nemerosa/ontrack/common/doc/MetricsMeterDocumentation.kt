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
)

annotation class MetricsMeterTag(
    val name: String,
    val description: String = ""
)

enum class MetricsMeterType(val type: String) {
    GAUGE("gauge"),
    COUNT("count"),
    TIMER("timer"),
    DISTRIBUTION_SUMMARY("distribution summary"),
}