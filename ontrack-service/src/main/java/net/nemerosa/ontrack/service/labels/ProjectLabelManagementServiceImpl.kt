package net.nemerosa.ontrack.service.labels

import net.nemerosa.ontrack.model.labels.*
import net.nemerosa.ontrack.model.security.ProjectView
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.repository.ProjectLabelRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class ProjectLabelManagementServiceImpl(
        private val projectLabelRepository: ProjectLabelRepository,
        private val labelManagementService: LabelManagementService,
        private val securityService: SecurityService
) : ProjectLabelManagementService {

    override fun getLabelsForProject(project: Project): List<Label> =
            projectLabelRepository.getLabelsForProject(project.id())
                    .map { it.toLabel() }

    override fun hasProjectLabel(project: Project, label: Label): Boolean =
        projectLabelRepository.hasProjectLabel(project.id(), label.id)

    override fun getProjectsForLabel(label: Label): List<ID> =
            projectLabelRepository.getProjectsForLabel(label.id)
                    .filter { securityService.isProjectFunctionGranted(it, ProjectView::class.java) }
                    .map { ID.of(it) }

    override fun associateProjectToLabel(project: Project, label: Label) {
        securityService.checkProjectFunction(project, ProjectLabelManagement::class.java)
        projectLabelRepository.associateProjectToLabel(project.id(), label.id)
    }

    override fun unassociateProjectToLabel(project: Project, label: Label) {
        securityService.checkProjectFunction(project, ProjectLabelManagement::class.java)
        projectLabelRepository.unassociateProjectToLabel(project.id(), label.id)
    }

    override fun associateProjectToLabels(project: Project, form: ProjectLabelForm) {
        securityService.checkProjectFunction(project, ProjectLabelManagement::class.java)
        // Checks all labels
        form.labels.map { labelManagementService.getLabel(it) }
        // Saves the association
        projectLabelRepository.associateProjectToLabels(project.id(), form)
    }
}