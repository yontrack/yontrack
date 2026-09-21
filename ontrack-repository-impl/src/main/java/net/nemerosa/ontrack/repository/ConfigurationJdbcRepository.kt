package net.nemerosa.ontrack.repository

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.support.Configuration
import net.nemerosa.ontrack.model.support.ConfigurationRepository
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.springframework.stereotype.Repository
import javax.sql.DataSource

@Repository
class ConfigurationJdbcRepository(
    dataSource: DataSource
) : AbstractJdbcRepository(dataSource), ConfigurationRepository {

    override fun <T : Configuration<T>> list(configurationClass: Class<T>): List<T> =
        namedParameterJdbcTemplate!!.query(
            "SELECT * FROM CONFIGURATIONS WHERE TYPE = :type ORDER BY NAME",
            params("type", configurationClass.name)
        ) { rs, _ -> readJson(configurationClass, rs, "content") }

    override fun <T : Configuration<T>> find(configurationClass: Class<T>, name: String): T? =
        getFirstItem<T>(
            "SELECT * FROM CONFIGURATIONS WHERE TYPE = :type AND NAME = :name",
            params("type", configurationClass.name).addValue("name", name)
        ) { rs, _ -> readJson(configurationClass, rs, "content") }

    override fun <T : Configuration<T>> save(configuration: T): T {
        val params = params("type", configuration.javaClass.name).addValue("name", configuration.name)
        val id = getFirstItem(
            "SELECT ID FROM CONFIGURATIONS WHERE TYPE = :type AND NAME = :name",
            params,
            Int::class.java
        )
        if (id != null) {
            // Update
            namedParameterJdbcTemplate!!.update(
                "UPDATE CONFIGURATIONS SET CONTENT = CAST(:content AS JSONB) WHERE ID = :id",
                params.addValue("content", writeJson(configuration)).addValue("id", id)
            )
        } else {
            // Creation
            namedParameterJdbcTemplate!!.update(
                "INSERT INTO CONFIGURATIONS(TYPE, NAME, CONTENT) VALUES (:type, :name, CAST(:content AS JSONB))",
                params.addValue("content", writeJson(configuration))
            )
        }
        // OK
        return configuration
    }

    override fun <T : Configuration<T>> delete(configurationClass: Class<T>, name: String) {
        namedParameterJdbcTemplate!!.update(
            "DELETE FROM CONFIGURATIONS WHERE TYPE = :type AND NAME = :name",
            params("type", configurationClass.name).addValue("name", name)
        )
    }

    override fun <T : Configuration<T>> migrate(configurationClass: Class<T>, migration: (raw: JsonNode) -> T?) {
        namedParameterJdbcTemplate!!.query(
            "SELECT * FROM CONFIGURATIONS WHERE TYPE = :type ORDER BY NAME",
            params("type", configurationClass.name)
        ) { rs ->
            val id = rs.getInt("id")
            val content = readJson(rs, "content")
            val newValue = migration(content)
            if (newValue != null) {
                namedParameterJdbcTemplate!!.update(
                    "UPDATE CONFIGURATIONS SET CONTENT = CAST(:content AS JSONB) WHERE ID = :id",
                    params("content", writeJson(newValue)).addValue("id", id)
                )
            }
        }
    }
}