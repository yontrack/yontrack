package net.nemerosa.ontrack.service

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PropertyServiceImplTest {

    @Test
    fun `Unreadable property message is the message of the root cause`() {
        val error = IllegalStateException("Cannot parse json", IllegalArgumentException("Missing field: value"))
        assertEquals("Missing field: value", PropertyServiceImpl.unreadablePropertyMessage(error))
    }

    @Test
    fun `Unreadable property message without the JSON source`() {
        val error = IllegalStateException(
            "Cannot parse json",
            IllegalArgumentException(
                """
                    Parameter specified as non-null is null: authType
                     at [Source: {"name":"Bitbucket-Cloud","password":"secret"}; line: 1, column: 85] (through reference chain: BitbucketCloudConfiguration["authType"])
                """.trimIndent()
            )
        )
        assertEquals(
            """Parameter specified as non-null is null: authType (through reference chain: BitbucketCloudConfiguration["authType"])""",
            PropertyServiceImpl.unreadablePropertyMessage(error)
        )
    }

    @Test
    fun `Unreadable property message without message`() {
        val error = IllegalStateException("Cannot parse json", NullPointerException())
        assertEquals("NullPointerException", PropertyServiceImpl.unreadablePropertyMessage(error))
    }

}
