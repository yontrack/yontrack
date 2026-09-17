package net.nemerosa.ontrack.service.labels

import net.nemerosa.ontrack.model.labels.ProjectLabelManagement
import net.nemerosa.ontrack.model.security.*
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.service.security.CoreAuthorizationContributor
import org.springframework.stereotype.Component

/**
 * Contributes the `labels` action on the `project` authorizations, telling
 * whether the current user may set the labels of a project.
 */
@Component
class ProjectLabelAuthorizationContributor(
    private val securityService: SecurityService,
) : AuthorizationContributor {

    override fun appliesTo(context: Any): Boolean = context is Project

    override fun getAuthorizations(user: AuthenticatedUser, context: Any): List<Authorization> =
        listOf(
            Authorization(
                CoreAuthorizationContributor.PROJECT,
                LABELS,
                securityService.isProjectFunctionGranted<ProjectLabelManagement>(context as Project)
            )
        )

    companion object {
        /**
         * Action to manage the labels of a project
         */
        const val LABELS = "labels"
    }
}
