package net.nemerosa.ontrack.service.support

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.DriverManager
import java.util.*
import javax.sql.DataSource

/**
 * A database of its own on the IT Postgres server, created for one test, then dropped.
 */
class TemporaryDatabase private constructor(
    val url: String,
    val username: String,
    val password: String,
) {

    val dataSource: DataSource = DriverManagerDataSource(url, username, password)

    val jdbc = JdbcTemplate(dataSource)

    companion object {

        private val serverUrl = System.getProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/ontrack")
        private val username = System.getProperty("spring.datasource.username", "ontrack")
        private val password = System.getProperty("spring.datasource.password", "ontrack")

        fun <T> withTemporaryDatabase(prefix: String, code: (database: TemporaryDatabase) -> T): T {
            val name = "${prefix}_${UUID.randomUUID().toString().replace("-", "")}"
            val url = serverUrl.replace(Regex("/[^/?]+(\\?|$)"), "/$name$1")
            admin("CREATE DATABASE $name")
            try {
                return code(TemporaryDatabase(url, username, password))
            } finally {
                admin("DROP DATABASE IF EXISTS $name WITH (FORCE)")
            }
        }

        private fun admin(sql: String) {
            DriverManager.getConnection(serverUrl, username, password).use { connection ->
                connection.createStatement().use { it.execute(sql) }
            }
        }
    }
}
