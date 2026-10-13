package net.nemerosa.ontrack.extension.av.tracking

import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.support.StartupService
import net.nemerosa.ontrack.repository.PromotionRunJdbcRepositoryAccessor
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import javax.sql.DataSource

/**
 * Migration of the auto-versioning tracking into a table.
 *
 * Added in 5.0.23, after the 6.0 upgrade floor (5.0.0): kept until V7 (ADR 0018, *The upgrade floor*).
 */
@Component
class AutoVersioningTrackingStoreMigration(
    dataSource: DataSource,
    private val promotionRunJdbcRepositoryAccessor: PromotionRunJdbcRepositoryAccessor,
    private val avTrailRepository: AvTrailRepository,
) : StartupService, AbstractJdbcRepository(dataSource) {

    private val logger: Logger = LoggerFactory.getLogger(AutoVersioningTrackingStoreMigration::class.java)

    override fun getName(): String = "Storing the auto-versioning trackings into a table"

    override fun startupOrder(): Int = StartupService.JOB_REGISTRATION

    /**
     * Each trail is removed from the store once copied, and only it (#2087): a trail which cannot
     * be copied stays in the store, with the ones after it, for the next start.
     */
    override fun start() {
        val store = "AutoVersioningTracking"
        namedParameterJdbcTemplate!!.query(
            """
                SELECT ID, PROMOTION_RUN, DATA
                FROM ENTITY_STORE
                WHERE STORE = :store
                ORDER BY ID
            """.trimIndent(),
            mapOf(
                "store" to store
            )
        ) { rs ->
            val id = rs.getInt("ID")
            val promotionRunId = rs.getInt("PROMOTION_RUN")
            val data = readJson(rs, "DATA")

            val run = promotionRunJdbcRepositoryAccessor.getPromotionRun(ID.of(promotionRunId))

            avTrailRepository.saveForPromotionRun(
                run = run,
                trail = data.parse<StoredAutoVersioningTrail>(),
            )

            // Deletion of the record once copied
            namedParameterJdbcTemplate!!.update(
                """
                    DELETE FROM ENTITY_STORE
                    WHERE ID = :id
                """.trimIndent(),
                mapOf(
                    "id" to id,
                )
            )
        }
    }
}
