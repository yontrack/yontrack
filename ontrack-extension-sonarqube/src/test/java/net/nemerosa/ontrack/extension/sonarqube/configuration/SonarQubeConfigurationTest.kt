package net.nemerosa.ontrack.extension.sonarqube.configuration

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SonarQubeConfigurationTest {

    @Test
    fun `Backward compatibility with the user field`() {
        assertEquals(
            SonarQubeConfiguration("test", "https://sonarqube.nemerosa.net", "token"),
            mapOf(
                "name" to "test",
                "url" to "https://sonarqube.nemerosa.net",
                "user" to "not used",
                "password" to "token",
            ).asJson().parse()
        )
    }

}