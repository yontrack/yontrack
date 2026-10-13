package net.nemerosa.ontrack.it.jobs

import net.nemerosa.ontrack.job.JobKey
import net.nemerosa.ontrack.test.TestUtils.uid
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate

/**
 * Another replica of Yontrack, as Quartz sees it in its tables (#2079): a Quartz instance checking
 * in, and executing the fires of jobs, for the tests of the status shared by the replicas without
 * running a second one.
 *
 * Its check-in interval is long enough for the Quartz instance of the test not to find it failed,
 * and not to recover its fires. [Closing][close] it removes its fires and its check-in.
 *
 * The job of a fire must be scheduled already: the name of its Quartz scheduler is read from its
 * row of `JOBS`.
 *
 * @property instance ID of the Quartz instance of this replica, its host name
 */
class OtherReplica(
    private val jdbc: NamedParameterJdbcTemplate,
    val instance: String = uid("replica-"),
) : AutoCloseable {

    private val schedulers = mutableSetOf<String>()

    /**
     * Records a fire of a job executed by this replica, started at [since] (epoch milliseconds).
     */
    fun run(key: JobKey, since: Long = System.currentTimeMillis()) {
        val scheduler = jdbc.queryForObject(
            "SELECT SCHEDULER FROM JOBS WHERE JOB_CATEGORY = :category AND JOB_TYPE = :type AND JOB_KEY = :key",
            keyParams(key),
            String::class.java,
        ) ?: error("Job $key not scheduled")
        if (scheduler !in schedulers) {
            checkIn(scheduler)
        }
        jdbc.update(
            """
                INSERT INTO QRTZ_FIRED_TRIGGERS (SCHED_NAME, ENTRY_ID, TRIGGER_NAME, TRIGGER_GROUP, INSTANCE_NAME,
                    FIRED_TIME, SCHED_TIME, PRIORITY, STATE, JOB_NAME, JOB_GROUP, IS_NONCONCURRENT, REQUESTS_RECOVERY)
                VALUES (:scheduler, :entry, :trigger, :group, :instance,
                    :since, :since, 5, 'EXECUTING', :key, :group, TRUE, FALSE)
            """,
            keyParams(key)
                .addValue("scheduler", scheduler)
                .addValue("entry", uid("entry-"))
                .addValue("trigger", uid("run-now:"))
                .addValue("group", "${key.type.category.key}/${key.type.key}")
                .addValue("instance", instance)
                .addValue("since", since),
        )
    }

    /**
     * Checks this replica in with the Quartz scheduler of the given name, at the given time (epoch
     * milliseconds): by default now, as a live replica does.
     */
    fun checkIn(scheduler: String, at: Long = System.currentTimeMillis()) {
        jdbc.update(
            """
                INSERT INTO QRTZ_SCHEDULER_STATE (SCHED_NAME, INSTANCE_NAME, LAST_CHECKIN_TIME, CHECKIN_INTERVAL)
                VALUES (:scheduler, :instance, :at, :interval)
                ON CONFLICT (SCHED_NAME, INSTANCE_NAME) DO UPDATE SET LAST_CHECKIN_TIME = :at
            """,
            MapSqlParameterSource()
                .addValue("scheduler", scheduler)
                .addValue("instance", instance)
                .addValue("at", at)
                .addValue("interval", CHECKIN_INTERVAL_MS),
        )
        schedulers += scheduler
    }

    /**
     * The fires of this replica are over.
     */
    fun done() {
        jdbc.update(
            "DELETE FROM QRTZ_FIRED_TRIGGERS WHERE INSTANCE_NAME = :instance",
            MapSqlParameterSource("instance", instance),
        )
    }

    override fun close() {
        done()
        jdbc.update(
            "DELETE FROM QRTZ_SCHEDULER_STATE WHERE INSTANCE_NAME = :instance",
            MapSqlParameterSource("instance", instance),
        )
    }

    private fun keyParams(key: JobKey) = MapSqlParameterSource()
        .addValue("category", key.type.category.key)
        .addValue("type", key.type.key)
        .addValue("key", key.id)

    companion object {
        /**
         * Check-in interval of the other replicas, in milliseconds
         */
        const val CHECKIN_INTERVAL_MS = 3_600_000L
    }
}
