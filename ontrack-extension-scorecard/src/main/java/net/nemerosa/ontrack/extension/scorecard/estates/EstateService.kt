package net.nemerosa.ontrack.extension.scorecard.estates

import net.nemerosa.ontrack.model.structure.Project
import java.util.concurrent.CompletableFuture

/**
 * Management of the estates.
 *
 * Every method checks the licence of the delivery scorecard first, and raises the licence error
 * without it. Reading the estates needs no other right; changing them needs `EstateManagement`.
 */
interface EstateService {

    /**
     * All the estates, by name
     */
    fun findAll(): List<Estate>

    /**
     * Estate by name, `null` if none
     */
    fun findByName(name: String): Estate?

    /**
     * Estate by ID
     *
     * @throws EstateNotFoundException If not found
     */
    fun getById(id: Int): Estate

    /**
     * Creates an estate. Its readings are computed at the next run of its daily job, or on recompute.
     */
    fun create(input: EstateInput): Estate

    /**
     * Replaces the definition of an estate. Its snapshots are kept.
     */
    fun update(id: Int, input: EstateInput): Estate

    /**
     * Deletes an estate, with its snapshots
     */
    fun delete(id: Int)

    /**
     * Projects an estate selects — the projects carrying all its labels — among the ones the user can see
     */
    fun getProjects(estate: Estate): List<Project>

    /**
     * Queues the recompute of the readings of every project of the estate, overwriting the snapshots
     * of the day.
     *
     * @return The recompute being run, or `null` if one was already running for the estate
     */
    fun recompute(estate: Estate): CompletableFuture<*>?
}
