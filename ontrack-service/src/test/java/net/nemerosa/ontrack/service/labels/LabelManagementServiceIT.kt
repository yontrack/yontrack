package net.nemerosa.ontrack.service.labels

import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.labels.LabelForm
import net.nemerosa.ontrack.model.labels.LabelManagement
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.security.access.AccessDeniedException
import kotlin.test.*

class LabelManagementServiceIT : AbstractDSLTestSupport() {

    @Test
    @AsAdminTest
    fun `Creating, updating and deleting a label`() {
        val category = uid("C")
        val name = uid("N")
        asUser().with(LabelManagement::class.java).execute {
            val label = labelManagementService.newLabel(
                LabelForm(
                    category = category,
                    name = name,
                    description = null,
                    color = "#FFFFFF"
                )
            )
            assertTrue(label.id > 0)
            assertEquals(category, label.category)
            assertEquals(name, label.name)
            assertNull(label.description)
            assertEquals("#FFFFFF", label.color)
            // Updating the description
            val updatedLabel = labelManagementService.updateLabel(
                label.id,
                LabelForm(
                    category = category,
                    name = name,
                    description = "New description",
                    color = "#FFFFFF"
                )
            )
            assertEquals(label.id, updatedLabel.id)
            assertEquals(category, updatedLabel.category)
            assertEquals(name, updatedLabel.name)
            assertEquals("New description", updatedLabel.description)
            assertEquals("#FFFFFF", updatedLabel.color)
            // Deleting the label
            labelManagementService.deleteLabel(label.id)
            val deletedLabel = labelManagementService.labels.find { it.id == label.id }
            assertNull(deletedLabel)
        }
    }

    @Test
    @AsAdminTest
    fun `Null category is allowed`() {
        val label = label(category = null)
        assertNull(label.category)
    }

    @Test
    fun `Cannot create a label if not authorized`() {
        val category = uid("C")
        val name = uid("N")
        assertFailsWith(AccessDeniedException::class) {
            labelManagementService.newLabel(
                LabelForm(
                    category = category,
                    name = name,
                    description = null,
                    color = "#FFFFFF"
                )
            )
        }
    }

    @Test
    fun `Cannot update a label if not authorized`() {
        val label = asAdmin {
            label()
        }
        assertFailsWith(AccessDeniedException::class) {
            labelManagementService.updateLabel(
                label.id,
                LabelForm(
                    category = label.category,
                    name = label.name,
                    description = "New description",
                    color = label.color
                )
            )
        }
    }

    @Test
    fun `Cannot delete a label if not authorized`() {
        val label = asAdmin {
            label()
        }
        assertFailsWith(AccessDeniedException::class) {
            labelManagementService.deleteLabel(label.id)
        }
    }

    @Test
    @AsAdminTest
    fun `Associating a list of labels for a project`() {
        val labels = (1..5).map { label() }
        project {
            // Sets some labels for the projects
            this.labels = labels.subList(0, 3)
            // Checks the labels of the projects
            assertEquals(
                labels.subList(0, 3).map { it.name },
                this.labels.map { it.name }
            )
            // Sets other labels for the projects (with some intersection)
            this.labels = labels.subList(3, 5)
            // Checks the labels of the projects
            assertEquals(
                labels.subList(3, 5).map { it.name },
                this.labels.map { it.name }
            )
        }
    }

    @Test
    @AsAdminTest
    fun `Looking for labels per category`() {
        val category = uid("C")
        (1..5).map { label(category = category, name = "name-$it") }
        // Other arbitrary labels
        (1..5).map { label() }
        // Looking for labels per category
        val labels = labelManagementService.findLabels(category, null)
        assertTrue(
            labels.all {
                it.category == category && it.name.matches("name-\\d".toRegex())
            }
        )
    }

    @Test
    @AsAdminTest
    fun `Looking for labels per name only`() {
        val name = uid("C")
        (1..5).map { label(category = uid("C"), name = name) }
        // Other arbitrary labels
        (1..5).map { label() }
        // Looking for labels per name
        val labels = labelManagementService.findLabels(null, name)
        assertEquals(5, labels.size)
        assertTrue(
            labels.all {
                it.name == name
            }
        )
    }

    @Test
    @AsAdminTest
    fun `Looking for labels per category and name`() {
        val category = uid("C")
        val name = uid("N")
        label(category = category, name = name)
        // Other arbitrary labels
        (1..5).map { label() }
        // Looking for labels per category and name
        val labels = labelManagementService.findLabels(category, name)
        assertEquals(1, labels.size)
        assertNotNull(labels.firstOrNull()) {
            assertEquals(category, it.category)
            assertEquals(name, it.name)
        }
    }

}