package net.nemerosa.ontrack.extension.general.validation

import tools.jackson.databind.node.StringNode
import net.nemerosa.ontrack.extension.general.GeneralExtensionFeature
import net.nemerosa.ontrack.test.assertIs
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TextValidationDataTypeTest {

    private val dataType = TextValidationDataType(GeneralExtensionFeature())

    @Test
    fun toJson() {
        val json = dataType.toJson("Some text")
        assertIs<StringNode>(json) {
            assertEquals("Some text", it.asText())
        }
    }

    @Test
    fun fromJson() {
        val data = dataType.fromJson(StringNode("Some text"))
        assertEquals("Some text", data)
    }

}