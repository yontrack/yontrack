package net.nemerosa.ontrack.repository

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.Ack

interface BuildFilterRepository {

    fun findForBranch(branchId: Int): Collection<TBuildFilter>

    fun findForBranch(accountId: Int?, branchId: Int): Collection<TBuildFilter>

    fun findByBranchAndName(accountId: Int, branchId: Int, name: String): TBuildFilter?

    fun save(accountId: Int?, branchId: Int, name: String, type: String, data: JsonNode): Ack

    /**
     * Deletes a filter from a branch and for an account. If `shared` is `true`,
     * we have also to delete it from the shared filters.
     *
     * @param accountId Account to delete the filter from
     * @param branchId  Branch to delete the filter from
     * @param name      Name of the filter to delete
     * @param shared    If set, deletes the filter from the shared ones as well
     * @return If a filter was deleted
     */
    fun delete(accountId: Int?, branchId: Int, name: String, shared: Boolean): Ack
}
