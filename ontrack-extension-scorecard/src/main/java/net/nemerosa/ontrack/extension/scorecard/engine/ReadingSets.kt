package net.nemerosa.ontrack.extension.scorecard.engine

import net.nemerosa.ontrack.extension.scorecard.license.ScorecardLicense
import net.nemerosa.ontrack.extension.scorecard.model.EstateReadingSet
import net.nemerosa.ontrack.extension.scorecard.model.NoEstateReadingSet
import net.nemerosa.ontrack.extension.scorecard.model.ReadingSet
import net.nemerosa.ontrack.extension.scorecard.storage.EstateRepository
import net.nemerosa.ontrack.model.structure.Project
import org.springframework.stereotype.Component

/**
 * Sets a project is in: the no-estate set, always, first; then the set of each estate the project
 * belongs to, by name — only when the licence allows the estates.
 */
@Component
class ReadingSets(
    private val scorecardLicense: ScorecardLicense,
    private val estateRepository: EstateRepository,
) {

    fun of(project: Project): List<ReadingSet> =
        listOf(NoEstateReadingSet) + if (scorecardLicense.estatesEnabled) {
            estateRepository.findByProject(project.id()).map { EstateReadingSet(it) }
        } else {
            emptyList()
        }
}
