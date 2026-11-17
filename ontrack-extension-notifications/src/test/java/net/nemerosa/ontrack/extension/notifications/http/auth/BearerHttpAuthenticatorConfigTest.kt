package net.nemerosa.ontrack.extension.notifications.http.auth

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class BearerHttpAuthenticatorConfigTest {

    @Test
    fun `Parsing without a token`() {
        assertEquals(
            emptyMap<String, String>().asJson().parse(),
            BearerHttpAuthenticatorConfig(
                token = "",
            )
        )
    }

}