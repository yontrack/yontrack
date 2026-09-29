package net.nemerosa.ontrack.extension.issues

import net.nemerosa.ontrack.extension.issues.model.ConfiguredIssueService
import net.nemerosa.ontrack.extension.issues.model.IssueServiceConfigurationRepresentation
import java.util.*

interface IssueServiceRegistry {

    /**
     * Gets all the issue services
     */
    val issueServices: Collection<IssueServiceExtension>

    /**
     * Gets an issue service by its ID. It may be present or not.
     */
    fun findIssueServiceById(id: String): IssueServiceExtension?

    val availableIssueServiceConfigurations: List<IssueServiceConfigurationRepresentation>

    /**
     * Gets the association between a service and a configuration, or `null`
     * if neither service nor configuration can be found.
     */
    fun getConfiguredIssueService(issueServiceConfigurationIdentifier: String): ConfiguredIssueService?
}
