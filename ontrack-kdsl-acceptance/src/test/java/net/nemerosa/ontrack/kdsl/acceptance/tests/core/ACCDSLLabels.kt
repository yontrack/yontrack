package net.nemerosa.ontrack.kdsl.acceptance.tests.core

import net.nemerosa.ontrack.kdsl.acceptance.tests.AbstractACCDSLTestSupport
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.spec.Label
import net.nemerosa.ontrack.kdsl.spec.createLabel
import net.nemerosa.ontrack.kdsl.spec.deleteLabel
import net.nemerosa.ontrack.kdsl.spec.labels
import net.nemerosa.ontrack.kdsl.spec.setLabels
import net.nemerosa.ontrack.kdsl.spec.updateLabel
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Labels are global, so every test here names its labels with a [uid] and deletes them again,
 * rather than relying on an empty list of labels.
 */
class ACCDSLLabels : AbstractACCDSLTestSupport() {

    /**
     * Runs [code] with a freshly created label, and deletes the label afterwards.
     */
    private fun <T> withLabel(
        name: String = uid("l"),
        category: String? = uid("c"),
        description: String? = "Test label",
        color: String = "#FF0000",
        code: (Label) -> T,
    ): T {
        val label = ontrack.createLabel(
            name = name,
            category = category,
            description = description,
            color = color,
        )
        return try {
            code(label)
        } finally {
            ontrack.deleteLabel(label.id)
        }
    }

    @Test
    fun `Creating a label`() {
        val name = uid("l")
        val category = uid("c")
        withLabel(name = name, category = category, description = "My label", color = "#FF0000") { label ->
            assertEquals(category, label.category)
            assertEquals(name, label.name)
            assertEquals("My label", label.description)
            assertEquals("#FF0000", label.color)
            assertEquals("$category:$name", label.display)
        }
    }

    @Test
    fun `Creating a label without a category`() {
        val name = uid("l")
        withLabel(name = name, category = null) { label ->
            assertNull(label.category)
            assertEquals(name, label.display)
        }
    }

    @Test
    fun `Listing the labels`() {
        withLabel { label ->
            val found = ontrack.labels().find { it.id == label.id }
            assertNotNull(found, "Label is in the list") {
                assertEquals(label.name, it.name)
                assertEquals(label.category, it.category)
            }
        }
    }

    @Test
    fun `Updating a label`() {
        withLabel(color = "#FF0000") { label ->
            val newName = uid("l")
            val updated = ontrack.updateLabel(
                id = label.id,
                name = newName,
                category = label.category,
                description = "Updated description",
                color = "#00FF00",
            )
            assertEquals(label.id, updated.id)
            assertEquals(newName, updated.name)
            assertEquals("Updated description", updated.description)
            assertEquals("#00FF00", updated.color)
            // ... and this is what the server now holds
            val reloaded = ontrack.labels().find { it.id == label.id }
            assertNotNull(reloaded) {
                assertEquals(newName, it.name)
                assertEquals("#00FF00", it.color)
            }
        }
    }

    @Test
    fun `Deleting a label`() {
        val label = ontrack.createLabel(name = uid("l"), category = uid("c"), color = "#FF0000")
        ontrack.deleteLabel(label.id)
        assertNull(
            ontrack.labels().find { it.id == label.id },
            "Label is gone"
        )
    }

    @Test
    fun `Setting the labels of a project`() {
        withLabel { first ->
            withLabel { second ->
                project {
                    assertTrue(labels().isEmpty(), "A new project carries no label")

                    setLabels(listOf(first.id, second.id))
                    assertEquals(
                        listOf(first.id, second.id).sorted(),
                        labels().map { it.id }.sorted(),
                    )

                    // Setting the labels replaces the whole set
                    setLabels(second)
                    assertEquals(
                        listOf(second.id),
                        labels().map { it.id },
                    )

                    // ... including with an empty set
                    setLabels(emptyList())
                    assertTrue(labels().isEmpty(), "All labels have been removed")
                }
            }
        }
    }

    @Test
    fun `Deleting a label removes it from its projects`() {
        withLabel { kept ->
            val removed = ontrack.createLabel(name = uid("l"), category = uid("c"), color = "#0000FF")
            project {
                setLabels(kept, removed)
                assertEquals(2, labels().size)
                ontrack.deleteLabel(removed.id)
                assertEquals(
                    listOf(kept.id),
                    labels().map { it.id },
                )
            }
        }
    }
}
