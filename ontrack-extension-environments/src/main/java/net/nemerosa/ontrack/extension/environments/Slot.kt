package net.nemerosa.ontrack.extension.environments

import net.nemerosa.ontrack.model.structure.Project
import java.util.*

data class Slot(
    val id: String = UUID.randomUUID().toString(),
    val environment: Environment,
    val description: String?,
    val project: Project,
    val qualifier: String,
    /**
     * Does this slot admit agents? An agent may start, run and finish a deployment pipeline only in a
     * slot which admits agents - and only if its owner may. It never satisfies a manual approval.
     */
    val agentsAdmitted: Boolean = false,
) {
    companion object {
        const val DEFAULT_QUALIFIER = ""
    }

    fun fullName() = "${environment.name}/${project.name}${
        qualifier.takeIf { it.isNotBlank() }?.let { "/$it" } ?: ""
    }"

    override fun toString(): String = fullName()
    fun withDescription(description: String?) = copy(description = description)

    fun withAgentsAdmitted(agentsAdmitted: Boolean) = copy(agentsAdmitted = agentsAdmitted)

}
