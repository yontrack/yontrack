package net.nemerosa.ontrack.extension.scorecard.samples

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.chart.support.Interval
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.springframework.stereotype.Repository
import javax.sql.DataSource

@Repository
class PromotionSamplesJdbc(
    dataSource: DataSource,
) : AbstractJdbcRepository(dataSource), PromotionSamples {

    override fun leadTimes(levels: Collection<PromotionLevel>, interval: Interval): List<DurationSample> =
        if (levels.isEmpty()) {
            emptyList()
        } else {
            namedParameterJdbcTemplate!!.query(
                """
                    SELECT B.BRANCHID, B.CREATION AS BUILD_CREATION, MIN(PR.CREATION) AS FIRST_PROMOTION
                    FROM PROMOTION_RUNS PR
                    INNER JOIN BUILDS B ON B.ID = PR.BUILDID
                    WHERE PR.PROMOTIONLEVELID IN (:levels)
                    GROUP BY B.ID, B.BRANCHID, B.CREATION
                    HAVING MIN(PR.CREATION) >= :start AND MIN(PR.CREATION) < :end
                    ORDER BY FIRST_PROMOTION
                """.trimIndent(),
                params(levels, interval)
            ) { rs, _ ->
                DurationSample(
                    branchId = rs.getInt("BRANCHID"),
                    start = Time.fromStorage(rs.getString("BUILD_CREATION"))!!,
                    end = Time.fromStorage(rs.getString("FIRST_PROMOTION"))!!,
                )
            }
        }

    override fun promotions(levels: Collection<PromotionLevel>, interval: Interval): List<EventSample> =
        if (levels.isEmpty()) {
            emptyList()
        } else {
            namedParameterJdbcTemplate!!.query(
                """
                    SELECT PL.BRANCHID, PR.CREATION
                    FROM PROMOTION_RUNS PR
                    INNER JOIN PROMOTION_LEVELS PL ON PL.ID = PR.PROMOTIONLEVELID
                    WHERE PR.PROMOTIONLEVELID IN (:levels)
                    AND PR.CREATION >= :start AND PR.CREATION < :end
                    ORDER BY PR.CREATION
                """.trimIndent(),
                params(levels, interval)
            ) { rs, _ ->
                EventSample(
                    branchId = rs.getInt("BRANCHID"),
                    time = Time.fromStorage(rs.getString("CREATION"))!!,
                )
            }
        }

    override fun builds(levels: Collection<PromotionLevel>, interval: Interval): List<BuildSample> =
        buildSamples(levels, interval, fromStart = true)

    override fun outages(levels: Collection<PromotionLevel>, interval: Interval): List<OutageSample> =
        Outages.of(buildSamples(levels, interval, fromStart = false), interval)

    /**
     * Builds of the branches of the levels created before the end of the interval, and after its
     * start if [fromStart] is set, with their first promotion at the level before its end.
     */
    private fun buildSamples(
        levels: Collection<PromotionLevel>,
        interval: Interval,
        fromStart: Boolean,
    ): List<BuildSample> =
        if (levels.isEmpty()) {
            emptyList()
        } else {
            val startCondition = if (fromStart) "AND B.CREATION >= :start" else ""
            namedParameterJdbcTemplate!!.query(
                """
                    SELECT B.ID, B.BRANCHID, B.CREATION, MIN(PR.CREATION) AS FIRST_PROMOTION
                    FROM BUILDS B
                    INNER JOIN PROMOTION_LEVELS PL ON PL.BRANCHID = B.BRANCHID
                    LEFT JOIN PROMOTION_RUNS PR
                        ON PR.BUILDID = B.ID
                        AND PR.PROMOTIONLEVELID = PL.ID
                        AND PR.CREATION < :end
                    WHERE PL.ID IN (:levels)
                    AND B.CREATION < :end
                    $startCondition
                    GROUP BY B.ID, B.BRANCHID, B.CREATION
                    ORDER BY B.CREATION, B.ID
                """.trimIndent(),
                params(levels, interval)
            ) { rs, _ ->
                BuildSample(
                    branchId = rs.getInt("BRANCHID"),
                    buildId = rs.getInt("ID"),
                    creation = Time.fromStorage(rs.getString("CREATION"))!!,
                    promotion = Time.fromStorage(rs.getString("FIRST_PROMOTION")),
                )
            }
        }

    private fun params(levels: Collection<PromotionLevel>, interval: Interval) = mapOf(
        "levels" to levels.map { it.id() },
        "start" to Time.store(interval.start),
        "end" to Time.store(interval.end),
    )
}
