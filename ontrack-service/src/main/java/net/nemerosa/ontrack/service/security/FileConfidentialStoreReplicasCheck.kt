package net.nemerosa.ontrack.service.security

import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import net.nemerosa.ontrack.service.job.JobSchedulerName
import org.quartz.Scheduler
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import javax.sql.DataSource

/**
 * The `file` key store keeps the keys on the disk of each replica: a secret encrypted by one
 * replica cannot be decrypted by another one (#2086). Once the application is ready, a warning
 * lists the other live replicas sharing the database, as registered by Quartz (#2077) - not
 * earlier, so that the Quartz instance of this replica is known.
 *
 * A warning, not a failure: the registration of a replica which is gone lingers until Quartz
 * recovers it. The Helm chart refuses the `file` store with several replicas anyway.
 */
@Component
@ConditionalOnProperty(name = [OntrackConfigProperties.KEY_STORE], havingValue = "file", matchIfMissing = true)
class FileConfidentialStoreReplicasCheck(
    dataSource: DataSource,
    private val jobSchedulerName: JobSchedulerName,
    private val scheduler: ObjectProvider<Scheduler>,
) {

    private val logger = LoggerFactory.getLogger(FileConfidentialStoreReplicasCheck::class.java)

    private val jdbc = NamedParameterJdbcTemplate(dataSource)

    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        warning()?.let { logger.warn(it) }
    }

    /**
     * The warning to log, if other replicas are live
     */
    internal fun warning(): String? =
        otherReplicas().takeIf { it.isNotEmpty() }?.let { others ->
            "[key-store] The file key store keeps a key on the disk of each replica, and other replicas share " +
                    "this database: ${others.joinToString()}. A secret encrypted by one replica cannot be " +
                    "decrypted by the others. With several replicas, use the jdbc, vault or secret key store."
        }

    /**
     * Quartz instances of the scheduler of this installation, other than this one, which checked in
     * recently enough for Quartz not to consider them failed: their last check-in is no older than
     * their check-in interval, plus the 7.5 seconds Quartz allows.
     */
    internal fun otherReplicas(): List<String> =
        jdbc.queryForList(
            """
                SELECT INSTANCE_NAME
                FROM QRTZ_SCHEDULER_STATE
                WHERE SCHED_NAME = :scheduler
                AND INSTANCE_NAME <> :self
                AND LAST_CHECKIN_TIME + CHECKIN_INTERVAL + :grace >= :now
                ORDER BY INSTANCE_NAME
            """,
            MapSqlParameterSource()
                .addValue("scheduler", jobSchedulerName.value)
                .addValue("self", scheduler.getObject().schedulerInstanceId)
                .addValue("grace", QUARTZ_GRACE_MS)
                .addValue("now", System.currentTimeMillis()),
            String::class.java,
        ).filterNotNull()

    companion object {
        /**
         * Time Quartz allows past the check-in interval of an instance before considering it failed
         */
        private const val QUARTZ_GRACE_MS = 7_500L
    }
}
