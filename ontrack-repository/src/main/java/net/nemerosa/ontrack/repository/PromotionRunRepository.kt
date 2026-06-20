package net.nemerosa.ontrack.repository

import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.structure.PromotionRunFieldValue

interface PromotionRunRepository {

    /**
     * For a [project], returns the last promotion run having occurred within the whole
     * project for a given promotion mentioned by name.
     */
    fun getLastPromotionRunForProject(project: Project, promotionName: String): PromotionRun?

    /**
     * For a [branch], returns the last promotion run having occurred for a given promotion mentioned by name.
     */
    fun getLastPromotionRunForBranch(branch: Branch, promotionName: String): PromotionRun?

    /**
     * Checks if the given [build] is promoted or not to the given [promotion level][promotionLevel].
     */
    fun isBuildPromoted(build: Build, promotionLevel: PromotionLevel): Boolean

    fun savePromotionRunFieldValues(promotionRunId: ID, fieldValues: List<PromotionRunFieldValue>)

    fun getPromotionRunFieldValues(promotionRunId: ID): List<PromotionRunFieldValue>
}