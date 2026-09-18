package net.nemerosa.ontrack.extension.environments.storage

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.environments.Slot
import net.nemerosa.ontrack.extension.environments.SlotPipeline
import net.nemerosa.ontrack.extension.environments.SlotPipelineStatus
import net.nemerosa.ontrack.model.pagination.PaginatedList
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.repository.BuildJdbcRepositoryAccessor
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import javax.sql.DataSource

@Repository
class SlotPipelineRepository(
    dataSource: DataSource,
    private val buildJdbcRepositoryAccessor: BuildJdbcRepositoryAccessor,
    private val slotRepository: SlotRepository,
) : AbstractJdbcRepository(dataSource) {

    fun savePipeline(pipeline: SlotPipeline): SlotPipeline {
        // Getting the pipeline
        val existing = findPipelineById(pipeline.id)
        return if (existing != null) {
            // Saving the pipeline
            namedParameterJdbcTemplate!!.update(
                """
                UPDATE ENV_SLOT_PIPELINE
                SET "END" = :end, STATUS = :status
                WHERE ID = :id
            """,
                mapOf(
                    "id" to pipeline.id,
                    "end" to pipeline.end?.let { Time.store(it) },
                    "status" to pipeline.status.name,
                )
            )
            // OK
            pipeline
        } else {
            // Getting the number of pipelines in the slot
            val number = (namedParameterJdbcTemplate!!.queryForObject(
                """
                SELECT MAX(NUMBER)
                FROM ENV_SLOT_PIPELINE
                WHERE SLOT_ID = :slotId
            """.trimIndent(),
                mapOf("slotId" to pipeline.slot.id),
                Int::class.java,
            ) ?: 0) + 1
            // Saving the pipeline
            namedParameterJdbcTemplate!!.update(
                """
                INSERT INTO ENV_SLOT_PIPELINE (ID, SLOT_ID, BUILD_ID, NUMBER, START, "END", STATUS)
                VALUES (:id, :slotId, :buildId, :number, :start, :end, :status)
            """,
                mapOf(
                    "id" to pipeline.id,
                    "slotId" to pipeline.slot.id,
                    "buildId" to pipeline.build.id(),
                    "number" to number,
                    "start" to dateTimeForDB(pipeline.start),
                    "end" to pipeline.end?.let { Time.store(it) },
                    "status" to pipeline.status.name,
                )
            )
            // OK
            pipeline.withNumber(number)
        }
    }

    fun forAllActivePipelines(slot: Slot, code: (pipeline: SlotPipeline) -> Unit) {
        val activeStatuses = SlotPipelineStatus.activeStatuses
            .joinToString(", ") { "'$it'" }
        namedParameterJdbcTemplate!!.query(
            """
                SELECT *
                FROM ENV_SLOT_PIPELINE
                WHERE SLOT_ID = :slotId
                AND STATUS IN ($activeStatuses)
            """.trimIndent(),
            mapOf(
                "slotId" to slot.id,
            )
        ) { rs ->
            val pipeline = toPipeline(rs)
            code(pipeline)
        }
    }

    fun findLastPipelineBySlotAndStatusExcludingOne(
        slot: Slot,
        status: SlotPipelineStatus,
        excludedPipeline: SlotPipeline,
    ): SlotPipeline? {
        return namedParameterJdbcTemplate!!.query(
            """
                SELECT *
                FROM ENV_SLOT_PIPELINE
                WHERE SLOT_ID = :slotId
                AND ID <> :excludedPipelineId
                AND STATUS = :status
                ORDER BY NUMBER DESC
            """.trimIndent(),
            mapOf(
                "slotId" to slot.id,
                "status" to status.name,
                "excludedPipelineId" to excludedPipeline.id,
            )
        ) { rs, _ ->
            toPipeline(rs)
        }.firstOrNull()
    }

    fun findPipelines(
        slot: Slot,
        offset: Int,
        size: Int,
        buildId: Int?,
        branchName: String? = null,
        done: Boolean? = null,
    ): PaginatedList<SlotPipeline> {
        return if (buildId != null) {
            findPipelinesForBuild(slot, offset, size, buildId, done)
        } else {
            findAllPipelines(slot, offset, size, branchName, done)
        }
    }

    private fun findAllPipelines(
        slot: Slot,
        offset: Int,
        size: Int,
        branchName: String?,
        done: Boolean?,
    ): PaginatedList<SlotPipeline> {
        var query = "WHERE SLOT_ID = :slotId"
        val params = mutableMapOf(
            "slotId" to slot.id,
        )

        if (!branchName.isNullOrBlank()) {
            params["branchName"] = branchName
            query += """
                AND EXISTS (
                    SELECT 1
                    FROM BUILDS b
                    WHERE B.ID = P.BUILD_ID
                      AND EXISTS (
                          SELECT 1
                          FROM BRANCHES BR
                          WHERE BR.ID = B.BRANCHID
                            AND BR.NAME = :branchName
                      )
                )
            """
        }

        if (done != null) {
            query += if (done) {
                " AND STATUS = 'DONE' "
            } else {
                " AND STATUS <> 'DONE' "
            }
        }

        val count = namedParameterJdbcTemplate!!.queryForObject(
            """
                SELECT COUNT(*)
                FROM ENV_SLOT_PIPELINE P
                $query
            """.trimIndent(),
            params,
            Int::class.java
        ) ?: 0
        val list = namedParameterJdbcTemplate!!.query(
            """
                SELECT *
                FROM ENV_SLOT_PIPELINE P
                $query
                ORDER BY NUMBER DESC
                LIMIT :size
                OFFSET :offset
            """.trimIndent(),
            params + mapOf(
                "offset" to offset,
                "size" to size,
            )
        ) { rs, _ ->
            toPipeline(rs)
        }
        return PaginatedList.create(items = list, offset = offset, pageSize = size, total = count)
    }

    private fun findPipelinesForBuild(
        slot: Slot,
        offset: Int,
        size: Int,
        buildId: Int,
        done: Boolean?
    ): PaginatedList<SlotPipeline> {
        val params = mutableMapOf(
            "slotId" to slot.id,
            "buildId" to buildId,
        )
        var query = """
            WHERE SLOT_ID = :slotId
            AND BUILD_ID = :buildId
        """.trimIndent()
        if (done != null) {
            query += if (done) {
                " AND STATUS = 'DONE' "
            } else {
                " AND STATUS <> 'DONE' "
            }
        }
        val count = namedParameterJdbcTemplate!!.queryForObject(
            """
                SELECT COUNT(*)
                FROM ENV_SLOT_PIPELINE
               $query
            """.trimIndent(),
            params,
            Int::class.java
        ) ?: 0
        val list = namedParameterJdbcTemplate!!.query(
            """
                SELECT *
                FROM ENV_SLOT_PIPELINE
                $query
                ORDER BY NUMBER DESC
                LIMIT :size
                OFFSET :offset
            """.trimIndent(),
            params + mapOf(
                "offset" to offset,
                "size" to size,
            )
        ) { rs, _ ->
            toPipeline(rs)
        }
        return PaginatedList.create(items = list, offset = offset, pageSize = size, total = count)
    }

    private fun toPipeline(rs: ResultSet) = toPipeline(rs, slotRepository.getSlotById(rs.getString("SLOT_ID")))

    /**
     * The same, for a caller which already holds the slot - a batch, which must not read the same
     * slot back once per row.
     */
    private fun toPipeline(rs: ResultSet, slot: Slot) = SlotPipeline(
        id = rs.getString("ID"),
        start = Time.fromStorage(rs.getString("START"))!!,
        end = Time.fromStorage(rs.getString("END")),
        number = rs.getInt("NUMBER"),
        status = SlotPipelineStatus.valueOf(rs.getString("STATUS")),
        build = buildJdbcRepositoryAccessor.getBuild(id(rs, "BUILD_ID")),
        slot = slot,
    )

    fun findPipelineById(id: String): SlotPipeline? =
        namedParameterJdbcTemplate!!.query(
            """
                SELECT *
                 FROM env_slot_pipeline
                 WHERE id = :id
            """.trimIndent(),
            mapOf("id" to id)
        ) { rs, _ ->
            toPipeline(rs)
        }.firstOrNull()

    fun getPipelineById(id: String): SlotPipeline =
        findPipelineById(id) ?: throw SlotPipelineIdNotFoundException(id)

    fun findLastDeployedPipeline(slot: Slot): SlotPipeline? =
        namedParameterJdbcTemplate!!.query(
            """
                SELECT *
                FROM ENV_SLOT_PIPELINE
                WHERE SLOT_ID = :slotId
                AND STATUS = '${SlotPipelineStatus.DONE}'
                ORDER BY NUMBER DESC
                LIMIT 1
            """.trimIndent(),
            mapOf("slotId" to slot.id)
        ) { rs, _ ->
            toPipeline(rs)
        }.firstOrNull()

    /**
     * The most recent deployment of each of these slots, whatever became of it, in one query.
     *
     * The batched form of [net.nemerosa.ontrack.extension.environments.service.SlotService.getCurrentPipeline].
     * `DISTINCT ON` is Postgres' way of saying "the first row of each group" and, with the matching
     * `ORDER BY`, it is the same "highest number wins" the single-slot query uses.
     *
     * @param slots The slots to look at, passed rather than their ids so the pipelines can be built
     *   without reading each slot back.
     * @return Pipelines by slot id, a slot with no deployment at all simply being absent.
     */
    fun findCurrentPipelinesBySlots(slots: Collection<Slot>): Map<String, SlotPipeline> =
        findPipelinesBySlots(slots, doneOnly = false)

    /**
     * The last **deployed** pipeline of each of these slots - what the slot is actually holding.
     *
     * The batched form of
     * [net.nemerosa.ontrack.extension.environments.service.SlotService.getLastDeployedPipeline].
     */
    fun findLastDeployedPipelinesBySlots(slots: Collection<Slot>): Map<String, SlotPipeline> =
        findPipelinesBySlots(slots, doneOnly = true)

    private fun findPipelinesBySlots(slots: Collection<Slot>, doneOnly: Boolean): Map<String, SlotPipeline> {
        if (slots.isEmpty()) return emptyMap()
        val slotsById = slots.associateBy { it.id }
        val statusCriteria = if (doneOnly) "AND STATUS = '${SlotPipelineStatus.DONE}'" else ""
        return namedParameterJdbcTemplate!!.query(
            """
                SELECT DISTINCT ON (SLOT_ID) *
                FROM ENV_SLOT_PIPELINE
                WHERE SLOT_ID IN (:slotIds)
                $statusCriteria
                ORDER BY SLOT_ID, NUMBER DESC
            """.trimIndent(),
            mapOf("slotIds" to slotsById.keys),
        ) { rs, _ ->
            toPipeline(rs, slotsById.getValue(rs.getString("SLOT_ID")))
        }.associateBy { it.slot.id }
    }

    fun findPipelineByBuild(build: Build): List<SlotPipeline> =
        namedParameterJdbcTemplate!!.query(
            """
                SELECT *
                FROM ENV_SLOT_PIPELINE
                WHERE BUILD_ID = :buildId
                ORDER BY START DESC
            """.trimIndent(),
            mapOf("buildId" to build.id())
        ) { rs, _ ->
            toPipeline(rs)
        }

    fun deleteDeployment(id: String) {
        namedParameterJdbcTemplate!!.update(
            """
                DELETE FROM ENV_SLOT_PIPELINE
                WHERE ID = :id
            """.trimIndent(),
            mapOf("id" to id)
        )
    }

}