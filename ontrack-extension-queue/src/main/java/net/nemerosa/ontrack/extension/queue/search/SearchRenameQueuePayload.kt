package net.nemerosa.ontrack.extension.queue.search

import net.nemerosa.ontrack.model.structure.ProjectEntityType

/**
 * Re-indexation of the search documents of a renamed project or branch.
 *
 * It carries the entity only, not its names: the re-indexation reads the current name.
 *
 * @property type [ProjectEntityType.PROJECT] or [ProjectEntityType.BRANCH]
 * @property id ID of the renamed entity
 */
data class SearchRenameQueuePayload(
    val type: ProjectEntityType,
    val id: Int,
)
