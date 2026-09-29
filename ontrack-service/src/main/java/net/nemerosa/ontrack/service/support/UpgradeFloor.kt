package net.nemerosa.ontrack.service.support

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion

/**
 * The oldest database this major accepts to upgrade (ADR 0018, *The upgrade floor*).
 *
 * 6.0 upgrades any 5.x database and nothing older: the V6 cleanup deleted the startup migrations
 * which were present in 5.0.0, relying on every 5.x install having run them once. The floor is
 * therefore the last Flyway version of 5.0.0, `V68__V5_av_audit.sql`.
 *
 * A database with a Flyway history but no successful `V68` has never run 5.0.0 and is refused,
 * before Flyway touches it. An empty database - no history at all - is a new installation, and
 * passes.
 */
object UpgradeFloor {

    /**
     * Last Flyway version of 5.0.0.
     */
    val FLOOR: MigrationVersion = MigrationVersion.fromVersion("68")

    /**
     * Refuses a database whose Flyway history does not reach the [FLOOR].
     *
     * @throws UpgradeFloorException when the database has a history which does not contain the floor
     */
    fun check(flyway: Flyway) {
        val applied = flyway.info().applied()
            .filter { !it.state.isFailed }
            .mapNotNull { it.version }
        if (applied.isNotEmpty() && FLOOR !in applied) {
            throw UpgradeFloorException(
                """
                    This database was last upgraded by a Yontrack (Ontrack) release older than 5.0 (schema version ${applied.max()}).
                    Yontrack 6 only upgrades a 5.x database: upgrade to any 5.x release first, then to 6.
                """.trimIndent()
            )
        }
    }
}

/**
 * Thrown at startup when the database is older than the [UpgradeFloor].
 */
class UpgradeFloorException(message: String) : IllegalStateException(message)
