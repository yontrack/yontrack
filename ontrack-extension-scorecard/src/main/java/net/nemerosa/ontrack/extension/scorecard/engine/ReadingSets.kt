package net.nemerosa.ontrack.extension.scorecard.engine

import net.nemerosa.ontrack.extension.scorecard.model.NoEstateReadingSet
import net.nemerosa.ontrack.extension.scorecard.model.ReadingSet
import net.nemerosa.ontrack.model.structure.Project
import org.springframework.stereotype.Component

/**
 * Sets a project is in: the no-estate set, always, first.
 */
@Component
class ReadingSets {

    fun of(@Suppress("UNUSED_PARAMETER") project: Project): List<ReadingSet> = listOf(NoEstateReadingSet)
}
