package net.nemerosa.ontrack.service.support

import org.flywaydb.core.Flyway

/**
 * The two phases of the database migrations (#2057).
 *
 * - The **expand** migrations, in `db/migration`, are additive and compatible with the previous
 *   version: they always run at startup, through the Flyway bean of Spring Boot, recorded in
 *   `schema_version`.
 * - The **contract** migrations, in `db/contract`, drop what no running version uses any longer:
 *   they run after the expand ones, at startup or later on, recorded in `schema_version_contract`.
 *
 * Both share one version sequence.
 */
object ContractMigrations {

    /**
     * Location of the expand migrations
     */
    const val EXPAND_LOCATION = "classpath:db/migration"

    /**
     * Location of the contract migrations
     */
    const val LOCATION = "classpath:db/contract"

    /**
     * History table of the contract migrations
     */
    const val TABLE = "schema_version_contract"

    /**
     * Whether Flyway locks the migrations of PostgreSQL with a transactional advisory lock
     */
    const val POSTGRESQL_TRANSACTIONAL_LOCK = "flyway.postgresql.transactional.lock"

    /**
     * Flyway of the contract migrations, configured as the [expand] one - same data source, same
     * schemas - with its own locations and history table.
     *
     * It is not a Flyway bean of its own: a second one would switch the Flyway auto-configuration of
     * Spring Boot off.
     */
    @JvmStatic
    fun flyway(expand: Flyway, locations: List<String>): Flyway =
        Flyway.configure(expand.configuration.classLoader)
            .configuration(expand.configuration)
            .locations(*locations.toTypedArray())
            // The Java migrations which are beans belong to the expand phase
            .javaMigrations()
            .table(TABLE)
            // As the expand phase: a session-level lock, so that DROP INDEX CONCURRENTLY does not
            // wait for the transaction of a transactional one
            .configuration(mapOf(POSTGRESQL_TRANSACTIONAL_LOCK to "false"))
            // The schema is never empty: the expand migrations have run. The contract history starts
            // with a baseline below every version.
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .baselineDescription("<< Contract history >>")
            .load()
}
