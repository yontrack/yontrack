package net.nemerosa.ontrack.model.structure

interface NumericValidationDataType<C, T> : ValidationDataType<C, T> {

    /**
     * Ordered list of metrics
     *
     * @return List of metrics or null if they have to be determined dynamically
     */
    fun getMetricNames(): List<String>?

    /**
     * Optional list of colors
     *
     * @return Color palette for the metrics (if not a default)
     */
    fun getMetricColors(): List<String> = listOf(MetricsColors.NEUTRAL)

    /**
     * Gets some metrics about this data.
     */
    fun getNumericMetrics(data: T): Map<String, Double>

    /**
     * IDs of the other data types this one is compatible with: the metrics chart of a validation
     * stamp of this type also reads the runs of these types, so that switching a stamp from one
     * of them to this type keeps its chart history.
     *
     * A compatible type must be numeric and its [getNumericMetrics] must use the names of
     * [getMetricNames] of this type. A type which is not declared here restarts the chart.
     *
     * @return IDs of the compatible types (their [ValidationDataTypeDescriptor.id])
     */
    val compatibleDataTypes: Set<String>
        get() = emptySet()

}
