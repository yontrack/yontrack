package net.nemerosa.ontrack.extension.config.model

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.config.extensions.CIConfigExtensionService
import net.nemerosa.ontrack.json.asJson
import org.junit.jupiter.api.Test
import tools.jackson.databind.node.NullNode
import kotlin.test.assertEquals

/**
 * Merging a custom configuration into the default one, property by property.
 */
class PropertiesMergeTest {

    private val extensions = mockk<CIConfigExtensionService> {
        every { merge(any(), any()) } returns emptyList()
    }

    private val first = "net.nemerosa.First"
    private val second = "net.nemerosa.Second"

    private fun value(type: String, text: String) = PropertyConfiguration(type, mapOf("text" to text).asJson())
    private fun deletion(type: String) = PropertyConfiguration(type, NullNode.instance)

    /**
     * Merging at each of the levels which accept properties, all three having to agree.
     */
    private fun merged(
        defaults: List<PropertyConfiguration>,
        custom: List<PropertyConfiguration>,
    ): List<PropertyConfiguration> {
        val project = ProjectConfiguration(properties = defaults)
            .merge(ProjectConfiguration(properties = custom), extensions)
            .properties
        val branch = BranchConfiguration(properties = defaults)
            .merge(BranchConfiguration(properties = custom), extensions)
            .properties
        val build = BuildConfiguration(properties = defaults)
            .merge(BuildConfiguration(properties = custom), extensions)
            .properties
        assertEquals(project, branch, "Project and branch")
        assertEquals(project, build, "Project and build")
        return project
    }

    @Test
    fun `A custom null cancels a default value`() {
        assertEquals(
            listOf(deletion(first)),
            merged(
                defaults = listOf(value(first, "default")),
                custom = listOf(deletion(first)),
            )
        )
    }

    @Test
    fun `A custom value overrides a default null`() {
        assertEquals(
            listOf(value(first, "custom")),
            merged(
                defaults = listOf(deletion(first)),
                custom = listOf(value(first, "custom")),
            )
        )
    }

    @Test
    fun `A custom value overrides a default value, which is not applied any more`() {
        assertEquals(
            listOf(value(first, "custom")),
            merged(
                defaults = listOf(value(first, "default")),
                custom = listOf(value(first, "custom")),
            )
        )
    }

    @Test
    fun `The properties set on one side only are kept`() {
        assertEquals(
            listOf(value(first, "default"), deletion(second)),
            merged(
                defaults = listOf(value(first, "default")),
                custom = listOf(deletion(second)),
            )
        )
    }

    @Test
    fun `The same type twice in a block still gives way to the custom value`() {
        assertEquals(
            listOf(value(first, "custom")),
            merged(
                defaults = listOf(value(first, "alias"), value(first, "class name")),
                custom = listOf(value(first, "custom")),
            )
        )
    }

}
