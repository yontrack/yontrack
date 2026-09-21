package net.nemerosa.ontrack.repository

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.springframework.stereotype.Repository
import javax.sql.DataSource

@Repository
class PreferencesJdbcRepository(dataSource: DataSource) : AbstractJdbcRepository(dataSource), PreferencesRepository {

    override fun getPreferences(accountId: Int): JsonNode? =
        namedParameterJdbcTemplate!!.query(
            """
                SELECT CONTENT
                FROM PREFERENCES
                WHERE ACCOUNTID = :accountId
            """,
            mapOf("accountId" to accountId)
        ) { rs, _ ->
            readJson(rs, "CONTENT")
        }.firstOrNull()

    override fun setPreferences(accountId: Int, json: JsonNode) {
        namedParameterJdbcTemplate!!.update(
            """
                INSERT INTO PREFERENCES (ACCOUNTID, CONTENT) 
                VALUES (:accountId, CAST(:content AS JSONB))
                ON CONFLICT (ACCOUNTID) DO
                UPDATE SET CONTENT = EXCLUDED.CONTENT
            """,
            mapOf(
                "accountId" to accountId,
                "content" to writeJson(json),
            )
        )
    }
}