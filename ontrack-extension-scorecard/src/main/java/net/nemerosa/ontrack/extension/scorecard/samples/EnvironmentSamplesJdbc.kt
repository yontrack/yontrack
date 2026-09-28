package net.nemerosa.ontrack.extension.scorecard.samples

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.chart.support.Interval
import net.nemerosa.ontrack.extension.environments.Slot
import net.nemerosa.ontrack.extension.environments.SlotPipelineStatus
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.springframework.stereotype.Repository
import javax.sql.DataSource

@Repository
class EnvironmentSamplesJdbc(
    dataSource: DataSource,
) : AbstractJdbcRepository(dataSource), EnvironmentSamples {

    override fun leadTimes(slot: Slot, interval: Interval): List<DurationSample> =
        namedParameterJdbcTemplate!!.query(
            """
                SELECT B.BRANCHID, B.CREATION AS BUILD_CREATION, MIN(P."END") AS FIRST_DONE
                FROM ENV_SLOT_PIPELINE P
                INNER JOIN BUILDS B ON B.ID = P.BUILD_ID
                WHERE P.SLOT_ID = :slot
                AND P.STATUS = :done
                AND P."END" IS NOT NULL
                GROUP BY B.ID, B.BRANCHID, B.CREATION
                HAVING MIN(P."END") >= :start AND MIN(P."END") < :end
                ORDER BY FIRST_DONE
            """.trimIndent(),
            params(slot, interval)
        ) { rs, _ ->
            DurationSample(
                branchId = rs.getInt("BRANCHID"),
                start = Time.fromStorage(rs.getString("BUILD_CREATION"))!!,
                end = Time.fromStorage(rs.getString("FIRST_DONE"))!!,
            )
        }

    override fun deployments(slot: Slot, interval: Interval): List<EventSample> =
        finished(slot, interval, fromStart = true)
            .filterNot { it.failed }
            .map { EventSample(branchId = it.branchId, time = it.end) }

    override fun outcomes(slot: Slot, interval: Interval): List<DeploymentSample> =
        finished(slot, interval, fromStart = true)

    override fun outages(slot: Slot, interval: Interval): List<OutageSample> =
        DeploymentOutages.of(finished(slot, interval, fromStart = false), interval)

    /**
     * Deployments of the slot done or failed before the end of the interval, and after its start
     * if [fromStart] is set.
     */
    private fun finished(slot: Slot, interval: Interval, fromStart: Boolean): List<DeploymentSample> {
        val startCondition = if (fromStart) """AND P."END" >= :start""" else ""
        return namedParameterJdbcTemplate!!.query(
            """
                SELECT B.BRANCHID, P.NUMBER, P."END", P.STATUS
                FROM ENV_SLOT_PIPELINE P
                INNER JOIN BUILDS B ON B.ID = P.BUILD_ID
                WHERE P.SLOT_ID = :slot
                AND P.STATUS IN (:done, :failed)
                AND P."END" IS NOT NULL
                AND P."END" < :end
                $startCondition
                ORDER BY P."END", P.NUMBER
            """.trimIndent(),
            params(slot, interval)
        ) { rs, _ ->
            DeploymentSample(
                branchId = rs.getInt("BRANCHID"),
                number = rs.getInt("NUMBER"),
                end = Time.fromStorage(rs.getString("END"))!!,
                failed = rs.getString("STATUS") == SlotPipelineStatus.FAILED.name,
            )
        }
    }

    private fun params(slot: Slot, interval: Interval) = mapOf(
        "slot" to slot.id,
        "done" to SlotPipelineStatus.DONE.name,
        "failed" to SlotPipelineStatus.FAILED.name,
        "start" to Time.store(interval.start),
        "end" to Time.store(interval.end),
    )
}
