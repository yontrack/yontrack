package net.nemerosa.ontrack.extension.environments.service

import net.nemerosa.ontrack.extension.environments.SlotPipeline
import net.nemerosa.ontrack.model.exceptions.InputException

/**
 * Thrown when trying to move a `FAILED` pipeline to another status. A failure is terminal: the slot
 * pipeline stays the record of a deployment which did not make it.
 */
class SlotPipelineFailedTerminalException(
    pipeline: SlotPipeline,
) : InputException(
    "Pipeline ${pipeline.fullName()} has failed and its status can no longer be changed."
)
