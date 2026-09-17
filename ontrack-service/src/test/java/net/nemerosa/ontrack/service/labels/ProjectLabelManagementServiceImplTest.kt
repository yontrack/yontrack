package net.nemerosa.ontrack.service.labels

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.nemerosa.ontrack.model.labels.LabelManagementService
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.NameDescription
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.repository.LabelRecord
import net.nemerosa.ontrack.repository.ProjectLabelRepository
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ProjectLabelManagementServiceImplTest {

    private val projectLabelRepository = mockk<ProjectLabelRepository>()
    private val labelManagementService = mockk<LabelManagementService>()
    private val securityService = mockk<SecurityService>()

    private val service = ProjectLabelManagementServiceImpl(
        projectLabelRepository,
        labelManagementService,
        securityService,
    )

    @Test
    fun `Labels for a project are read in one repository call, without any lookup per label`() {
        val project = Project.of(NameDescription.nd("P", "")).withId(ID.of(1))
        every { projectLabelRepository.getLabelsForProject(1) } returns listOf(
            LabelRecord(id = 10, category = "a", name = "x", description = "Desc", color = "#FF0000"),
            LabelRecord(id = 11, category = null, name = "y", description = null, color = "#00FF00"),
        )

        val labels = service.getLabelsForProject(project)

        assertEquals(listOf(10, 11), labels.map { it.id })
        assertEquals(listOf("a", null), labels.map { it.category })
        assertEquals(listOf("x", "y"), labels.map { it.name })
        assertEquals(listOf("Desc", null), labels.map { it.description })
        assertEquals(listOf("#FF0000", "#00FF00"), labels.map { it.color })
        verify(exactly = 1) { projectLabelRepository.getLabelsForProject(1) }
        verify(exactly = 0) { labelManagementService.getLabel(any()) }
    }
}
