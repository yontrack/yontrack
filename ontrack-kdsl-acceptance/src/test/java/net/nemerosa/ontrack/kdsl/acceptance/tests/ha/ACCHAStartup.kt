package net.nemerosa.ontrack.kdsl.acceptance.tests.ha

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.kdsl.acceptance.tests.ACCProperties
import net.nemerosa.ontrack.kdsl.spec.configurations.configurations
import net.nemerosa.ontrack.kdsl.spec.extension.jira.jira
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Both backends of the stack start together, on an empty database, with the same configuration as
 * code (#2087): their startup tasks run one replica at a time, so that both of them start, the
 * configuration is applied, and there is one administrator account.
 *
 * The stack starts A and B side by side, each with `compose/casc/kdsl-ha.yaml`: by the time these
 * tests run, both have started.
 */
class ACCHAStartup : AbstractACCHATestSupport() {

    @Test
    fun `the configuration as code is applied when both backends start together`() {
        listOf("A" to ontrackA, "B" to ontrackB).forEach { (backend, client) ->
            (1..CASC_JIRA_CONFIGURATIONS).forEach { index ->
                val name = "ha-startup-$index"
                assertNotNull(client.configurations.jira.findByName(name), "Jira configuration $name read through $backend") {
                    assertEquals("https://jira-$index.example.com", it.url)
                }
            }
        }
    }

    @Test
    fun `one administrator account is provisioned when both backends start together`() {
        listOf(ACCProperties.HA.A.url, ACCProperties.HA.B.url).forEach { url ->
            val accounts = graphQL(
                url,
                """
                    {
                        accounts(name: "$ADMIN_EMAIL") {
                            email
                            groups { name }
                        }
                    }
                """
            ).path("data").path("accounts").filter { account: JsonNode -> account.path("email").asString() == ADMIN_EMAIL }
            assertEquals(1, accounts.size, "One administrator account read through $url")
            val groups: List<String> = accounts.first().path("groups").toList().map { group -> group.path("name").asString() }
            assertEquals(
                listOf("Administrators"),
                groups,
                "The administrator account is in its group, once",
            )
        }
    }

    companion object {
        /**
         * Number of Jira configurations in `compose/casc/kdsl-ha.yaml`: several, so that two
         * replicas applying it together would collide on one of them.
         */
        private const val CASC_JIRA_CONFIGURATIONS = 5

        /**
         * Email of the provisioned administrator, the default of
         * `ontrack.config.security.authorization.admin.email`.
         */
        private const val ADMIN_EMAIL = "admin@ontrack.local"
    }
}
