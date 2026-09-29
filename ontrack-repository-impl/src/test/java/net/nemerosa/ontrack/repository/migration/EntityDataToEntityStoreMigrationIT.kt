package net.nemerosa.ontrack.repository.migration

import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.structure.EntityStore
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.repository.AbstractRepositoryTestSupport
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The migration moving the entity data into the entity store (#1925) turns every `ENTITY_DATA` row
 * into an `ENTITY_STORE` record - its name as the store, [EntityStore.DEFAULT_NAME] as the record
 * name, its JSON unchanged - and empties `ENTITY_DATA`.
 */
class EntityDataToEntityStoreMigrationIT : AbstractRepositoryTestSupport() {

    private fun migrate() {
        ResourceDatabasePopulator(
            ClassPathResource("db/migration/V93__1925_entity_data_to_entity_store.sql")
        ).execute(dataSource)
    }

    @Test
    fun `Entity data rows are moved to the entity store with their JSON unchanged`() {
        val branch = do_create_branch()
        val project = branch.project
        val branchKey = uid("net.nemerosa.ontrack.Branch")
        val projectKey = uid("net.nemerosa.ontrack.Project")
        val branchJson = """{"configurations":[{"sourceProject":"p","sourcePromotion":"GOLD"}]}"""
        val projectJson = "\"some-catalog-key\""
        insertEntityData(branch, branchKey, branchJson)
        insertEntityData(project, projectKey, projectJson)

        migrate()

        assertEquals(branchJson.parseAsJson(), findStored("BRANCH", branch.id(), branchKey))
        assertEquals(projectJson.parseAsJson(), findStored("PROJECT", project.id(), projectKey))
        assertEquals(0, countEntityData(branchKey))
        assertEquals(0, countEntityData(projectKey))
    }

    @Test
    fun `The most recent row wins when an entity has the same name twice`() {
        val branch = do_create_branch()
        val key = uid("net.nemerosa.ontrack.Duplicate")
        insertEntityData(branch, key, """{"version":1}""")
        insertEntityData(branch, key, """{"version":2}""")

        migrate()

        assertEquals("""{"version":2}""".parseAsJson(), findStored("BRANCH", branch.id(), key))
        assertEquals(0, countEntityData(key))
    }

    @Test
    fun `The creation order of the rows is kept`() {
        // The second branch gets its row first
        val b1 = do_create_branch()
        val b2 = do_create_branch()
        val key = uid("net.nemerosa.ontrack.Order")
        insertEntityData(b2, key, """{"branch":2}""")
        insertEntityData(b1, key, """{"branch":1}""")

        migrate()

        assertEquals(
            listOf(b2.id(), b1.id()),
            namedParameterJdbcTemplate.queryForList(
                "SELECT BRANCH FROM ENTITY_STORE WHERE STORE = :store ORDER BY ID",
                mapOf("store" to key),
                Int::class.java
            )
        )
    }

    @Test
    fun `A row without a value is not moved`() {
        val branch = do_create_branch()
        val key = uid("net.nemerosa.ontrack.Empty")
        namedParameterJdbcTemplate.update(
            "INSERT INTO ENTITY_DATA (BRANCH, NAME, JSON_VALUE) VALUES (:entity, :name, NULL)",
            mapOf("entity" to branch.id(), "name" to key)
        )

        migrate()

        assertNull(findStored("BRANCH", branch.id(), key))
        assertEquals(0, countEntityData(key))
    }

    private fun insertEntityData(entity: ProjectEntity, name: String, json: String) {
        namedParameterJdbcTemplate.update(
            "INSERT INTO ENTITY_DATA (${entity.projectEntityType.name}, NAME, JSON_VALUE) VALUES (:entity, :name, CAST(:json AS JSONB))",
            mapOf("entity" to entity.id(), "name" to name, "json" to json)
        )
    }

    private fun findStored(column: String, id: Int, store: String): JsonNode? =
        namedParameterJdbcTemplate.queryForList(
            "SELECT DATA FROM ENTITY_STORE WHERE $column = :id AND STORE = :store AND NAME = :name",
            mapOf("id" to id, "store" to store, "name" to EntityStore.DEFAULT_NAME),
            String::class.java
        ).firstOrNull()?.parseAsJson()

    private fun countEntityData(name: String): Int =
        namedParameterJdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ENTITY_DATA WHERE NAME = :name",
            mapOf("name" to name),
            Int::class.java
        )!!

}
