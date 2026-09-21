package net.nemerosa.ontrack.repository

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.Ack
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.PropertySearchArguments
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import net.nemerosa.ontrack.repository.support.createSQL
import org.apache.commons.lang3.StringUtils
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Repository
import java.lang.String.format
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.*
import java.util.function.BiFunction
import java.util.function.Predicate
import javax.sql.DataSource

@Repository
class PropertyJdbcRepository(
    dataSource: DataSource
) : AbstractJdbcRepository(dataSource), PropertyRepository {

    override fun hasProperty(typeName: String, entityType: ProjectEntityType, entityId: ID): Boolean {
        return namedParameterJdbcTemplate!!.queryForList(
            """
                    SELECT ID FROM PROPERTIES WHERE TYPE = :type AND ${entityType.name} = :entityId
                """,
            params("type", typeName).addValue("entityId", entityId.value),
            Int::class.java
        ).filterNotNull().isNotEmpty()
    }

    @Cacheable(cacheNames = ["properties"], key = "#typeName + #entityType.name() + #entityId.value")
    override fun loadProperty(typeName: String, entityType: ProjectEntityType, entityId: ID): TProperty? {
        return getFirstItem(
            String.format(
                "SELECT * FROM PROPERTIES WHERE TYPE = :type AND %s = :entityId",
                entityType.name
            ),
            params("type", typeName).addValue("entityId", entityId.value)
        ) { rs, rowNum -> toProperty(rs) }
    }

    @CacheEvict(cacheNames = ["properties"], key = "#typeName + #entityType.name() + #entityId.value")
    override fun saveProperty(typeName: String, entityType: ProjectEntityType, entityId: ID, data: JsonNode) {
        val params = params("type", typeName).addValue("entityId", entityId.value)
        // Any previous value?
        val propertyId = getFirstItem(
            String.format(
                "SELECT ID FROM PROPERTIES WHERE TYPE = :type AND %s = :entityId",
                entityType.name
            ),
            params,
            Int::class.java
        )
        // Data parameters
        params.addValue("json", writeJson(data))
        // Update
        if (propertyId != null) {
            namedParameterJdbcTemplate!!.update(
                "UPDATE PROPERTIES SET JSON = CAST(:json AS JSONB) WHERE ID = :id",
                params.addValue("id", propertyId)
            )
        } else {
            namedParameterJdbcTemplate!!.update(
                String.format(
                    "INSERT INTO PROPERTIES(TYPE, %s, JSON) " + "VALUES(:type, :entityId, CAST(:json AS JSONB))",
                    entityType.name
                ),
                params
            )
        }// Creation
    }

    @CacheEvict(cacheNames = ["properties"], key = "#typeName + #entityType.name() + #entityId.value")
    override fun deleteProperty(typeName: String, entityType: ProjectEntityType, entityId: ID): Ack {
        return Ack.one(
            namedParameterJdbcTemplate!!.update(
                String.format(
                    "DELETE FROM PROPERTIES WHERE TYPE = :type AND %s = :entityId",
                    entityType.name
                ),
                params("type", typeName).addValue("entityId", entityId.value)
            )
        )
    }

    override fun searchByProperty(
        typeName: String,
        entityLoader: BiFunction<ProjectEntityType, ID, ProjectEntity>,
        predicate: Predicate<TProperty>
    ): Collection<ProjectEntity> {
        return namedParameterJdbcTemplate!!.execute<Collection<ProjectEntity>>(
            "SELECT * FROM PROPERTIES WHERE TYPE = :type ORDER BY ID DESC",
            params("type", typeName)
        ) { ps: PreparedStatement ->
            val entities = ArrayList<ProjectEntity>()
            val rs = ps.executeQuery()
            while (rs.next()) {
                val t = toProperty(rs)
                if (predicate.test(t)) {
                    entities.add(entityLoader.apply(t.entityType, t.entityId))
                }
            }
            entities
        }!!
    }

    override fun forEachEntityWithProperty(typeName: String, consumer: (TProperty) -> Unit) {
        namedParameterJdbcTemplate!!.query(
            "SELECT * FROM PROPERTIES WHERE TYPE = :type ORDER BY ID DESC",
            params("type", typeName)
        ) { rs ->
            val property = toProperty(rs)
            consumer(property)
        }
    }

    override fun findBuildByBranchAndSearchkey(
        branchId: ID,
        typeName: String,
        searchArguments: PropertySearchArguments?
    ): ID? {
        val tables = mutableListOf(
            "SELECT b.ID " +
                    "FROM PROPERTIES pp " +
                    "INNER JOIN BUILDS b ON pp.BUILD = b.ID "
        )
        val criteria = mutableListOf(
            "pp.TYPE = :type ",
            "b.BRANCHID = :branchId"
        )
        val params = mutableMapOf<String, Any?>()
        params["type"] = typeName
        params["branchId"] = branchId.value
        if (searchArguments != null) {
            prepareQueryForPropertyValue(
                searchArguments,
                tables,
                criteria,
                params
            )
        }
        val sql = createSQL(tables, criteria)
        val id = getFirstItem(
            sql,
            params,
            Int::class.java
        )
        return if (id != null) {
            ID.of(id)
        } else {
            null
        }
    }

    override fun findByEntityTypeAndSearchArguments(
        entityType: ProjectEntityType,
        typeName: String,
        searchArguments: PropertySearchArguments?
    ): List<ID> {
        val entityColumn: String = entityType.displayName
        val tables = mutableListOf("SELECT pp.$entityColumn FROM PROPERTIES pp ")
        val criteria = mutableListOf("pp.TYPE = :type", "pp.$entityColumn IS NOT NULL ")
        val params = mutableMapOf<String, Any?>()
        params["type"] = typeName
        if (searchArguments != null) {
            prepareQueryForPropertyValue(
                searchArguments,
                tables,
                criteria,
                params
            )
        }
        val sql = createSQL(tables, criteria)
        @Suppress("SqlSourceToSinkFlow")
        return namedParameterJdbcTemplate!!.queryForList(
            sql,
            params,
            Int::class.java
        ).filterNotNull().map { id -> ID.of(id) }
    }

    @Throws(SQLException::class)
    private fun toProperty(rs: ResultSet): TProperty {
        val id = rs.getInt("id")
        val typeName = rs.getString("type")
        // Detects the entity
        var entityType: ProjectEntityType? = null
        var entityId: ID? = null
        for (candidate in ProjectEntityType.values()) {
            val candidateId = rs.getInt(candidate.name)
            if (!rs.wasNull()) {
                entityType = candidate
                entityId = ID.of(candidateId)
            }
        }
        // Sanity check
        if (entityType == null || !ID.isDefined(entityId)) {
            throw IllegalStateException(
                String.format(
                    "Could not find any entity for property %s with id = %d",
                    typeName,
                    id
                )
            )
        }
        // OK
        return TProperty(
            typeName,
            entityType,
            entityId!!,
            readJson(rs, "json")
        )
    }

    companion object {
        @JvmStatic
        fun prepareQueryForPropertyValue(
            searchArguments: PropertySearchArguments,
            tables: MutableList<String>,
            criteria: MutableList<String>,
            params: MutableMap<String, Any?>,
        ) {
            if (StringUtils.isNotBlank(searchArguments.jsonContext)) {
                tables += format(" LEFT JOIN %s on true", searchArguments.jsonContext)
            }
            searchArguments.jsonCriteria
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    criteria += it
                    if (searchArguments.criteriaParams != null) {
                        for ((key, value) in searchArguments.criteriaParams!!) {
                            params[key] = value
                        }
                    }
                }
        }
    }
}
