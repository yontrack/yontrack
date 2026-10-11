package net.nemerosa.ontrack.extension.workflows.repository

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import net.nemerosa.ontrack.repository.support.readLocalDateTimeNotNull
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import javax.sql.DataSource

/**
 * Yontrack processes running workflow nodes, with their last heartbeat.
 */
@Repository
class WorkflowEngineProcessRepository(
    dataSource: DataSource,
) : AbstractJdbcRepository(dataSource) {

    /**
     * Records the heartbeat of a process, registering it if needed.
     */
    fun heartbeat(id: String, started: LocalDateTime, heartbeat: LocalDateTime) {
        namedParameterJdbcTemplate!!.update(
            """
                INSERT INTO WKF_ENGINE_PROCESSES(ID, STARTED, HEARTBEAT)
                VALUES (:id, :started, :heartbeat)
                ON CONFLICT (ID) DO UPDATE SET HEARTBEAT = EXCLUDED.HEARTBEAT
            """.trimIndent(),
            mapOf(
                "id" to id,
                "started" to Time.store(started),
                "heartbeat" to Time.store(heartbeat),
            )
        )
    }

    /**
     * Last heartbeat of every known process, indexed by process ID.
     */
    fun getHeartbeats(): Map<String, LocalDateTime> =
        jdbcTemplate!!.query(
            "SELECT ID, HEARTBEAT FROM WKF_ENGINE_PROCESSES"
        ) { rs, _ ->
            rs.getString("ID") to rs.readLocalDateTimeNotNull("HEARTBEAT")
        }.toMap()

    /**
     * Forgets the processes whose last heartbeat is older than [time].
     */
    fun prune(time: LocalDateTime) {
        namedParameterJdbcTemplate!!.update(
            "DELETE FROM WKF_ENGINE_PROCESSES WHERE HEARTBEAT < :time",
            mapOf("time" to Time.store(time))
        )
    }
}
