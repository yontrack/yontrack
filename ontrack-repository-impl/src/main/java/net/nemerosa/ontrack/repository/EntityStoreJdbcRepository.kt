package net.nemerosa.ontrack.repository

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.json.parseInto
import net.nemerosa.ontrack.model.structure.EntityStore
import net.nemerosa.ontrack.model.structure.EntityStoreFilter
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import javax.sql.DataSource
import kotlin.reflect.KClass

@Repository
class EntityStoreJdbcRepository(dataSource: DataSource) : AbstractJdbcRepository(dataSource), EntityStore {

    override fun store(entity: ProjectEntity, store: String, name: String, data: Any) {
        namedParameterJdbcTemplate!!.update(
            """
                INSERT INTO ENTITY_STORE(${entity.projectEntityType.name}, STORE, NAME, DATA)
                VALUES(:entityId, :store, :name, CAST(:data AS JSONB))
                ON CONFLICT (${entity.projectEntityType.name}, STORE, NAME)
                DO UPDATE SET DATA = CAST(:data AS JSONB)
            """,
            mapOf(
                "entityId" to entity.id(),
                "store" to store,
                "name" to name,
                "data" to writeJson(data),
            )
        )
    }

    override fun <T : Any> findByName(entity: ProjectEntity, store: String, name: String, type: KClass<T>): T? {
        return getFirstItem(
            """
                SELECT DATA
                FROM ENTITY_STORE
                WHERE ${entity.projectEntityType.name} = :entityId
                AND STORE = :store
                AND NAME = :name
            """,
            mapOf(
                "entityId" to entity.id(),
                "store" to store,
                "name" to name,
            )
        ) { rs, _ -> readJson(rs, "DATA") }?.parseInto(type)
    }

    override fun deleteByName(entity: ProjectEntity, store: String, name: String) {
        namedParameterJdbcTemplate!!.update(
            """
                DELETE FROM ENTITY_STORE
                WHERE ${entity.projectEntityType.name} = :entityId
                AND STORE = :store
                AND NAME = :name
            """,
            mapOf(
                "entityId" to entity.id(),
                "store" to store,
                "name" to name,
            )
        )
    }

    override fun deleteByStore(entity: ProjectEntity, store: String) {
        namedParameterJdbcTemplate!!.update(
            """
                DELETE FROM ENTITY_STORE
                WHERE ${entity.projectEntityType.name} = :entityId
                AND STORE = :store
            """,
            mapOf(
                "entityId" to entity.id(),
                "store" to store,
            )
        )
    }

    override fun deleteByFilter(entity: ProjectEntity, store: String, filter: EntityStoreFilter) {
        val context = StringBuilder()
        val criteria = StringBuilder()
        val params = mutableMapOf<String, Any?>()
        buildCriteria(entity, store, filter, context, criteria, params)
        // Runs the query
        @Suppress("SqlSourceToSinkFlow")
        namedParameterJdbcTemplate!!.update(
            "DELETE FROM ENTITY_STORE $context WHERE $criteria",
            params
        )
    }

    override fun getCountByFilter(entity: ProjectEntity, store: String, filter: EntityStoreFilter): Int {
        val context = StringBuilder()
        val criteria = StringBuilder()
        val params = mutableMapOf<String, Any?>()
        buildCriteria(entity, store, filter, context, criteria, params)
        // Runs the query
        @Suppress("SqlSourceToSinkFlow")
        return namedParameterJdbcTemplate!!.queryForObject(
            "SELECT COUNT(ID) FROM ENTITY_STORE $context WHERE $criteria",
            params,
            Int::class.java
        ) ?: 0
    }

    override fun <T : Any> getByFilter(
        entity: ProjectEntity,
        store: String,
        offset: Int,
        size: Int,
        filter: EntityStoreFilter,
        type: KClass<T>
    ): List<T> {
        val context = StringBuilder()
        val criteria = StringBuilder()
        val params = mutableMapOf<String, Any?>()
        buildCriteria(entity, store, filter, context, criteria, params)
        params["offset"] = offset
        params["size"] = size
        // Runs the query
        @Suppress("SqlSourceToSinkFlow")
        return namedParameterJdbcTemplate!!.query(
            "SELECT DATA FROM ENTITY_STORE $context WHERE $criteria ORDER BY ID DESC OFFSET :offset LIMIT :size",
            params
        ) { rs: ResultSet, _ ->
            readJson(rs, "DATA").parseInto(type)
        }
    }

