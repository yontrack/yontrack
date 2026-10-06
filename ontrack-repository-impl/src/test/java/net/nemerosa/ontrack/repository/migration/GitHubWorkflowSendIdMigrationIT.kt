package net.nemerosa.ontrack.repository.migration

import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.repository.AbstractRepositoryTestSupport
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The GitHub configurations stored before `workflowSendId` defaulted to `false` (#1952) keep sending the
 * workflow ID: the migration writes `true` where the key is missing, and nothing else.
 */
class GitHubWorkflowSendIdMigrationIT : AbstractRepositoryTestSupport() {

    private val gitHubConfiguration = "net.nemerosa.ontrack.extension.github.model.GitHubEngineConfiguration"
    private val gitLabConfiguration = "net.nemerosa.ontrack.extension.gitlab.model.GitLabConfiguration"

    @Test
    fun `GitHub configurations without the key keep sending the workflow ID, and nothing else changes`() {
        val missing = uid("C")
        val disabled = uid("C")
        val enabled = uid("C")
        val other = uid("C")
        insertConfiguration(gitHubConfiguration, missing, """{"name":"$missing"}""")
        insertConfiguration(gitHubConfiguration, disabled, """{"name":"$disabled","workflowSendId":false}""")
        insertConfiguration(gitHubConfiguration, enabled, """{"name":"$enabled","workflowSendId":true}""")
        insertConfiguration(gitLabConfiguration, other, """{"name":"$other"}""")

        ResourceDatabasePopulator(
            ClassPathResource("db/migration/V99__1952_github_workflow_send_id.sql")
        ).execute(dataSource)

        val migrated = loadContent(gitHubConfiguration, missing)
        assertTrue(migrated.path("workflowSendId").asBoolean(false), "Missing key is set to true")
        assertEquals(missing, migrated.path("name").asText(), "Rest of the content is kept")

        assertFalse(
            loadContent(gitHubConfiguration, disabled).path("workflowSendId").asBoolean(true),
            "Explicit false is kept"
        )
        assertTrue(
            loadContent(gitHubConfiguration, enabled).path("workflowSendId").asBoolean(false),
            "Explicit true is kept"
        )
        assertFalse(
            loadContent(gitLabConfiguration, other).has("workflowSendId"),
            "Other configuration types are not touched"
        )
    }

    private fun insertConfiguration(type: String, name: String, content: String) {
        namedParameterJdbcTemplate.update(
            "INSERT INTO CONFIGURATIONS (TYPE, NAME, CONTENT) VALUES (:type, :name, CAST(:content AS JSONB))",
            mapOf("type" to type, "name" to name, "content" to content)
        )
    }

    private fun loadContent(type: String, name: String) =
        namedParameterJdbcTemplate.queryForObject(
            "SELECT CONTENT::TEXT FROM CONFIGURATIONS WHERE TYPE = :type AND NAME = :name",
            mapOf("type" to type, "name" to name),
            String::class.java
        )!!.parseAsJson()

}
