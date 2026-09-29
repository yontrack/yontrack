package net.nemerosa.ontrack.repository.migration

import net.nemerosa.ontrack.repository.AbstractRepositoryTestSupport
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import kotlin.test.assertEquals

/**
 * The migration removing the pure-Git support (#1924) deletes the *Git configuration* property of the
 * projects and the Git configurations - and nothing else.
 */
class PureGitRemovalMigrationIT : AbstractRepositoryTestSupport() {

    private val gitProperty = "net.nemerosa.ontrack.extension.git.property.GitProjectConfigurationPropertyType"
    private val gitHubProperty = "net.nemerosa.ontrack.extension.github.property.GitHubProjectConfigurationPropertyType"

    private val gitConfiguration = "net.nemerosa.ontrack.extension.git.model.BasicGitConfiguration"
    private val gitHubConfiguration = "net.nemerosa.ontrack.extension.github.model.GitHubEngineConfiguration"

    @Test
    fun `Git configuration properties and Git configurations are removed, and nothing else`() {
        // A project with the Git configuration property, another with the GitHub one
        val gitProject = do_create_project()
        insertProperty(gitProject.id(), gitProperty)
        val gitHubProject = do_create_project()
        insertProperty(gitHubProject.id(), gitHubProperty)

        // A Git configuration and a GitHub one
        val name = uid("C")
        insertConfiguration(gitConfiguration, name)
        insertConfiguration(gitHubConfiguration, name)

        // Running the migration
        ResourceDatabasePopulator(
            ClassPathResource("db/migration/V92__1924_pure_git_removal.sql")
        ).execute(dataSource)

        assertEquals(0, countProperties(gitProject.id(), gitProperty), "Git configuration property is gone")
        assertEquals(1, countProperties(gitHubProject.id(), gitHubProperty), "GitHub configuration property is kept")

        assertEquals(0, countConfigurations(gitConfiguration, name), "Git configuration is gone")
        assertEquals(1, countConfigurations(gitHubConfiguration, name), "GitHub configuration is kept")
    }

    private fun insertProperty(project: Int, type: String) {
        namedParameterJdbcTemplate.update(
            "INSERT INTO PROPERTIES (TYPE, PROJECT, JSON) VALUES (:type, :project, CAST(:json AS JSONB))",
            mapOf("type" to type, "project" to project, "json" to """{"configuration":"C"}""")
        )
    }

    private fun countProperties(project: Int, type: String): Int =
        namedParameterJdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM PROPERTIES WHERE TYPE = :type AND PROJECT = :project",
            mapOf("type" to type, "project" to project),
            Int::class.java
        )!!

    private fun insertConfiguration(type: String, name: String) {
        namedParameterJdbcTemplate.update(
            "INSERT INTO CONFIGURATIONS (TYPE, NAME, CONTENT) VALUES (:type, :name, CAST(:content AS JSONB))",
            mapOf("type" to type, "name" to name, "content" to """{"name":"$name"}""")
        )
    }

    private fun countConfigurations(type: String, name: String): Int =
        namedParameterJdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM CONFIGURATIONS WHERE TYPE = :type AND NAME = :name",
            mapOf("type" to type, "name" to name),
            Int::class.java
        )!!

}
