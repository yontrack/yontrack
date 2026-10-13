package net.nemerosa.ontrack.service.security

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import javax.sql.DataSource

/**
 * Versions of the keys in `STORAGE`, one row per key, read by its primary key.
 *
 * The keys are generated and imported one at a time across the replicas, under an advisory lock
 * on the key, held by a transaction of its own: the key is in the store, and its version bumped,
 * when the next replica gets the lock.
 */
@Component
class JdbcConfidentialKeyVersions(
    dataSource: DataSource,
    transactionManager: PlatformTransactionManager,
) : ConfidentialKeyVersions {

    private val jdbc = NamedParameterJdbcTemplate(dataSource)

    private val ownTransaction = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    override fun getVersion(id: String): Long? =
        jdbc.query(
            "SELECT CAST(DATA ->> 'version' AS BIGINT) AS VERSION FROM STORAGE WHERE STORE = :store AND NAME = :id",
            params(id),
        ) { rs, _ -> rs.getLong("VERSION") }.firstOrNull()

    override fun <T> exclusively(id: String, code: () -> T): T {
        @Suppress("UNCHECKED_CAST")
        return ownTransaction.execute {
            // pg_advisory_xact_lock returns void: the row is read and ignored
            jdbc.query(
                "SELECT pg_advisory_xact_lock(:lockClass, hashtext(:id))",
                MapSqlParameterSource("lockClass", LOCK_CLASS).addValue("id", id),
            ) { _, _ -> }
            code()
        } as T
    }

    override fun bump(id: String): Long =
        jdbc.queryForObject(
            """
                INSERT INTO STORAGE (STORE, NAME, DATA)
                VALUES (:store, :id, CAST('{"version": 1}' AS JSONB))
                ON CONFLICT (STORE, NAME) DO UPDATE
                SET DATA = JSONB_BUILD_OBJECT('version', COALESCE(CAST(STORAGE.DATA ->> 'version' AS BIGINT), 0) + 1)
                RETURNING CAST(DATA ->> 'version' AS BIGINT)
            """,
            params(id),
            Long::class.java,
        ) ?: error("No version returned for the key $id")

    private fun params(id: String) = MapSqlParameterSource("store", STORE).addValue("id", id)

    companion object {
        /**
         * Store of the versions in `STORAGE`, whose names are the IDs of the keys
         */
        const val STORE = "ConfidentialKeyVersions"

        /**
         * First key of the advisory lock of the generation and of the import of a key, the second
         * being a hash of its ID: "CKEY" in ASCII - next to the locks on the trails ("TRAI"), on
         * the blobs of the evidence ("EVID"), on the reconciliation of the jobs ("JOBS"), on the
         * processing of the auto-versioning orders ("AVQU", "AVMG"), on the startup ("STRT"), on
         * the assisted changes ("ASCH") and on the re-indexation of the renamed entities ("SRNM").
         */
        const val LOCK_CLASS: Int = 0x434B4559
    }
}
