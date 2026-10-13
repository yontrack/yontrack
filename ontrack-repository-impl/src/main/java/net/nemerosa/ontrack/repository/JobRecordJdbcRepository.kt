package net.nemerosa.ontrack.repository

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.job.JobCategory
import net.nemerosa.ontrack.job.JobKey
import net.nemerosa.ontrack.job.JobListener
import net.nemerosa.ontrack.job.JobType
import net.nemerosa.ontrack.job.Schedule
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.springframework.stereotype.Repository
import tools.jackson.databind.JsonNode
import java.sql.ResultSet
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
    )

    companion object {

        /**
         * Rows of the jobs with the next fire of their trigger, if any: the trigger of a job has
         * the same name and group as the job, its group being the keys of its category and type,
         * separated by a slash.
         */
        private const val SELECT = """
            SELECT J.*, T.NEXT_FIRE_TIME
            FROM JOBS J
            LEFT JOIN QRTZ_TRIGGERS T
                ON T.SCHED_NAME = J.SCHEDULER
                AND T.TRIGGER_NAME = J.JOB_KEY
                AND T.TRIGGER_GROUP = J.JOB_CATEGORY || '/' || J.JOB_TYPE
        """

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
