package net.nemerosa.ontrack.extension.workflows.repository

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstance
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstanceFilter
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstanceNode
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstanceNodeStatus
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.events.SerializableEvent
import net.nemerosa.ontrack.model.events.merge
import net.nemerosa.ontrack.model.pagination.PaginatedList
import net.nemerosa.ontrack.model.templating.TemplatingContextData
import net.nemerosa.ontrack.model.trigger.TriggerData
import net.nemerosa.ontrack.model.trigger.TriggerRegistry
import net.nemerosa.ontrack.model.trigger.getTriggerById
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.LocalDateTime
import javax.sql.DataSource

@Repository
class WorkflowInstanceRepository(
    dataSource: DataSource,
    val triggerRegistry: TriggerRegistry,
) : AbstractJdbcRepository(dataSource) {

    fun createInstance(instance: WorkflowInstance) {
        namedParameterJdbcTemplate!!.update(
            """
                INSERT INTO WKF_INSTANCES(ID, TIMESTAMP, WORKFLOW, EVENT, TRIGGER_ID, TRIGGER_DATA)
                VALUES (:id, :timestamp, CAST(:workflow AS JSONB), CAST(:event AS JSONB), :triggerId, CAST(:triggerData AS JSONB))
            """.trimIndent(),
            mapOf(
                "id" to instance.id,
                "timestamp" to dateTimeForDB(instance.timestamp),
                "workflow" to writeJson(instance.workflow),
                "event" to writeJson(instance.event),
                "triggerId" to instance.triggerData?.id,
                "triggerData" to writeJson(instance.triggerData?.data),
            )
        )
        instance.contexts.forEach { (contextName, contextData) ->
            namedParameterJdbcTemplate!!.update(
                """
                    INSERT INTO WKF_INSTANCE_CONTEXT(INSTANCE_ID, CONTEXT_ID, HANDLER_ID, DATA)
                    VALUES (:instanceId, :contextId, :handlerId, CAST(:data AS JSONB))
                """.trimIndent(),
                mapOf(
                    "instanceId" to instance.id,
                    "contextId" to contextName,
                    "handlerId" to contextData.id,
                    "data" to writeJson(contextData.data),
                )
            )
        }
        instance.nodesExecutions.forEach { nx ->
            namedParameterJdbcTemplate!!.update(
                """
                   INSERT INTO WKF_INSTANCE_NODES(INSTANCE_ID, NODE_ID, STATUS, START_TIME, END_TIME, OUTPUT, ERROR) 
                   VALUES (:instanceId, :nodeId, :status, :startTime, :endTime, CAST(:output as JSONB), :error)
                """.trimIndent(),
                mapOf(
                    "instanceId" to instance.id,
                    "nodeId" to nx.id,
                    "status" to nx.status.name,
                    "startTime" to dateTimeForDB(nx.startTime),
                    "endTime" to dateTimeForDB(nx.endTime),
                    "output" to writeJson(nx.output),
                    "error" to nx.error,
                )
            )
        }
    }

    fun findWorkflowInstance(id: String): WorkflowInstance? =
        namedParameterJdbcTemplate!!.query(
            """
                SELECT *
                FROM WKF_INSTANCES
                WHERE ID = :id
            """.trimIndent(),
            mapOf("id" to id)
        ) { rs, _ ->
            toWorkflowInstance(rs)
        }.firstOrNull()

    private fun toWorkflowInstance(rs: ResultSet): WorkflowInstance {
        val instanceId = rs.getString("ID")
        val nodesExecutions = namedParameterJdbcTemplate!!.query(
            """
                SELECT *
                FROM WKF_INSTANCE_NODES
                WHERE INSTANCE_ID = :instanceId
            """.trimIndent(),
            mapOf(
                "instanceId" to instanceId,
            )
        ) { rsn, _ ->
            toWorkflowInstanceNode(rsn)
        }
        val contexts = namedParameterJdbcTemplate!!.query(
            """
                SELECT *
                FROM WKF_INSTANCE_CONTEXT
                WHERE INSTANCE_ID = :instanceId
            """.trimIndent(),
            mapOf(
                "instanceId" to instanceId,
            )
        ) { rsn, _ ->
            rsn.getString("CONTEXT_ID")!! to TemplatingContextData(
                id = rsn.getString("HANDLER_ID"),
                data = readJson(rsn, "DATA"),
            )
        }.toMap()
        val triggerId = rs.getString("TRIGGER_ID")
        val triggerData = readJson(rs, "TRIGGER_DATA")
        return WorkflowInstance(
            id = instanceId,
            timestamp = dateTimeFromDB(rs.getString("TIMESTAMP"))!!,
            workflow = readJson(rs, "WORKFLOW").parse(),
            event = readJson(rs, "EVENT").parse(),
            triggerData = if (triggerId != null && triggerData != null) {
                TriggerData(
                    id = triggerId,
                    data = triggerData,
                )
            } else {
                null
            },
            contexts = contexts,
            nodesExecutions = nodesExecutions,
        )
    }

    private fun toWorkflowInstanceNode(rsn: ResultSet) = WorkflowInstanceNode(
        id = rsn.getString("NODE_ID"),
        status = WorkflowInstanceNodeStatus.valueOf(rsn.getString("STATUS")),
        startTime = dateTimeFromDB(rsn.getString("START_TIME")),
        endTime = dateTimeFromDB(rsn.getString("END_TIME")),
        output = readJson(rsn, "OUTPUT"),
        error = rsn.getString("ERROR"),
    )

    fun nodeWaiting(instanceId: String, nodeId: String) {
        namedParameterJdbcTemplate!!.update(
            """
                UPDATE WKF_INSTANCE_NODES
                SET STATUS = :status
                WHERE INSTANCE_ID = :instanceId
                AND NODE_ID = :nodeId
            """.trimIndent(),
            mapOf(
                "instanceId" to instanceId,
                "nodeId" to nodeId,
                "status" to WorkflowInstanceNodeStatus.WAITING.name,
            )
        )
    }

    fun nodeStarted(instanceId: String, nodeId: String) {
        namedParameterJdbcTemplate!!.update(
            """
                UPDATE WKF_INSTANCE_NODES
                SET STATUS = :status, START_TIME = :startTime
                WHERE INSTANCE_ID = :instanceId
                AND NODE_ID = :nodeId
            """.trimIndent(),
            mapOf(
                "instanceId" to instanceId,
                "nodeId" to nodeId,
                "status" to WorkflowInstanceNodeStatus.STARTED.name,
                "startTime" to dateTimeForDB(Time.now),
            )
        )
    }

    fun nodeSuccess(instanceId: String, nodeId: String, output: JsonNode?, event: SerializableEvent?) {
        if (event != null) {
            mergeInstanceEvent(instanceId, event)
        }
        namedParameterJdbcTemplate!!.update(
            """
                UPDATE WKF_INSTANCE_NODES
                SET STATUS = :status, OUTPUT = CAST(:output as JSONB), END_TIME = :endTime
                WHERE INSTANCE_ID = :instanceId
                AND NODE_ID = :nodeId
            """.trimIndent(),
            mapOf(
                "instanceId" to instanceId,
                "nodeId" to nodeId,
                "status" to WorkflowInstanceNodeStatus.SUCCESS.name,
                "output" to writeJson(output),
                "endTime" to dateTimeForDB(Time.now),
            )
        )
    }

    private fun mergeInstanceEvent(instanceId: String, event: SerializableEvent) {
        // Locking the existing event
        val existingEvent = namedParameterJdbcTemplate!!.query(
            """
                SELECT EVENT
                FROM WKF_INSTANCES
                WHERE ID = :instanceId
                FOR UPDATE
            """.trimIndent(),
            mapOf(
                "instanceId" to instanceId,
            )
        ) { rs, _ ->
            readJson(rs, "EVENT").parse<SerializableEvent>()
        }.first()
        // Merging the two events
        val mergedEvent: SerializableEvent = existingEvent.merge(event)
        // Saving the event back
        namedParameterJdbcTemplate!!.update(
            """
                UPDATE WKF_INSTANCES
                SET EVENT = CAST(:event AS JSONB)
                WHERE ID = :instanceId
            """.trimIndent(),
            mapOf(
                "instanceId" to instanceId,
                "event" to writeJson(mergedEvent)
            )
        )
    }

    fun nodeProgress(instanceId: String, nodeId: String, output: JsonNode?) {
        namedParameterJdbcTemplate!!.update(
            """
                UPDATE WKF_INSTANCE_NODES
                SET OUTPUT = CAST(:output as JSONB)
                WHERE INSTANCE_ID = :instanceId
                AND NODE_ID = :nodeId
            """.trimIndent(),
            mapOf(
                "instanceId" to instanceId,
                "nodeId" to nodeId,
                "output" to writeJson(output),
            )
        )
    }

    fun nodeError(instanceId: String, nodeId: String, message: String?, output: JsonNode?) {
        val actualOutput = output ?: getNodeOutput(instanceId, nodeId)
        namedParameterJdbcTemplate!!.update(
            """
                UPDATE WKF_INSTANCE_NODES
                SET STATUS = :status, OUTPUT = CAST(:output as JSONB), ERROR = :error, END_TIME = :endTime
                WHERE INSTANCE_ID = :instanceId
                AND NODE_ID = :nodeId
            """.trimIndent(),
            mapOf(
                "instanceId" to instanceId,
                "nodeId" to nodeId,
                "status" to WorkflowInstanceNodeStatus.ERROR.name,
                "output" to writeJson(actualOutput),
                "error" to message,
                "endTime" to dateTimeForDB(Time.now),
            )
        )
    }

    fun nodeCancelled(instanceId: String, nodeId: String, message: String) {
        namedParameterJdbcTemplate!!.update(
            """
                UPDATE WKF_INSTANCE_NODES
                SET STATUS = :status, ERROR = :error, END_TIME = :endTime
                WHERE INSTANCE_ID = :instanceId
                AND NODE_ID = :nodeId
            """.trimIndent(),
            mapOf(
                "instanceId" to instanceId,
                "nodeId" to nodeId,
                "status" to WorkflowInstanceNodeStatus.CANCELLED.name,
                "error" to message,
                "endTime" to dateTimeForDB(Time.now),
            )
        )
    }

    fun getNodeStatus(instanceId: String, nodeId: String) =
        namedParameterJdbcTemplate!!.queryForObject(
            """
                SELECT STATUS
                FROM WKF_INSTANCE_NODES
                WHERE INSTANCE_ID = :instanceId
                AND NODE_ID = :nodeId
            """.trimIndent(),
            mapOf(
                "instanceId" to instanceId,
                "nodeId" to nodeId,
            ),
            String::class.java
        )?.let { WorkflowInstanceNodeStatus.valueOf(it) }

    /**
     * Gets the statuses of several nodes at once.
     *
     * Used when a node waits for all its parents: one query per waiting node instead of
     * one query per parent keeps the pressure on the connection pool under control.
     *
     * @return Statuses indexed by node ID. Unknown nodes are simply absent from the map.
     */
    fun getNodeStatuses(instanceId: String, nodeIds: Collection<String>): Map<String, WorkflowInstanceNodeStatus> {
        if (nodeIds.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, WorkflowInstanceNodeStatus>()
        namedParameterJdbcTemplate!!.query(
            """
                SELECT NODE_ID, STATUS
                FROM WKF_INSTANCE_NODES
                WHERE INSTANCE_ID = :instanceId
                AND NODE_ID IN (:nodeIds)
            """.trimIndent(),
            mapOf(
                "instanceId" to instanceId,
                "nodeIds" to nodeIds,
            ),
        ) { rs, _ ->
            result[rs.getString("NODE_ID")] = WorkflowInstanceNodeStatus.valueOf(rs.getString("STATUS"))
        }
        return result
    }

    fun getNodeOutput(instanceId: String, nodeId: String): JsonNode? =
        namedParameterJdbcTemplate!!.query(
            """
                SELECT OUTPUT
                FROM WKF_INSTANCE_NODES
                WHERE INSTANCE_ID = :instanceId
                AND NODE_ID = :nodeId
            """.trimIndent(),
            mapOf(
                "instanceId" to instanceId,
                "nodeId" to nodeId,
            ),
        ) { rs, _ ->
            readJson(rs, "OUTPUT")
        }.firstOrNull()

    fun stopInstance(instanceId: String) {
        findWorkflowInstance(instanceId)?.let { instance ->
            // Marking each unfinished node as cancelled
            instance.nodesExecutions.forEach { nx ->
                if (!nx.status.finished) {
                    nodeCancelled(instanceId, nx.id, "Instance stopped")
                }
            }
            // Updates the instance status
        }
    }

    private fun buildFilter(
        workflowInstanceFilter: WorkflowInstanceFilter,
        criterias: MutableList<String>,
        params: MutableMap<String, Any?>,
    ) {
        if (!workflowInstanceFilter.name.isNullOrBlank()) {
            criterias += "i.WORKFLOW::JSONB->>'name' = :name"
            params["name"] = workflowInstanceFilter.name
        }
        workflowInstanceFilter.triggerId?.takeIf { it.isNotBlank() }?.let { triggerId ->
            criterias += "i.TRIGGER_ID = :triggerId"
            params["triggerId"] = triggerId
            workflowInstanceFilter.triggerData?.takeIf { it.isNotBlank() }?.let { triggerData ->
                // We need to the trigger
                val trigger = triggerRegistry.getTriggerById<Any>(triggerId)
                // We need to convert the trigger data into a JSON path in the trigger data JSON
                trigger.filterCriteria(
                    token = triggerData,
                    criterias = criterias,
                    params = params,
                )
            }
        }
    }

    private fun findInstancesWithStatus(workflowInstanceFilter: WorkflowInstanceFilter): PaginatedList<WorkflowInstance> {
        val criterias = mutableListOf<String>()
        val params = mutableMapOf<String, Any?>()

        // Instance level criterias
        buildFilter(workflowInstanceFilter, criterias, params)

        // Node level criterias
        params["status"] = workflowInstanceFilter.status?.name

        // Where clause
        val where = if (criterias.isEmpty()) {
            ""
        } else {
            "WHERE " + criterias.joinToString(" AND ") { "($it)" }
        }

        /**
         * See [WorkflowInstance.computeStatus]
         */
        val subQuery = """
            SELECT i.*,
            CASE 
                WHEN COUNT(*) = SUM(CASE WHEN n.status = 'CREATED' THEN 1 ELSE 0 END)
                     THEN 'STARTED'
                WHEN SUM(
                     CASE WHEN n.status IN ('ERROR','CANCELLED','TIMEOUT','SUCCESS') 
                          THEN 0 
                          ELSE 1 
                     END
                   ) > 0
                     THEN 'RUNNING'
                WHEN SUM(CASE WHEN n.status = 'ERROR' THEN 1 ELSE 0 END) > 0
                     THEN 'ERROR'
                WHEN SUM(CASE WHEN n.status IN ('CANCELLED','TIMEOUT') THEN 1 ELSE 0 END) > 0
                     THEN 'STOPPED'
                WHEN COUNT(*) = SUM(CASE WHEN n.status = 'SUCCESS' THEN 1 ELSE 0 END)
                     THEN 'SUCCESS'
                ELSE 'RUNNING'
            END AS computed_status
            FROM wkf_instances i
            JOIN wkf_instance_nodes n ON i.id = n.instance_id
            GROUP BY i.id
        """.trimIndent()

        val count = namedParameterJdbcTemplate!!.queryForObject(
            """
                SELECT COUNT(*)
                FROM (
                    $subQuery
                    $where
                ) AS sub
                WHERE sub.computed_status = :status
            """.trimIndent(),
            params,
            Int::class.java
        ) ?: 0

        val items = namedParameterJdbcTemplate!!.query(
            """
                SELECT sub.*
                FROM (
                    $subQuery
                    $where
                ) AS sub
                WHERE sub.computed_status = :status
                ORDER BY sub.TIMESTAMP DESC
                LIMIT :limit
                OFFSET :offset
            """.trimIndent(),
            params + mapOf(
                "limit" to workflowInstanceFilter.size,
                "offset" to workflowInstanceFilter.offset
            )
        ) { rs, _ ->
            toWorkflowInstance(rs)
        }

        return PaginatedList.create(
            items = items,
            offset = workflowInstanceFilter.offset,
            pageSize = workflowInstanceFilter.size,
            total = count
        )
    }

    fun findInstances(workflowInstanceFilter: WorkflowInstanceFilter): PaginatedList<WorkflowInstance> {

        val criterias = mutableListOf<String>()
        val params = mutableMapOf<String, Any?>()

        if (!workflowInstanceFilter.id.isNullOrBlank()) {
            criterias += "i.ID = :id"
            params["id"] = workflowInstanceFilter.id
        } else if (workflowInstanceFilter.status != null) {
            return findInstancesWithStatus(workflowInstanceFilter)
        } else {
            buildFilter(workflowInstanceFilter, criterias, params)
        }

        val where = if (criterias.isEmpty()) {
            ""
        } else {
            "WHERE " + criterias.joinToString(" AND ") { "($it)" }
        }

        val count = namedParameterJdbcTemplate!!.queryForObject(
            """
                SELECT COUNT(*)
                FROM WKF_INSTANCES i
                $where
            """.trimIndent(),
            params,
            Int::class.java
        ) ?: 0

        val items = namedParameterJdbcTemplate!!.query(
            """
                SELECT *
                FROM WKF_INSTANCES i
                $where
                ORDER BY i.TIMESTAMP DESC
                LIMIT :limit
                OFFSET :offset
            """.trimIndent(),
            params + mapOf(
                "limit" to workflowInstanceFilter.size,
                "offset" to workflowInstanceFilter.offset
            )
        ) { rs, _ ->
            toWorkflowInstance(rs)
        }

        return PaginatedList.create(
            items = items,
            offset = workflowInstanceFilter.offset,
            pageSize = workflowInstanceFilter.size,
            total = count
        )
    }

    fun clearAll() {
        jdbcTemplate!!.update(
            """
                -- noinspection SqlWithoutWhere
                DELETE FROM WKF_INSTANCES
            """.trimIndent()
        )
    }

    fun cleanup(time: LocalDateTime) {
        val timestamp = dateTimeForDB(time)!!
        namedParameterJdbcTemplate!!.update(
            """
                DELETE FROM WKF_INSTANCES
                WHERE TIMESTAMP < :timestamp
            """.trimIndent(),
            mapOf(
                "timestamp" to timestamp,
            )
        )
    }

}