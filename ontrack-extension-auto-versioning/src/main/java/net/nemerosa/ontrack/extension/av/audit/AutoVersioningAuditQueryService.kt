package net.nemerosa.ontrack.extension.av.audit

import net.nemerosa.ontrack.model.structure.Branch

/**
 * Reads of the auto-versioning audit entries.
 *
 * An entry is visible when the current user has `ProjectView` on its **target** project — the
 * source project is not checked. The internal processing reads through [AutoVersioningAuditStore],
 * which sees every entry.
 */
interface AutoVersioningAuditQueryService {

    /**
     * Gets an entry of the target [branch], `null` when it does not exist or when the project of
     * the [branch] is not visible.
     */
    fun findByUUID(branch: Branch, uuid: String): AutoVersioningAuditEntry?

    /**
     * Gets the entries matching the [filter], among the visible ones.
     */
    fun findByFilter(filter: AutoVersioningAuditQueryFilter): List<AutoVersioningAuditEntry>

    /**
     * Counts the entries matching the [filter], among the visible ones.
     */
    fun countByFilter(filter: AutoVersioningAuditQueryFilter): Int

}
