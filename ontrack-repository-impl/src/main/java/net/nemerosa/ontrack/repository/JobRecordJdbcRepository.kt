package net.nemerosa.ontrack.repository

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.job.JobCategory
import net.nemerosa.ontrack.job.JobKey
import net.nemerosa.ontrack.job.JobListener
import net.nemerosa.ontrack.job.JobRunProgress
import net.nemerosa.ontrack.job.JobType
import net.nemerosa.ontrack.job.Schedule
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.springframework.stereotype.Repository
import tools.jackson.databind.JsonNode
import java.sql.ResultSet
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@Repository
class JobRecordJdbcRepository(dataSource: DataSource) : AbstractJdbcRepository(dataSource), JobRecordRepository {

    override fun findAll(scheduler: String): List<JobRecord> =
        namedParameterJdbcTemplate!!.query(
            "$SELECT WHERE J.SCHEDULER = :scheduler ORDER BY J.ID",
            params("scheduler", scheduler),
        ) { rs, _ -> toRecord(rs) }

    override fun findById(scheduler: String, id: Int): JobRecord? =
        getFirstItem(
            "$SELECT WHERE J.SCHEDULER = :scheduler AND J.ID = :id",
            params("scheduler", scheduler).addValue("id", id),
        ) { rs, _ -> toRecord(rs) }

    override fun findByKey(scheduler: String, key: JobKey): JobRecord? =
        getFirstItem(
            "$SELECT WHERE J.SCHEDULER = :scheduler AND J.JOB_CATEGORY = :category AND J.JOB_TYPE = :type AND J.JOB_KEY = :key",
            keyParams(scheduler, key),
        ) { rs, _ -> toRecord(rs) }

    override fun findByCategory(scheduler: String, category: String): List<JobRecord> =
        namedParameterJdbcTemplate!!.query(
            "$SELECT WHERE J.SCHEDULER = :scheduler AND J.JOB_CATEGORY = :category ORDER BY J.ID",
            params("scheduler", scheduler).addValue("category", category),
        ) { rs, _ -> toRecord(rs) }

    override fun create(scheduler: String, key: JobKey, description: String, schedule: Schedule): JobRecord {
        namedParameterJdbcTemplate!!.update(
            """
                INSERT INTO JOBS (SCHEDULER, JOB_CATEGORY, CATEGORY_NAME, JOB_TYPE, TYPE_NAME, JOB_KEY, DESCRIPTION, SCHEDULE, PAUSED)
                VALUES (
                    :scheduler, :category, :categoryName, :type, :typeName, :key, :description, CAST(:schedule AS JSONB),
                    COALESCE(
                        (SELECT S.VALUE = 'true' FROM SETTINGS S WHERE S.CATEGORY = :legacyCategory AND S.NAME = :legacyName),
                        FALSE
                    )
                )
            """,
            keyParams(scheduler, key)
                .addValue("categoryName", key.type.category.name)
                .addValue("typeName", key.type.name)
                .addValue("description", description)
                .addValue("schedule", writeJson(scheduleJson(schedule)))
                .addValue("legacyCategory", LEGACY_PAUSE_CATEGORY)
                .addValue("legacyName", key.toString()),
        )
        return findByKey(scheduler, key) ?: error("Job $key not created")
    }

    override fun update(scheduler: String, id: Int, key: JobKey, description: String, schedule: Schedule) {
        namedParameterJdbcTemplate!!.update(
            """
                UPDATE JOBS
                SET CATEGORY_NAME = :categoryName, TYPE_NAME = :typeName, DESCRIPTION = :description, SCHEDULE = CAST(:schedule AS JSONB)
                WHERE SCHEDULER = :scheduler AND ID = :id
            """,
            params("scheduler", scheduler)
                .addValue("id", id)
                .addValue("categoryName", key.type.category.name)
                .addValue("typeName", key.type.name)
                .addValue("description", description)
                .addValue("schedule", writeJson(scheduleJson(schedule))),
        )
    }

    override fun setPaused(scheduler: String, id: Int, paused: Boolean) {
        namedParameterJdbcTemplate!!.update(
            "UPDATE JOBS SET PAUSED = :paused WHERE SCHEDULER = :scheduler AND ID = :id",
            params("scheduler", scheduler).addValue("id", id).addValue("paused", paused),
        )
    }

