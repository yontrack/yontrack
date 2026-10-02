package net.nemerosa.ontrack.extension.scorecard.storage

import net.nemerosa.ontrack.extension.scorecard.estates.Estate
import net.nemerosa.ontrack.extension.scorecard.estates.EstateMarker
import net.nemerosa.ontrack.extension.scorecard.estates.EstateReadingConfig
import net.nemerosa.ontrack.extension.scorecard.estates.EstateSecurity

/**
 * Storage of the estates. No security, no licence check.
 */
interface EstateRepository {

    /**
     * All the estates, by name
     */
    fun findAll(): List<Estate>

    fun findById(id: Int): Estate?

    fun findByName(name: String): Estate?

    /**
     * Creates an estate
     *
     * @return ID of the estate
     */
    fun create(
        name: String,
        description: String?,
        labelIds: List<Int>,
        marker: EstateMarker?,
        readingConfigs: List<EstateReadingConfig>,
        security: EstateSecurity,
    ): Int

    /**
     * Replaces the definition of an estate
     */
    fun update(
        id: Int,
        name: String,
        description: String?,
        labelIds: List<Int>,
        marker: EstateMarker?,
        readingConfigs: List<EstateReadingConfig>,
        security: EstateSecurity,
    )

    /**
     * Deletes an estate, with its readings
     */
    fun delete(id: Int)

    /**
     * Estates using a label, by name
     */
    fun findByLabel(labelId: Int): List<Estate>

    /**
     * Estates a project belongs to — the project carries all their labels — by name
     */
    fun findByProject(projectId: Int): List<Estate>

    /**
     * IDs of the projects an estate selects: the projects which carry all its labels
     */
    fun findProjectIds(estateId: Int): List<Int>
}
