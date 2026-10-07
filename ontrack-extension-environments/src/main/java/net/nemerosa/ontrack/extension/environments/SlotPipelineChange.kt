package net.nemerosa.ontrack.extension.environments

import net.nemerosa.ontrack.model.structure.SignatureActor
import java.time.LocalDateTime
import java.util.*

/**
 * Change happening to a pipeline.
 *
 * @property user Who made the change
 * @property actor The agent behind the change, null for a person
 */
data class SlotPipelineChange(
    val id: String = UUID.randomUUID().toString(),
    val pipeline: SlotPipeline,
    val user: String,
    val timestamp: LocalDateTime,
    val type: SlotPipelineChangeType,
    val status: SlotPipelineStatus?,
    val message: String?,
    val overrideMessage: String? = null,
    val actor: SignatureActor? = null,
)
