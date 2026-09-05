package net.nemerosa.ontrack.common

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SystemPropertiesUtilsTest {

    @Test
    fun `Camel case to kebab case`() {
        assertEquals(
            "ontrack.config.key-store",
            "ontrack.config.keyStore".camelCaseToKebabCase(),
        )
    }

    @Test
    fun `Camel case to kebab case with consecutive capitals`() {
        assertEquals(
            "ontrack.config.api-u-r-l",
            "ontrack.config.apiURL".camelCaseToKebabCase(),
        )
    }

    @Test
    fun `Camel case to kebab case with a digit`() {
        assertEquals(
            "ontrack.config.oauth2-token",
            "ontrack.config.oauth2Token".camelCaseToKebabCase(),
        )
    }

    @Test
    fun `Camel case to environment name`() {
        assertEquals(
            "ONTRACK_CONFIG_KEY_STORE",
            "ontrack.config.keyStore".camelCaseToEnvironmentName(),
        )
    }

    @Test
    fun `Camel case to environment name for a multiple word property`() {
        assertEquals(
            "ONTRACK_CONFIG_EXTENSION_WORKFLOWS_ASYNC_CHECK_INTERVAL",
            "ontrack.config.extension.workflows.asyncCheckInterval".camelCaseToEnvironmentName(),
        )
    }

    @Test
    fun `Camel case to environment name with consecutive capitals`() {
        assertEquals(
            "ONTRACK_CONFIG_API_U_R_L",
            "ontrack.config.apiURL".camelCaseToEnvironmentName(),
        )
    }

    @Test
    fun `Camel case to environment name for an already kebab case property`() {
        assertEquals(
            "ONTRACK_CONFIG_KEY_STORE",
            "ontrack.config.key-store".camelCaseToEnvironmentName(),
        )
    }

    @Test
    fun `Camel case to environment name with wildcard`() {
        assertEquals(
            "ONTRACK_EXTENSION_QUEUE_SPECIFIC_<*>_SCALE",
            "ontrack.extension.queue.specific.<*>.scale".camelCaseToEnvironmentName(),
        )
    }

}
