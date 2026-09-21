package net.nemerosa.ontrack.repository

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.Signature
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import net.nemerosa.ontrack.repository.support.store.*
import org.apache.commons.lang3.StringUtils
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.SQLException
import java.time.LocalDateTime
import javax.sql.DataSource

@Repository
class EntityDataStoreJdbcRepository(
    dataSource: DataSource,
) : AbstractJdbcRepository(dataSource), EntityDataStore {

    override fun add(
        entity: ProjectEntity,
        category: String,
        name: String,
        signature: Signature,
        groupName: String?,
        data: JsonNode,
    ): EntityDataStoreRecord {
        val id = dbCreate(
            String.format(
                "INSERT INTO ENTITY_DATA_STORE(%s, CATEGORY, NAME, GROUPID, JSON, CREATION, CREATOR) VALUES (:entityId, :category, :name, :groupId, CAST(:json AS JSONB), :creation, :creator)",
                entity.projectEntityType.name
            ),
            params("entityId", entity.id())
                .addValue("category", category)
                .addValue("name", name)
                .addValue("groupId", groupName)
                .addValue("json", writeJson(data))
                .addValue("creation", dateTimeForDB(signature.time))
                .addValue("creator", signature.user.name)
        )
        // Audit
        audit(EntityDataStoreRecordAuditType.CREATED, id, signature)
        // OK
        return EntityDataStoreRecord(
            id,
            entity,
            category,
            name,
            groupName,
            signature,
            data
        )
    }

    override fun replaceOrAdd(
        entity: ProjectEntity,
        category: String,
        name: String,
        signature: Signature,
        groupName: String?,
        data: JsonNode,
    ): EntityDataStoreRecord { // Gets the last ID by category and name
        val id = getFirstItem(
            String.format(
                "SELECT ID FROM ENTITY_DATA_STORE " +
                        "WHERE %s = :entityId " +
                        "AND CATEGORY = :category " +
                        "AND NAME = :name " +
                        "ORDER BY ID DESC " +
                        "LIMIT 1",
                entity.projectEntityType.name
            ),
            params("entityId", entity.id())
                .addValue("category", category)
                .addValue("name", name),
            Int::class.java
        )
        // Existing record
        return if (id != null) {
            namedParameterJdbcTemplate!!.update(
                "UPDATE ENTITY_DATA_STORE SET " +
                        "CREATION = :creation, " +
                        "CREATOR = :creator, " +
                        "JSON = CAST(:json AS JSONB), " +
                        "GROUPID = :groupId " +
                        "WHERE ID = :id",
                params("id", id)
                    .addValue("groupId", groupName)
                    .addValue("json", writeJson(data))
                    .addValue("creation", dateTimeForDB(signature.time))
                    .addValue("creator", signature.user.name)
            )
            audit(EntityDataStoreRecordAuditType.UPDATED, id, signature)
            EntityDataStoreRecord(
                id,
                entity,
                category,
                name,
                groupName,
                signature,
                data
            )
        } else {
            add(entity, category, name, signature, groupName, data)
        }
    }

    override fun getRecordAudit(id: Int): List<EntityDataStoreRecordAudit> {
        return namedParameterJdbcTemplate!!.query(
            "SELECT * FROM ENTITY_DATA_STORE_AUDIT " +
                    "WHERE RECORD_ID = :recordId " +
                    "ORDER BY ID DESC",
            params("recordId", id)
        ) { rs: ResultSet, _: Int ->
            EntityDataStoreRecordAudit(
                EntityDataStoreRecordAuditType.valueOf(rs.getString("AUDIT_TYPE")),
                readSignature(rs, "TIMESTAMP", "CREATOR")
            )
        }
    }

    override fun deleteByName(entity: ProjectEntity, category: String, name: String) {
        namedParameterJdbcTemplate!!.update(
            String.format(
                "DELETE FROM ENTITY_DATA_STORE " +
                        "WHERE %s = :entityId " +
                        "AND CATEGORY = :category " +
                        "AND NAME = :name",
                entity.projectEntityType.name
            ),
            params("entityId", entity.id())
                .addValue("category", category)
                .addValue("name", name)
        )
    }

    override fun deleteByGroup(entity: ProjectEntity, category: String, groupName: String) {
        namedParameterJdbcTemplate!!.update(
            String.format(
                "DELETE FROM ENTITY_DATA_STORE " +
                        "WHERE %s = :entityId " +
                        "AND CATEGORY = :category " +
                        "AND GROUPID = :groupId",
                entity.projectEntityType.name
            ),
            params("entityId", entity.id())
                .addValue("category", category)
                .addValue("groupId", groupName)
        )
    }

    override fun deleteByCategoryBefore(category: String, beforeTime: LocalDateTime) {
        namedParameterJdbcTemplate!!.update(
            "DELETE FROM ENTITY_DATA_STORE " +
                    "WHERE CATEGORY = :category " +
                    "AND CREATION <= :beforeTime",
            params("category", category)
                .addValue("beforeTime", dateTimeForDB(beforeTime))
        )
    }

    override fun findLastByCategoryAndName(
        entity: ProjectEntity,
        category: String,
        name: String,
        beforeTime: LocalDateTime?,
    ): EntityDataStoreRecord? { // SQL & parameters
        var sql = String.format(
            "SELECT * FROM ENTITY_DATA_STORE " +
                    "WHERE %s = :entityId AND CATEGORY = :category AND NAME = :name ",
            entity.projectEntityType.name
        )
        var params = params("entityId", entity.id())
            .addValue("category", category)
            .addValue("name", name)
        // Time criteria
        if (beforeTime != null) {
            sql += "AND CREATION <= :beforeTime "
            params = params.addValue("beforeTime", dateTimeForDB(beforeTime))
        }
        // Ordering
        sql += "ORDER BY CREATION DESC, ID DESC LIMIT 1"
        // Performs the query
        return getFirstItem(
            sql,
            params
        ) { rs: ResultSet, _: Int -> toEntityDataStoreRecord(entity, rs) }
    }

    override fun findLastByCategoryAndGroupAndName(
        entity: ProjectEntity,
        category: String,
        groupName: String,
        name: String,
    ): EntityDataStoreRecord? {
        return getLastByName(
            namedParameterJdbcTemplate!!.query(
                String.format(
                    "SELECT * FROM ENTITY_DATA_STORE " +
                            "WHERE %s = :entityId " +
                            "AND CATEGORY = :category " +
                            "AND GROUPID = :groupId " +
                            "AND NAME = :name",
                    entity.projectEntityType.name
                ),
                params("entityId", entity.id())
                    .addValue("category", category)
                    .addValue("groupId", groupName)
                    .addValue("name", name)
            ) { rs: ResultSet, _: Int -> toEntityDataStoreRecord(entity, rs) }
        ).firstOrNull()
    }

    override fun findLastRecordsByNameInCategory(entity: ProjectEntity, category: String): List<EntityDataStoreRecord> {
        return getLastByName(
            namedParameterJdbcTemplate!!.query(
                String.format(
                    "SELECT * FROM ENTITY_DATA_STORE " +
                            "WHERE %s = :entityId " +
                            "AND CATEGORY = :category " +
                            "ORDER BY CREATION DESC, ID DESC",
                    entity.projectEntityType.name
                ),
                params("entityId", entity.id())
                    .addValue("category", category)
            ) { rs: ResultSet, _: Int -> toEntityDataStoreRecord(entity, rs) }
        )
    }

    private fun getLastByName(entries: List<EntityDataStoreRecord>): List<EntityDataStoreRecord> {
        return entries
            .groupBy { it.name }
            .values
            .mapNotNull { list ->
                list.minOrNull()
            }
            .sorted()
    }

    @Throws(SQLException::class)
    private fun toEntityDataStoreRecord(entity: ProjectEntity?, rs: ResultSet): EntityDataStoreRecord {
        return EntityDataStoreRecord(
            rs.getInt("ID"),
            entity,
            rs.getString("CATEGORY"),
            rs.getString("NAME"),
            rs.getString("GROUPID"),
            readSignature(rs),
            readJson(rs, "JSON")
        )
    }

    override fun addObject(
        entity: ProjectEntity,
        category: String,
        name: String,
        signature: Signature,
        groupName: String?,
        data: Any,
    ): EntityDataStoreRecord {
        return add(
            entity = entity,
            category = category,
            name = name,
            signature = signature,
            groupName = groupName,
            data = data.asJson()
        )
    }

    override fun replaceOrAddObject(
        entity: ProjectEntity,
        category: String,
        name: String,
        signature: Signature,
        groupName: String?,
        data: Any,
    ): EntityDataStoreRecord {
        return replaceOrAdd(
            entity,
            category,
            name,
            signature,
            groupName,
            data.asJson()
        )
    }

    override fun getById(entity: ProjectEntity, id: Int): EntityDataStoreRecord? {
        return getFirstItem(
            String.format(
                "SELECT * FROM ENTITY_DATA_STORE " +
                        "WHERE %s = :entityId " +
                        "AND ID = :id",
                entity.projectEntityType.name
            ),
            params("id", id).addValue("entityId", entity.id())
        ) { rs: ResultSet, _: Int -> toEntityDataStoreRecord(entity, rs) }
    }

    override fun getByCategoryAndName(
        entity: ProjectEntity,
        category: String,
        name: String,
        offset: Int,
        page: Int,
    ): List<EntityDataStoreRecord> {
        return namedParameterJdbcTemplate!!.query(
            String.format(
                "SELECT * FROM ENTITY_DATA_STORE " +
                        "WHERE %s = :entityId " +
                        "AND CATEGORY = :category " +
                        "AND NAME = :name " +
                        "ORDER BY CREATION DESC, ID DESC " +
                        "LIMIT :page OFFSET :offset",
                entity.projectEntityType.name
            ),
            params("entityId", entity.id())
                .addValue("category", category)
                .addValue("name", name)
                .addValue("offset", offset)
                .addValue("page", page)
        ) { rs: ResultSet, _: Int -> toEntityDataStoreRecord(entity, rs) }
    }

    override fun getByCategory(
        entity: ProjectEntity,
        category: String,
        offset: Int,
        page: Int,
    ): List<EntityDataStoreRecord> {
        return namedParameterJdbcTemplate!!.query(
            String.format(
                "SELECT * FROM ENTITY_DATA_STORE " +
                        "WHERE %s = :entityId " +
                        "AND CATEGORY = :category " +
                        "ORDER BY CREATION DESC, ID DESC " +
                        "LIMIT :page OFFSET :offset",
                entity.projectEntityType.name
            ),
            params("entityId", entity.id())
                .addValue("category", category)
                .addValue("offset", offset)
                .addValue("page", page)
        ) { rs: ResultSet, _: Int -> toEntityDataStoreRecord(entity, rs) }
    }

    override fun getCountByCategoryAndName(entity: ProjectEntity, category: String, name: String): Int {
        return namedParameterJdbcTemplate!!.queryForObject(
            String.format(
                "SELECT COUNT(*) FROM ENTITY_DATA_STORE " +
                        "WHERE %s = :entityId " +
                        "AND CATEGORY = :category " +
                        "AND NAME = :name ",
                entity.projectEntityType.name
            ),
            params("entityId", entity.id())
                .addValue("category", category)
                .addValue("name", name),
            Int::class.java
        ) ?: 0
    }

    override fun getCountByCategory(entity: ProjectEntity, category: String): Int {
        return namedParameterJdbcTemplate!!.queryForObject(
            String.format(
                "SELECT COUNT(*) FROM ENTITY_DATA_STORE " +
                        "WHERE %s = :entityId " +
                        "AND CATEGORY = :category ",
                entity.projectEntityType.name
            ),
            params("entityId", entity.id())
                .addValue("category", category),
            Int::class.java
        ) ?: 0
    }

    override fun deleteAll() {
        jdbcTemplate!!.update(
            "DELETE FROM ENTITY_DATA_STORE"
        )
    }

    override fun getByFilter(entityDataStoreFilter: EntityDataStoreFilter): List<EntityDataStoreRecord> {
        // Checks the entity
        requireNotNull(entityDataStoreFilter.entity) { "The filter `entity` parameter is required." }
        // SQL criterias
        val context = StringBuilder()
        val critera = StringBuilder()
        val params = MapSqlParameterSource()
        buildCriteria(entityDataStoreFilter, context, critera, params)
        // Runs the query
        return namedParameterJdbcTemplate!!.query(
            """
                    SELECT * 
                    FROM ENTITY_DATA_STORE 
                    $context
                    WHERE 1 = 1  
                    $critera 
                    ORDER BY CREATION DESC, ID DESC 
                    LIMIT :page OFFSET :offset""",
            params
                .addValue("offset", entityDataStoreFilter.offset)
                .addValue("page", entityDataStoreFilter.count)
        ) { rs: ResultSet, _: Int -> toEntityDataStoreRecord(entityDataStoreFilter.entity, rs) }
    }

    override fun forEachByFilter(
        entityDataStoreFilter: EntityDataStoreFilter,
        consumer: (EntityDataStoreRecord) -> Unit,
    ) {
        // Checks the entity
        requireNotNull(entityDataStoreFilter.entity) { "The filter `entity` parameter is required." }
        // SQL criterias
        val context = StringBuilder()
        val critera = StringBuilder()
        val params = MapSqlParameterSource()
        buildCriteria(entityDataStoreFilter, context, critera, params)
        // Runs the query
        namedParameterJdbcTemplate!!.query(
            """
                    SELECT * 
                    FROM ENTITY_DATA_STORE 
                    $context
                    WHERE 1 = 1  
                    $critera 
                    ORDER BY CREATION DESC, ID DESC 
                    LIMIT :page OFFSET :offset""",
            params
                .addValue("offset", entityDataStoreFilter.offset)
                .addValue("page", entityDataStoreFilter.count)
        ) { rs: ResultSet, _: Int ->
            val record = toEntityDataStoreRecord(entityDataStoreFilter.entity, rs)
            consumer(record)
        }
    }

    override fun getCountByFilter(entityDataStoreFilter: EntityDataStoreFilter): Int {
        // SQL criterias
        val context = StringBuilder()
        val critera = StringBuilder()
        val params = MapSqlParameterSource()
        buildCriteria(entityDataStoreFilter, context, critera, params)
        // Runs the query
        return namedParameterJdbcTemplate!!.queryForObject(
            String.format(
                "SELECT COUNT(*) FROM ENTITY_DATA_STORE " +
                        "$context " +
                        "WHERE 1 = 1 " +
                        " %s " +
                        "LIMIT :page OFFSET :offset",
                critera
            ),
            params
                .addValue("offset", entityDataStoreFilter.offset)
                .addValue("page", entityDataStoreFilter.count),
            Int::class.java
        ) ?: 0
    }

    override fun deleteByFilter(entityDataStoreFilter: EntityDataStoreFilter): Int { // SQL criterias
        val context = StringBuilder()
        val critera = StringBuilder()
        val params = MapSqlParameterSource()
        buildCriteria(entityDataStoreFilter, context, critera, params)
        // Runs the query
        return namedParameterJdbcTemplate!!.update(
            "DELETE FROM ENTITY_DATA_STORE $context WHERE 1 = 1 $critera",
            params
        )
    }

    override fun deleteRangeByFilter(entityDataStoreFilter: EntityDataStoreFilter): Int { // SQL criterias
        val critera = StringBuilder()
        val context = StringBuilder()
        val params = MapSqlParameterSource()
        buildCriteria(entityDataStoreFilter, context, critera, params)
        // Runs the query
        return namedParameterJdbcTemplate!!.update(
            """
                DELETE FROM ENTITY_DATA_STORE
                WHERE ctid IN (
                    SELECT ctid
                    FROM ENTITY_DATA_STORE
                    $context
                    WHERE 1 = 1
                    $critera
                    ORDER BY ID DESC
                    LIMIT :page OFFSET :offset
                )
                """,
            params
                .addValue("offset", entityDataStoreFilter.offset)
                .addValue("page", entityDataStoreFilter.count)
        )
    }

    private fun buildCriteria(
        filter: EntityDataStoreFilter,
        context: StringBuilder,
        criteria: StringBuilder,
        params: MapSqlParameterSource,
    ) { // Entity
        if (filter.entity != null) {
            criteria.append(String.format(" AND %s = :entityId", filter.entity!!.projectEntityType.name))
            params.addValue("entityId", filter.entity!!.id())
        }
        // Category
        if (StringUtils.isNotBlank(filter.category)) {
            criteria.append(" AND CATEGORY = :category")
            params.addValue("category", filter.category)
        }
        // Name
        if (StringUtils.isNotBlank(filter.name)) {
            criteria.append(" AND NAME = :name")
            params.addValue("name", filter.name)
        }
        // Group
        if (StringUtils.isNotBlank(filter.group)) {
            criteria.append(" AND GROUPID = :group")
            params.addValue("group", filter.group)
        }
        // Before time
        if (filter.beforeTime != null) {
            criteria.append(" AND CREATION <= :beforeTime")
            params.addValue("beforeTime", dateTimeForDB(filter.beforeTime))
        }
        // Creator
        val creator = filter.creator
        if (!creator.isNullOrBlank()) {
            criteria.append(" AND CREATOR = :creator")
            params.addValue("creator", creator)
        }
        // JSON context
        if (!filter.jsonContext.isNullOrBlank()) {
            context.append(filter.jsonContext)
        }
        // JSON filter
        if (!filter.jsonFilter.isNullOrBlank()) {
            criteria.append(" AND ${filter.jsonFilter}")
            filter.jsonFilterCriterias?.onEach { (name, value) ->
                params.addValue(name, value)
            }
        }
    }

    private fun audit(type: EntityDataStoreRecordAuditType, recordId: Int, signature: Signature) {
        namedParameterJdbcTemplate!!.update(
            "INSERT INTO ENTITY_DATA_STORE_AUDIT(RECORD_ID, AUDIT_TYPE, TIMESTAMP, CREATOR) " +
                    "VALUES (:recordId, :auditType, :timestamp, :user)",
            params("recordId", recordId)
                .addValue("auditType", type.name)
                .addValue("timestamp", dateTimeForDB(signature.time))
                .addValue("user", signature.user.name)
        )
    }
}