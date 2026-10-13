package net.nemerosa.ontrack.kdsl.acceptance.tests.ha

import net.nemerosa.ontrack.kdsl.acceptance.tests.ACCProperties
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.connector.graphql.GraphQLClientException
import net.nemerosa.ontrack.kdsl.connector.support.DefaultConnector
import net.nemerosa.ontrack.kdsl.spec.Ontrack
import net.nemerosa.ontrack.kdsl.spec.admin.admin
import net.nemerosa.ontrack.kdsl.spec.extension.github.ingestion.gitHubIngestion
import net.nemerosa.ontrack.kdsl.spec.generateToken
import net.nemerosa.ontrack.kdsl.spec.revokeToken
import net.nemerosa.ontrack.kdsl.spec.settings.security
import net.nemerosa.ontrack.kdsl.spec.settings.settings
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Settings changed through a replica apply to the next request served by another one: they are
 * read once per request, never cached across requests (#2080).
 *
 * Each test first has B read the settings, which it would have kept in a cache, then changes them
 * through A, and checks that the very next request through B applies them.
 */
class ACCHASettings : AbstractACCHATestSupport() {

    @Test
    fun `a security setting changed through A applies to the next request through B`() {
        val project = ontrackA.createProject(uid("p_"), "")
        val user = ontrackA.admin.createUser(email = "${uid("u-")}@ontrack.local")
        val userOnB = Ontrack(
            connector = DefaultConnector(
                url = ACCProperties.HA.B.url,
                token = ACCProperties.getOrCreateToken(username = user.email),
            )
        )
        ontrackA.settings.security.with(update = { it.withGrantProjectViewToAll(true) }) {
            assertTrue(userOnB.sees(project.name), "Project visible to all through B")

            ontrackA.settings.security.set(
                ontrackA.settings.security.get().withGrantProjectViewToAll(false)
            )

            assertFalse(userOnB.sees(project.name), "Project no longer visible to all through B")
        }
    }

    @Test
    fun `a GitHub ingestion webhook secret rotated through A is enforced by B on the next delivery`() {
        // The secret of the hook is also the API token it acts with: two tokens of the administrator
        val oldName = uid("hook-old-")
        val newName = uid("hook-new-")
        val oldSecret = ontrackA.generateToken(oldName)
        val newSecret = ontrackA.generateToken(newName)
        try {
            ontrackA.settings.gitHubIngestion.with(update = { it.withSecret(oldSecret) }) {
                assertEquals(200, pingB(signedWith = oldSecret), "Delivery signed with the secret accepted by B")

                ontrackA.settings.gitHubIngestion.set(
                    ontrackA.settings.gitHubIngestion.get().withSecret(newSecret)
                )

                assertEquals(400, pingB(signedWith = oldSecret), "Delivery signed with the old secret rejected by B")
                assertEquals(200, pingB(signedWith = newSecret), "Delivery signed with the new secret accepted by B")
            }
        } finally {
            ontrackA.revokeToken(oldName)
            ontrackA.revokeToken(newName)
        }
    }

    /**
     * Whether this client sees the project: a project which is not granted is refused.
     */
    private fun Ontrack.sees(name: String): Boolean =
        try {
            findProjectByName(name) != null
        } catch (e: GraphQLClientException) {
            if (e.message?.contains("Project access not granted") == true) false else throw e
        }

    private fun pingB(signedWith: String): Int = pingIngestionHook(ACCProperties.HA.B.url, signedWith)
}