    override fun <T : Any> forEachByFilter(
        entity: ProjectEntity,
        store: String,
        type: KClass<T>,
        filter: EntityStoreFilter,
        code: (T) -> Unit
    ) {
        val context = StringBuilder()
        val criteria = StringBuilder()
        val params = mutableMapOf<String, Any?>()
        buildCriteria(entity, store, filter, context, criteria, params)
        // Runs the query
        namedParameterJdbcTemplate!!.query(
            @Suppress("SqlSourceToSinkFlow")
            """
                SELECT * 
                FROM ENTITY_STORE 
                $context
                WHERE $criteria 
                ORDER BY ID DESC
            """,
            params
        ) { rs: ResultSet, _: Int ->
            val record = readJson(rs, "DATA").parseInto(type)
            code(record)
        }
    }

    override fun deleteByStoreForAllEntities(store: String) {
        namedParameterJdbcTemplate!!.update(
            """
                DELETE FROM ENTITY_STORE
                 WHERE STORE = :store
            """,
            mapOf("store" to store)
        )
    }

    private fun buildCriteria(
        entity: ProjectEntity,
        store: String,
        filter: EntityStoreFilter,
        context: StringBuilder,
        criteria: StringBuilder,
        params: MutableMap<String, Any?>
    ) {
        val criteriaList = mutableListOf<String>()
        // Entity
        criteriaList += "${entity.projectEntityType.name} = :entityId"
        params["entityId"] = entity.id()
        // Store
        criteriaList += "STORE = :store"
        params["store"] = store
        // JSON context
        if (!filter.jsonContext.isNullOrBlank()) {
            context.append(filter.jsonContext)
        }
        // JSON filter
        val jsonFilter = filter.jsonFilter
        if (!jsonFilter.isNullOrBlank()) {
            criteriaList += jsonFilter
            filter.jsonFilterCriterias?.forEach { (name, value) ->
                params[name] = value
            }
        }
        // OK
        criteria.append(criteriaList.joinToString(" AND ") { "($it)" })
    }

    override fun migrateFromEntityDataStore(
        category: String,
        migration: (name: String, data: JsonNode) -> Pair<String, JsonNode>
    ) {
        namedParameterJdbcTemplate!!.query(
            """
                SELECT *
                FROM ENTITY_DATA_STORE
                WHERE CATEGORY = :category
                ORDER BY ID
            """,
            mapOf(
                "category" to category,
            )
        ) { rs: ResultSet, _: Int ->
            val name = rs.getString("name")
            val data = readJson(rs, "json")
            val (newName, newData) = migration(name, data)
            namedParameterJdbcTemplate!!.update(
                """
                    INSERT INTO ENTITY_STORE(PROJECT, BRANCH, PROMOTION_LEVEL, VALIDATION_STAMP, BUILD, PROMOTION_RUN, VALIDATION_RUN, STORE, NAME, DATA)
                    VALUES(:project, :branch, :promotion_level, :validation_stamp, :build, :promotion_run, :validation_run, :store, :name, CAST(:data AS JSONB))
                """,
                mapOf(
                    "project" to rs.getInt("project").takeIf { it != 0 },
                    "branch" to rs.getInt("branch").takeIf { it != 0 },
                    "promotion_level" to rs.getInt("promotion_level").takeIf { it != 0 },
                    "validation_stamp" to rs.getInt("validation_stamp").takeIf { it != 0 },
                    "build" to rs.getInt("build").takeIf { it != 0 },
                    "promotion_run" to rs.getInt("promotion_run").takeIf { it != 0 },
                    "validation_run" to rs.getInt("validation_run").takeIf { it != 0 },
                    "store" to category,
                    "name" to newName,
                    "data" to writeJson(newData),
                )
            )
        }
        namedParameterJdbcTemplate!!.update(
            """
                DELETE FROM ENTITY_DATA_STORE
                WHERE CATEGORY = :category
            """,
            mapOf(
                "category" to category,
            )
        )
    }
}