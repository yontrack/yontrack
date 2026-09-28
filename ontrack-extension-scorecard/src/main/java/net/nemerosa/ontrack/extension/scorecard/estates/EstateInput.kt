package net.nemerosa.ontrack.extension.scorecard.estates

/**
 * What an estate is created or updated from.
 *
 * @property name Unique name of the estate
 * @property description Description of the estate
 * @property labels Labels selecting the projects, as `category:name` or `name`; at least one, all existing
 * @property marker Marker the delivery readings are read up to, `null` for the default one
 * @property readingConfigs Window override and target per reading, at most one per reading
 */
data class EstateInput(
    val name: String,
    val description: String? = null,
    val labels: List<String>,
    val marker: EstateMarker? = null,
    val readingConfigs: List<EstateReadingConfig> = emptyList(),
)
