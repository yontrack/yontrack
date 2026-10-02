package net.nemerosa.ontrack.extension.scorecard.estates

import net.nemerosa.ontrack.extension.scorecard.model.ReadingDirection
import net.nemerosa.ontrack.extension.scorecard.model.ReadingKeys
import net.nemerosa.ontrack.model.labels.Label

/**
 * A group of projects selected by labels, every one of them required, and read together, with the
 * marker and the targets its projects are read against.
 *
 * @property id ID of the estate
 * @property name Unique name of the estate
 * @property description Description of the estate
 * @property labels Labels selecting the projects of the estate, all required, never empty
 * @property marker Marker the delivery readings of the estate are read up to, `null` for the default one
 * @property readingConfigs Window override and target of the readings, for the readings which have one
 * @property security What the estate expects of the security scans of its projects
 */
data class Estate(
    val id: Int,
    val name: String,
    val description: String?,
    val labels: List<Label>,
    val marker: EstateMarker?,
    val readingConfigs: List<EstateReadingConfig>,
    val security: EstateSecurity = EstateSecurity(),
) {
    /**
     * Configuration of a reading in this estate, `null` if the reading has neither a window nor a target
     */
    fun readingConfig(key: String): EstateReadingConfig? = readingConfigs.find { it.key == key }
}

/**
 * Window override and target of one reading in an estate.
 *
 * @property key Key of the reading, like `delivery.leadTime`
 * @property windowDays Number of days the reading is taken over, `null` for the window of the settings
 * @property target Threshold the reading is judged against, in the unit of the reading, `null` for none
 */
data class EstateReadingConfig(
    val key: String,
    val windowDays: Int? = null,
    val target: Double? = null,
) {
    /**
     * Which way the reading is better, fixed by the reading
     */
    val direction: ReadingDirection? get() = ReadingKeys.direction(key)
}
