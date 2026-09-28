package net.nemerosa.ontrack.extension.av.audit

import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.model.structure.Branch
import java.time.LocalDateTime

/**
 * Storage abstraction for the auto versioning events.
 */
interface AutoVersioningAuditStore {

    /**
     * Cancels all orders whose processing has not started yet.
     *
     * Only the requests having the same:
     *
     * * source project
     * * qualifier
     * * target branch
     * * list of paths
     *
     * as the [order] are considered.
     *
     * @param order Order used to identify the requests to cancel
     * @return Number of requests canceled
     */
    fun throttling(order: AutoVersioningOrder): Int

    /**
     * Creates a new entry
     *
     * @param order Audit entry to create
     * @param data Data to attach to the initial `CREATED` state
     * @return The created audit entry
     */
    fun create(order: AutoVersioningOrder, data: Map<String, String> = emptyMap()): AutoVersioningAuditEntry

    /**
     * Adds a new state to an auto versioning process.
     */
    fun addState(
        targetBranch: Branch,
        uuid: String,
        routing: String? = null,
        queue: String? = null,
        upgradeBranch: String? = null,
        state: AutoVersioningAuditState,
        vararg data: Pair<String, String>
    )

    fun findByUUID(targetBranch: Branch, uuid: String): AutoVersioningAuditEntry?

    /**
     * Gets the UUIDs of the entries of the [targetBranch] whose `CREATED` state carries the given
     * [key] and [value] in its data, oldest first.
     */
    fun findUUIDsByCreationData(targetBranch: Branch, key: String, value: String): List<String>

    /**
     * Counts the number of auto versioning audit entries which are in the given [state]
     *
     * @param state State to look for
     * @return Number of auto versioning audit entries which are in the given [state]
     */
    fun countByState(state: AutoVersioningAuditState): Int

    /**
     * Global query to get audit versioning entries.
     *
     * @param filter Filter to use
     * @return List of audit entries matching the [filter]
     */
    fun auditVersioningEntries(
        filter: AutoVersioningAuditQueryFilter
    ): List<AutoVersioningAuditEntry>

    /**
     * Looks for all entries that are ready to be processed at any given time.
     */
    fun findByReady(
        time: LocalDateTime,
    ): List<AutoVersioningAuditEntry>

    /**
     * Global query to get the count versioning entries.
     *
     * @param filter Filter to use
     * @return Count of audit entries matching the [filter]
     */
    fun auditVersioningEntriesCount(
        filter: AutoVersioningAuditQueryFilter
    ): Int

    /**
     * Gets all audit entries before [retentionDate].
     *
     * @param retentionDate All items before this date will be removed
     * @param nonRunningOnly Criteria on running state
     * @return List of entries
     */
    fun findAllBefore(retentionDate: LocalDateTime, nonRunningOnly: Boolean): List<AutoVersioningAuditEntry>

    /**
     * Removes all audit entries before [retentionDate].
     *
     * @param retentionDate All items before this date will be removed
     * @param nonRunningOnly Criteria on running state
     * @return Number of entries having been removed
     */
    fun removeAllBefore(retentionDate: LocalDateTime, nonRunningOnly: Boolean): Int

    /**
     * Removes all existing audit entries
     */
    fun removeAll()
}