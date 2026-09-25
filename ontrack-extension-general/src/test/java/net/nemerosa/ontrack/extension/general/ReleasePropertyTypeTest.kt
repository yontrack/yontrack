package net.nemerosa.ontrack.extension.general

import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReleasePropertyTypeTest {

    private val type = ReleasePropertyType(
            extensionFeature = GeneralExtensionFeature(),
            releaseSearchExtension = mockk(),
            eventFactory = mockk(),
            eventPostService = mockk()
    )

    @Test
    fun `Contains value tests`() {
        "Rel one" `does not contain` "two"
        "Rel one" `does not contain` "one"
        "Rel one" `does not contain` "ONE"
        "1.2.3" `does not contain` "1.2"
        "1.2.3" `does not contain` "name"
        "1.2.3" `does not contain` "NAME"

        "Rel one" contains "Rel one"
        "Rel one" contains "Rel ONE"
        "Rel one" contains "Rel*"
        "Rel one" contains "*one"
        "Rel one" contains "*ONE"
    }

    private infix fun String.`does not contain`(value: String) {
        assertFalse(type.containsValue(ReleaseProperty(this), value))
    }

    private infix fun String.contains(value: String) {
        assertTrue(type.containsValue(ReleaseProperty(this), value))
    }

}