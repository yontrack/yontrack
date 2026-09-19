package net.nemerosa.ontrack.extension.gitlab.service

import net.nemerosa.ontrack.extension.gitlab.AbstractGitLabTestSupport
import net.nemerosa.ontrack.extension.gitlab.model.GitLabConfiguration
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.asJsonString
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.security.EncryptionService
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A GitLab configuration stored before the token rework carried `user` and `password`. The migration moves
 * the (already encrypted) password onto `token` and drops the user, so that an upgrade does not silently
 * break a working instance.
 */
class GitLabConfigurationTokenMigrationIT : AbstractGitLabTestSupport() {

    @Autowired
    private lateinit var migration: GitLabConfigurationTokenMigration

    @Autowired
    private lateinit var encryptionService: EncryptionService

    @Test
    fun `A configuration stored with a user and a password becomes a token`() {
        val name = uid("GL")
        withDisabledConfigurationTest {
            asAdmin {
                storeLegacyConfiguration(
                    name = name,
                    url = "https://gitlab.example.com",
                    user = "some-user",
                    password = encryptionService.encrypt("the-token"),
                    ignoreSslCertificate = true,
                )
                // Running it more than once must not undo it
                repeat(3) {
                    migration.start()
                    val config = gitConfigurationService.getConfiguration(name)
                    assertEquals("https://gitlab.example.com", config.url)
                    assertEquals("the-token", config.token)
                    assertTrue(config.ignoreSslCertificate, "The SSL setting is kept")
                }
                // The user is gone from the stored JSON
                val raw = rawConfiguration(name)
                assertFalse(raw.has("user"), "The user has been dropped")
                assertFalse(raw.has("password"), "The password has been dropped")
                assertTrue(raw.has("token"), "The token is stored")
                assertTrue(
                    raw.path("token").asText() != "the-token",
                    "The token is still encrypted at rest",
                )
            }
        }
    }

    @Test
    fun `A configuration already using a token is left alone`() {
        val name = uid("GL")
        withDisabledConfigurationTest {
            asAdmin {
                gitConfigurationService.newConfiguration(
                    GitLabConfiguration(
                        name = name,
                        url = "https://gitlab.example.com",
                        token = "the-token",
                    )
                )
                val before = rawConfiguration(name)
                migration.start()
                assertEquals(before, rawConfiguration(name))
                assertEquals("the-token", gitConfigurationService.getConfiguration(name).token)
            }
        }
    }

    @Test
    fun `A configuration stored without any password keeps working`() {
        val name = uid("GL")
        withDisabledConfigurationTest {
            asAdmin {
                storeLegacyConfiguration(
                    name = name,
                    url = "https://gitlab.example.com",
                    user = "some-user",
                    password = null,
                    ignoreSslCertificate = false,
                )
                migration.start()
                val config = gitConfigurationService.getConfiguration(name)
                assertEquals("https://gitlab.example.com", config.url)
                assertEquals(null, config.token)
            }
        }
    }

    private fun storeLegacyConfiguration(
        name: String,
        url: String,
        user: String?,
        password: String?,
        ignoreSslCertificate: Boolean,
    ) {
        namedParameterJdbcTemplate!!.update(
            "INSERT INTO CONFIGURATIONS(TYPE, NAME, CONTENT) VALUES (:type, :name, CAST(:content AS JSONB))",
            mapOf(
                "name" to name,
                "type" to GitLabConfiguration::class.java.name,
                "content" to mapOf(
                    "name" to name,
                    "url" to url,
                    "user" to user,
                    "password" to password,
                    "ignoreSslCertificate" to ignoreSslCertificate,
                ).asJson().asJsonString()
            )
        )
    }

    private fun rawConfiguration(name: String) =
        namedParameterJdbcTemplate!!.queryForObject(
            "SELECT CONTENT::TEXT FROM CONFIGURATIONS WHERE TYPE = :type AND NAME = :name",
            mapOf(
                "type" to GitLabConfiguration::class.java.name,
                "name" to name,
            ),
            String::class.java
        )!!.parseAsJson()

}
