package net.nemerosa.ontrack.repository.migration

import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.structure.EntityStore
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import tools.jackson.databind.JsonNode
import java.sql.DriverManager
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The migration moving the entity data into the entity store (#1925) turns every `ENTITY_DATA` row
 * into an `ENTITY_STORE` record - its name as the store, [EntityStore.DEFAULT_NAME] as the record
 * name, its JSON unchanged - and the table is then dropped (#1933).
 *
 * Each test runs against a database of its own, created on the IT Postgres server, migrated up to
 * [LAST_WITH_ENTITY_DATA], seeded, migrated to the latest version, then dropped.
 */
class EntityDataToEntityStoreMigrationIT {

    private val url = System.getProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/ontrack")
    private val username = System.getProperty("spring.datasource.username", "ontrack")
    private val password = System.getProperty("spring.datasource.password", "ontrack")

    @Test
    fun `Entity data rows are moved to the entity store with their JSON unchanged`() {
        withDatabase { db ->
            val branch = db.createBranch()
            val project = db.projectOf(branch)
            val branchJson = """{"configurations":[{"sourceProject":"p","sourcePromotion":"GOLD"}]}"""
            val projectJson = "\"some-catalog-key\""
            db.insertEntityData("BRANCH", branch, "net.nemerosa.ontrack.Branch", branchJson)
            db.insertEntityData("PROJECT", project, "net.nemerosa.ontrack.Project", projectJson)

            db.migrateToLatest()

            assertEquals(branchJson.parseAsJson(), db.findStored("BRANCH", branch, "net.nemerosa.ontrack.Branch"))
            assertEquals(projectJson.parseAsJson(), db.findStored("PROJECT", project, "net.nemerosa.ontrack.Project"))
            assertFalse(db.entityDataExists(), "ENTITY_DATA is dropped")
        }
    }

    @Test
    fun `The most recent row wins when an entity has the same name twice`() {
        withDatabase { db ->
            val branch = db.createBranch()
            val key = "net.nemerosa.ontrack.Duplicate"
            db.insertEntityData("BRANCH", branch, key, """{"version":1}""")
            db.insertEntityData("BRANCH", branch, key, """{"version":2}""")

            db.migrateToLatest()

            assertEquals("""{"version":2}""".parseAsJson(), db.findStored("BRANCH", branch, key))
            assertFalse(db.entityDataExists(), "ENTITY_DATA is dropped")
        }
    }

    @Test
    fun `The creation order of the rows is kept`() {
        withDatabase { db ->
            // The second branch gets its row first
            val b1 = db.createBranch()
            val b2 = db.createBranch()
            val key = "net.nemerosa.ontrack.Order"
            db.insertEntityData("BRANCH", b2, key, """{"branch":2}""")
            db.insertEntityData("BRANCH", b1, key, """{"branch":1}""")

            db.migrateToLatest()

            assertEquals(
                listOf(b2, b1),
                db.jdbc.queryForList(
                    "SELECT BRANCH FROM ENTITY_STORE WHERE STORE = :store ORDER BY ID",
                    mapOf("store" to key),
                    Int::class.java
                )
            )
            assertFalse(db.entityDataExists(), "ENTITY_DATA is dropped")
        }
    }

    @Test
    fun `A row without a value is not moved`() {
        withDatabase { db ->
            val branch = db.createBranch()
            val key = "net.nemerosa.ontrack.Empty"
            db.jdbc.update(
                "INSERT INTO ENTITY_DATA (BRANCH, NAME, JSON_VALUE) VALUES (:entity, :name, NULL)",
                mapOf("entity" to branch, "name" to key)
            )

            db.migrateToLatest()

            assertNull(db.findStored("BRANCH", branch, key))
            assertFalse(db.entityDataExists(), "ENTITY_DATA is dropped")
        }
    }

    /**
     * A throwaway database, migrated up to [LAST_WITH_ENTITY_DATA].
     */
    private class Database(
        private val flyway: Flyway,
        val jdbc: NamedParameterJdbcTemplate,
    ) {

        private var count = 0

        fun migrateToLatest() {
            flyway.migrate()
            assertTrue(flyway.info().pending().isEmpty(), "Every migration has run")
        }

        fun createBranch(): Int {
            val index = ++count
            val project = jdbc.queryForObject(
                "INSERT INTO PROJECTS (NAME) VALUES (:name) RETURNING ID",
                mapOf("name" to "P$index"),
                Int::class.java
            )!!
            return jdbc.queryForObject(
                "INSERT INTO BRANCHES (PROJECTID, NAME) VALUES (:project, :name) RETURNING ID",
                mapOf("project" to project, "name" to "B$index"),
                Int::class.java
            )!!
        }

        fun projectOf(branch: Int): Int =
            jdbc.queryForObject(
                "SELECT PROJECTID FROM BRANCHES WHERE ID = :id",
                mapOf("id" to branch),
                Int::class.java
            )!!

        fun insertEntityData(column: String, id: Int, name: String, json: String) {
            jdbc.update(
                "INSERT INTO ENTITY_DATA ($column, NAME, JSON_VALUE) VALUES (:entity, :name, CAST(:json AS JSONB))",
                mapOf("entity" to id, "name" to name, "json" to json)
            )
        }

        fun findStored(column: String, id: Int, store: String): JsonNode? =
            jdbc.queryForList(
                "SELECT DATA FROM ENTITY_STORE WHERE $column = :id AND STORE = :store AND NAME = :name",
                mapOf("id" to id, "store" to store, "name" to EntityStore.DEFAULT_NAME),
                String::class.java
            ).firstOrNull()?.parseAsJson()

        fun entityDataExists(): Boolean =
            jdbc.queryForObject(
                "SELECT TO_REGCLASS('entity_data') IS NOT NULL",
                emptyMap<String, Any>(),
                Boolean::class.java
            )!!
    }

    private fun withDatabase(code: (db: Database) -> Unit) {
        val database = "ontrack_entity_data_${UUID.randomUUID().toString().replace("-", "")}"
        val databaseUrl = url.replace(Regex("/[^/?]+(\\?|$)"), "/$database$1")
        admin("CREATE DATABASE $database")
        try {
            val flyway = Flyway.configure()
                .dataSource(databaseUrl, username, password)
                // As configured by FlywayConfiguration
                .table("schema_version")
                .load()
            Flyway.configure()
                .configuration(flyway.configuration)
                .target(LAST_WITH_ENTITY_DATA)
                .load()
                .migrate()
            code(
                Database(
                    flyway = flyway,
                    jdbc = NamedParameterJdbcTemplate(DriverManagerDataSource(databaseUrl, username, password)),
                )
            )
        } finally {
            admin("DROP DATABASE IF EXISTS $database WITH (FORCE)")
        }
    }

    private fun admin(sql: String) {
        DriverManager.getConnection(url, username, password).use { connection ->
            connection.createStatement().use { it.execute(sql) }
        }
    }

    companion object {
        /**
         * Last Flyway version before the entity data moved to the entity store,
         * `V92__1924_pure_git_removal.sql`.
         */
        private const val LAST_WITH_ENTITY_DATA = "92"
    }
}