    override fun delete(scheduler: String, id: Int) {
        namedParameterJdbcTemplate!!.update(
            "DELETE FROM JOBS WHERE SCHEDULER = :scheduler AND ID = :id",
            params("scheduler", scheduler).addValue("id", id),
        )
    }

    override fun startRun(scheduler: String, key: JobKey, startedAt: LocalDateTime) {
        namedParameterJdbcTemplate!!.update(
            """
                UPDATE JOBS
                SET RUN_COUNT = RUN_COUNT + 1, LAST_RUN_DATE = :startedAt,
                    PROGRESS_PERCENTAGE = NULL, PROGRESS_MESSAGE = NULL, STOP_REQUESTED = FALSE
                $WHERE_KEY
            """,
            keyParams(scheduler, key).addValue("startedAt", Time.store(startedAt)),
        )
    }

    override fun setProgress(scheduler: String, key: JobKey, progress: JobRunProgress) {
        namedParameterJdbcTemplate!!.update(
            "UPDATE JOBS SET PROGRESS_PERCENTAGE = :percentage, PROGRESS_MESSAGE = :message $WHERE_KEY",
            keyParams(scheduler, key)
                .addValue("percentage", progress.percentage)
                .addValue("message", progress.message),
        )
    }

    override fun endRun(scheduler: String, key: JobKey, durationMs: Long, error: String?, timedOut: Boolean) {
        val sql = if (error == null) {
            """
                UPDATE JOBS
                SET LAST_RUN_DURATION_MS = :duration, LAST_ERROR = NULL, ERROR_COUNT = 0, TIMEOUT_COUNT = 0,
                    PROGRESS_PERCENTAGE = NULL, PROGRESS_MESSAGE = NULL, STOP_REQUESTED = FALSE
                $WHERE_KEY
            """
        } else {
            """
                UPDATE JOBS
                SET LAST_ERROR = :error, ERROR_COUNT = ERROR_COUNT + 1,
                    TIMEOUT_COUNT = CASE WHEN :timedOut THEN TIMEOUT_COUNT ELSE 0 END,
                    PROGRESS_PERCENTAGE = NULL, PROGRESS_MESSAGE = NULL, STOP_REQUESTED = FALSE
                $WHERE_KEY
            """
        }
        namedParameterJdbcTemplate!!.update(
            sql,
            keyParams(scheduler, key)
                .addValue("duration", durationMs)
                .addValue("error", error)
                .addValue("timedOut", timedOut),
        )
    }

    override fun recordTimeout(scheduler: String, key: JobKey) {
        namedParameterJdbcTemplate!!.update(
            "UPDATE JOBS SET TIMEOUT_COUNT = TIMEOUT_COUNT + 1 $WHERE_KEY",
            keyParams(scheduler, key),
        )
    }

    override fun requestStop(scheduler: String, key: JobKey) {
        namedParameterJdbcTemplate!!.update(
            "UPDATE JOBS SET STOP_REQUESTED = TRUE $WHERE_KEY",
            keyParams(scheduler, key),
        )
    }

    override fun findStopRequested(scheduler: String): List<JobKey> =
        namedParameterJdbcTemplate!!.query(
            "$SELECT WHERE J.SCHEDULER = :scheduler AND J.STOP_REQUESTED",
            params("scheduler", scheduler),
        ) { rs, _ -> toRecord(rs).key }

    override fun lockForReconciliation(scheduler: String) {
        // pg_advisory_xact_lock returns void: the row is read and ignored
        namedParameterJdbcTemplate!!.query(
            "SELECT pg_advisory_xact_lock(:lockClass, hashtext(:scheduler))",
            params("lockClass", LOCK_CLASS).addValue("scheduler", scheduler),
        ) { _, _ -> }
    }

    private fun keyParams(scheduler: String, key: JobKey) =
        params("scheduler", scheduler)
            .addValue("category", key.type.category.key)
            .addValue("type", key.type.key)
            .addValue("key", key.id)

    private fun scheduleJson(schedule: Schedule) = mapOf(
        "initialPeriod" to schedule.initialPeriod,
        "period" to schedule.period,
        "unit" to schedule.unit.name,
        "cron" to schedule.cron,
    )

    private fun readSchedule(json: JsonNode) = Schedule(
        initialPeriod = json.path("initialPeriod").asLong(),
        period = json.path("period").asLong(),
        unit = TimeUnit.valueOf(json.path("unit").asString()),
        cron = json.path("cron").takeIf { !it.isNull && !it.isMissingNode }?.asString(),
    )

