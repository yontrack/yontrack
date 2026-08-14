package net.nemerosa.ontrack.yaml

import net.nemerosa.ontrack.json.parseAsJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class YamlTest {

    private val yaml = Yaml()

    @Test
    fun `writing a single document does not emit a document start marker`() {
        val output = yaml.write(listOf("""{"name":"Test"}""".parseAsJson()))
        assertFalse(output.startsWith("---"), "No document start marker for a single document")
        assertEquals("""name: "Test"""", output.trim())
    }

    @Test
    fun `writing several documents separates them with a document start marker`() {
        val output = yaml.write(
            listOf(
                """{"name":"One"}""".parseAsJson(),
                """{"name":"Two"}""".parseAsJson(),
            )
        )
        assertFalse(output.startsWith("---"), "No leading document start marker")
        assertTrue("---" in output, "Documents are separated by a document start marker")
    }

    @Test
    fun `single document round trip`() {
        val nodes = listOf("""{"name":"Test","values":[1,2,3]}""".parseAsJson())
        assertEquals(nodes, yaml.read(yaml.write(nodes)))
    }

    @Test
    fun `several documents round trip`() {
        val nodes = listOf(
            """{"name":"One"}""".parseAsJson(),
            """{"name":"Two"}""".parseAsJson(),
        )
        assertEquals(nodes, yaml.read(yaml.write(nodes)))
    }

    @Test
    fun `writing a list as a root node`() {
        val output = yaml.write(listOf("""[{"name":"One"},{"name":"Two"}]""".parseAsJson()))
        assertEquals(
            """
                - name: "One"
                - name: "Two"
            """.trimIndent(),
            output.trim()
        )
    }

}
