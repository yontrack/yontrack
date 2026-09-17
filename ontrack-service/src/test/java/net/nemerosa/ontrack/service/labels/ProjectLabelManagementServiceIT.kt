package net.nemerosa.ontrack.service.labels

import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.labels.LabelForm
import net.nemerosa.ontrack.model.labels.ProjectLabelManagement
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@AsAdminTest
class ProjectLabelManagementServiceIT : AbstractDSLTestSupport() {

    @Test
    fun associateProjectToLabel() {
        val label = label()
        project {
            // Association
            asUser().with(this, ProjectLabelManagement::class.java).execute {
                projectLabelManagementService.associateProjectToLabel(this, label)
            }
            // Testing the association
            asUserWithView(this).execute {
                val projects = projectLabelManagementService.getProjectsForLabel(label)
                assertTrue(projects.contains(id), "Project is associated to label")
                assertTrue(
                        projectLabelManagementService.getLabelsForProject(this)
                                .map { it.id }
                                .contains(label.id),
                        "Label is associated to project"
                )
            }
            // Removing the association
            asUser().with(this, ProjectLabelManagement::class.java).execute {
                projectLabelManagementService.unassociateProjectToLabel(this, label)
            }
            // Testing the association
            asUserWithView(this).execute {
                val projects = projectLabelManagementService.getProjectsForLabel(label)
                assertTrue(projects.isEmpty(), "Project is not associated to label")
                assertTrue(
                        projectLabelManagementService.getLabelsForProject(this).isEmpty(),
                        "Label is not associated to project"
                )
            }
        }
    }

    @Test
    fun `Labels for a project with no label`() {
        label()
        project {
            assertEquals(emptyList(), projectLabelManagementService.getLabelsForProject(this))
        }
    }

    @Test
    fun `Labels for a project are returned with all their fields`() {
        val label = labelManagementService.newLabel(
            LabelForm(
                category = uid("C"),
                name = uid("N"),
                description = "Some description",
                color = "#00FF00",
            )
        )
        project {
            labels = listOf(label)
            val labels = projectLabelManagementService.getLabelsForProject(this)
            assertEquals(1, labels.size)
            val actual = labels.first()
            assertEquals(label.id, actual.id)
            assertEquals(label.category, actual.category)
            assertEquals(label.name, actual.name)
            assertEquals("Some description", actual.description)
            assertEquals("#00FF00", actual.color)
        }
    }

    @Test
    fun `Labels for a project are sorted by category and then by name, labels without category last`() {
        val prefix = uid("P")
        val noCategory = label(category = null, name = "${prefix}-a")
        val b2 = label(category = "${prefix}-b", name = "2")
        val b1 = label(category = "${prefix}-b", name = "1")
        val a3 = label(category = "${prefix}-a", name = "3")
        project {
            labels = listOf(noCategory, b2, b1, a3)
            assertEquals(
                listOf(a3, b1, b2, noCategory).map { it.id },
                projectLabelManagementService.getLabelsForProject(this).map { it.id }
            )
        }
    }
}