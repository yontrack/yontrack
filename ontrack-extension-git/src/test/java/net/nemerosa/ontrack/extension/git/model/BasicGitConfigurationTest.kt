package net.nemerosa.ontrack.extension.git.model

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class BasicGitConfigurationTest {

    @Test
    fun obfuscation_of_password() {
        val configuration = BasicGitConfiguration.empty()
            .withUser("test").withPassword("secret")
        assertEquals("", configuration.obfuscate().password)
    }

}