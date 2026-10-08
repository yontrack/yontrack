package net.nemerosa.ontrack.extension.api

import net.nemerosa.ontrack.model.extension.Extension
import net.nemerosa.ontrack.model.security.AgentPolicySlot
import net.nemerosa.ontrack.model.structure.Project

/**
 * Lists the slots of a project where the current agent may start a deployment pipeline, owned by the
 * extension which manages the slots.
 */
interface AgentPolicySlotExtension : Extension {

    /**
     * The slots of the [project] which admit agents, and where the current user may start a pipeline.
     */
    fun getAdmittedSlots(project: Project): List<AgentPolicySlot>
}
