package net.nemerosa.ontrack.extension.notifications.http.auth

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class HeaderHttpAuthenticatorConfigTest {

    @Test
    fun `Parsing without a value`() {
        assertEquals(
            mapOf(
                "name" to "test",
            ).asJson().parse(),
            HeaderHttpAuthenticatorConfig(
                name = "test",
                value = "",
            )
        )
    }

}