    private fun toRecord(rs: ResultSet) = JobRecord(
        id = rs.getInt("ID"),
        key = JobKey(
            type = JobType(
                category = JobCategory(rs.getString("JOB_CATEGORY"), rs.getString("CATEGORY_NAME")),
                key = rs.getString("JOB_TYPE"),
                name = rs.getString("TYPE_NAME"),
            ),
            id = rs.getString("JOB_KEY"),
        ),
        description = rs.getString("DESCRIPTION"),
        schedule = readSchedule(readJson(rs, "SCHEDULE")),
        paused = rs.getBoolean("PAUSED"),
        nextFireTime = rs.getLong("NEXT_FIRE_TIME").takeIf { !rs.wasNull() && it > 0 }?.let { Time.from(it) },
        runningOn = rs.getString("RUNNING_ON"),
        runningSince = rs.getLong("RUNNING_SINCE").takeIf { !rs.wasNull() && it > 0 }?.let { Time.from(it) },
        runCount = rs.getLong("RUN_COUNT"),
        lastRunDate = Time.fromStorage(rs.getString("LAST_RUN_DATE")),
        lastRunDurationMs = rs.getLong("LAST_RUN_DURATION_MS"),
        lastError = rs.getString("LAST_ERROR"),
        errorCount = rs.getLong("ERROR_COUNT"),
        timeoutCount = rs.getLong("TIMEOUT_COUNT"),
        progress = rs.getString("PROGRESS_MESSAGE")?.let { message ->
            JobRunProgress(
                percentage = rs.getInt("PROGRESS_PERCENTAGE").takeIf { !rs.wasNull() } ?: -1,
                message = message,
            )
        },
        stopRequested = rs.getBoolean("STOP_REQUESTED"),
    )

    companion object {

        /**
         * Rows of the jobs with the next fire of their trigger, if any, and the fire of the job
         * being executed, if any (#2079): the trigger of a job has the same name and group as the
         * job, its group being the keys of its category and type, separated by a slash. A fire of
         * a job is executed by one Quartz instance at a time, the job not allowing concurrent
         * runs; Quartz removes the fires of an instance which stopped checking in.
         */
        private const val SELECT = """
            SELECT J.*, T.NEXT_FIRE_TIME, F.INSTANCE_NAME AS RUNNING_ON, F.FIRED_TIME AS RUNNING_SINCE
            FROM JOBS J
            LEFT JOIN QRTZ_TRIGGERS T
                ON T.SCHED_NAME = J.SCHEDULER
                AND T.TRIGGER_NAME = J.JOB_KEY
                AND T.TRIGGER_GROUP = J.JOB_CATEGORY || '/' || J.JOB_TYPE
            LEFT JOIN LATERAL (
                SELECT FT.INSTANCE_NAME, FT.FIRED_TIME
                FROM QRTZ_FIRED_TRIGGERS FT
                WHERE FT.SCHED_NAME = J.SCHEDULER
                AND FT.JOB_NAME = J.JOB_KEY
                AND FT.JOB_GROUP = J.JOB_CATEGORY || '/' || J.JOB_TYPE
                AND FT.STATE = 'EXECUTING'
                ORDER BY FT.FIRED_TIME
                LIMIT 1
            ) F ON TRUE
        """

        /**
         * Row of one job, by its key
         */
        private const val WHERE_KEY =
            "WHERE SCHEDULER = :scheduler AND JOB_CATEGORY = :category AND JOB_TYPE = :type AND JOB_KEY = :key"

        /**
         * Category of the settings in which Yontrack 5 recorded the paused jobs, by the string form of
         * their key.
         */
        private val LEGACY_PAUSE_CATEGORY: String = JobListener::class.java.name

        /**
         * First key of the advisory lock of the reconciliation of the jobs, the second being a hash
         * of the name of the scheduler: "JOBS" in ASCII — next to the locks on the trails ("TRAI"),
         * on the blobs of the evidence ("EVID"), on the processing of the auto-versioning orders
         * ("AVQU", "AVMG"), on the startup ("STRT"), on the assisted changes ("ASCH") and on the
         * re-indexation of the renamed entities ("SRNM").
         */
        const val LOCK_CLASS: Int = 0x4A4F4253
    }
}
