package net.nemerosa.ontrack.repository

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import javax.sql.DataSource

@Repository
class StorageJdbcRepository(
    dataSource: DataSource,
) : AbstractJdbcRepository(dataSource),
    StorageRepository {

    override fun delete(store: String, key: String) {
        val params = params("store", store).addValue("key", key)
        // Deleting first
        namedParameterJdbcTemplate!!.update(
            "DELETE FROM STORAGE WHERE STORE = :store AND NAME = :key",
            params
        )
    }

    override fun exists(store: String, key: String): Boolean {
        return getFirstItem(
            "SELECT NAME FROM STORAGE WHERE STORE = :store AND NAME = :key",
            params("store", store).addValue("key", key),
            String::class.java
        ) != null
    }

    override fun storeJson(store: String, key: String, node: JsonNode) {
        val params = params("store", store)
            .addValue("key", key)
            .addValue("data", writeJson(node))
        namedParameterJdbcTemplate!!.update(
            """
                INSERT INTO STORAGE (STORE, NAME, DATA) 
                VALUES(:store, :key, CAST(:data AS JSONB))
                ON CONFLICT (STORE, NAME) DO
                UPDATE SET DATA = EXCLUDED.DATA
            """,
            params
        )
    }

    override fun retrieveJson(store: String, key: String): JsonNode? = getFirstItem(
        "SELECT DATA FROM STORAGE WHERE STORE = :store AND NAME = :key",
        params("store", store).addValue("key", key)
    ) { rs, _ -> readJson(rs, "DATA") }

    override fun getKeys(store: String): List<String> {
        return namedParameterJdbcTemplate!!.queryForList(
            "SELECT NAME FROM STORAGE WHERE STORE = :store ORDER BY NAME",
            params("store", store),
            String::class.java
        ).filterNotNull()
    }

    override fun count(store: String, context: String, query: String?, queryVariables: Map<String, *>?): Int {
        var sql = "SELECT COUNT(*) FROM STORAGE $context WHERE STORE = :store"
        if (!query.isNullOrBlank()) sql += " AND $query"

        val params = params("store", store)

        if (queryVariables != null) {
            params.addValues(queryVariables)
        }

        return namedParameterJdbcTemplate!!.queryForObject(sql, params, Int::class.java) ?: 0
    }

    override fun filter(
        store: String,
        offset: Int,
        size: Int,
        context: String,
        query: String?,
        queryVariables: Map<String, *>?,
        orderQuery: String?,
    ): List<JsonNode> =
        filterRecords(store, offset, size, context, query, queryVariables, orderQuery).values.toList()

    override fun filterRecords(
        store: String,
        offset: Int,
        size: Int,
        context: String,
        query: String?,
        queryVariables: Map<String, *>?,
        orderQuery: String?,
    ): Map<String, JsonNode> {
        var sql = "SELECT NAME, DATA FROM STORAGE $context WHERE STORE = :store"
        if (query != null && query.isNotBlank()) sql += " AND $query"
        if (orderQuery != null && orderQuery.isNotBlank()) sql += " $orderQuery"
        sql += " OFFSET :offset LIMIT :size"

        val params = params("store", store)
            .addValue("offset", offset)
            .addValue("size", size)

        if (queryVariables != null) {
            params.addValues(queryVariables)
        }

        return namedParameterJdbcTemplate!!.query(sql, params) { rs, _ ->
            rs.getString("NAME") to readJson(rs, "DATA")
        }.toMap()
    }

    override fun forEach(
        store: String,
        context: String,
        query: String?,
        queryVariables: Map<String, *>?,
        orderQuery: String?,
        code: (key: String, node: JsonNode) -> Unit,
    ) {
        var sql = "SELECT NAME, DATA FROM STORAGE $context WHERE STORE = :store"
        if (query != null && query.isNotBlank()) sql += " AND $query"
        if (orderQuery != null && orderQuery.isNotBlank()) sql += " $orderQuery"

        val params = params("store", store)

        if (queryVariables != null) {
            params.addValues(queryVariables)
        }

        namedParameterJdbcTemplate!!.query(sql, params) { rs ->
            val key = rs.getString("NAME")
            val node = readJson(rs, "DATA")
            code(key, node)
        }
    }

    override fun clear(store: String) {
        namedParameterJdbcTemplate!!.update(
            "DELETE FROM STORAGE WHERE STORE = :store",
            mapOf(
                "store" to store,
            )
        )
    }

    override fun deleteWithFilter(store: String, query: String?, queryVariables: Map<String, *>?): Int {
        var sql = "DELETE FROM STORAGE WHERE STORE = :store"
        if (query != null) sql += " AND $query"

        val params = params("store", store)
        if (queryVariables != null) {
            params.addValues(queryVariables)
        }

        return namedParameterJdbcTemplate!!.update(sql, params)
    }

    override fun getData(store: String): Map<String, JsonNode> {
        val results: MutableMap<String, JsonNode> = LinkedHashMap()
        namedParameterJdbcTemplate!!.query(
            "SELECT NAME, DATA FROM STORAGE WHERE STORE = :store ORDER BY NAME",
            params("store", store)
        ) { rs: ResultSet ->
            val name = rs.getString("NAME")
            val node = readJson(rs, "DATA")
            results[name] = node
        }
        return results
    }
}