package net.nemerosa.ontrack.repository.migration

import net.nemerosa.ontrack.repository.AbstractRepositoryTestSupport
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import kotlin.test.assertEquals

/**
 * The migration removing the data of the delivery metrics (#1899) deletes the settings of the
 * end-to-end promotion metrics export - and nothing else.
 */
class DeliveryMetricsRemovalMigrationIT : AbstractRepositoryTestSupport() {

    private val e2eSettings = "net.nemerosa.ontrack.extension.dm.export.EndToEndPromotionMetricsExportSettings"

    @Test
    fun `E2E promotion metrics export settings are removed, and nothing else`() {
        val otherSettings = uid("net.nemerosa.ontrack.OtherSettings-")
        listOf("enabled", "branches", "pastDays", "restorationDays").forEach { name ->
            insertSetting(e2eSettings, name)
            insertSetting(otherSettings, name)
        }

        ResourceDatabasePopulator(
            ClassPathResource("db/migration/V90__1899_delivery_metrics_removal.sql")
        ).execute(dataSource)

        assertEquals(0, countSettings(e2eSettings), "E2E promotion metrics export settings are gone")
        assertEquals(4, countSettings(otherSettings), "Other settings are kept")
    }

    private fun insertSetting(category: String, name: String) {
        namedParameterJdbcTemplate.update(
            "DELETE FROM SETTINGS WHERE CATEGORY = :category AND NAME = :name",
            mapOf("category" to category, "name" to name)
        )
        namedParameterJdbcTemplate.update(
            "INSERT INTO SETTINGS (CATEGORY, NAME, VALUE) VALUES (:category, :name, :value)",
            mapOf("category" to category, "name" to name, "value" to "some-value")
        )
    }

    private fun countSettings(category: String): Int =
        namedParameterJdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM SETTINGS WHERE CATEGORY = :category",
            mapOf("category" to category),
            Int::class.java
        )!!

}
