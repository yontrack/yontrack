package net.nemerosa.ontrack.model.labels

import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class LabelFormTest {

    @Test
    fun `Category format`() {
        form(category = null).validate()
        form(category = "language").validate()
        assertFailsWith<LabelFormatException> {
            form(category = "language and tech").validate()
        }
        assertFailsWith<LabelFormatException> {
            form(category = "language<tech>").validate()
        }
    }

    @Test
    fun `Name format`() {
        form(name = "kotlin").validate()
        assertFailsWith<LabelFormatException> {
            form(name = "kotlin and java").validate()
        }
        assertFailsWith<LabelFormatException> {
            form(name = "kotlin<java>").validate()
        }
    }

    @Test
    fun `Color format`() {
        form(color = "#FF0000").validate()
        form(color = "#ff0000").validate()
        assertFailsWith<LabelFormatException> {
            form(color = "#F00").validate()
        }
        assertFailsWith<LabelFormatException> {
            form(color = "red").validate()
        }
    }

    private fun form(
            category: String? = uid("C"),
            name: String = uid("N"),
            color: String = "#FF0000"
    ) = LabelForm(
            category = category,
            name = name,
            description = null,
            color = color
    )

}