package net.nemerosa.ontrack.extension.scorecard.estates

import net.nemerosa.ontrack.extension.scorecard.storage.EstateRepository
import net.nemerosa.ontrack.model.labels.Label
import net.nemerosa.ontrack.model.labels.LabelDeletionGuard
import org.springframework.stereotype.Component

/**
 * Refuses the deletion of a label an estate uses: the labels of an estate are all required, and
 * dropping one would silently widen the estate.
 *
 * Checked with or without the licence: the estates are kept when it lapses, and come back with it.
 */
@Component
class EstateLabelDeletionGuard(
    private val estateRepository: EstateRepository,
) : LabelDeletionGuard {

    override fun checkLabelDeletion(label: Label): String? {
        val names = estateRepository.findByLabel(label.id).map { it.name }
        return when (names.size) {
            0 -> null
            1 -> "it selects the projects of the estate ${names.single()}"
            else -> "it selects the projects of the estates ${names.joinToString(", ")}"
        }
    }
}
