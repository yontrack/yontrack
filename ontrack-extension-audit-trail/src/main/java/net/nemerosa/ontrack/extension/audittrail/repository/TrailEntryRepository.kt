package net.nemerosa.ontrack.extension.audittrail.repository

import net.nemerosa.ontrack.extension.audittrail.model.TrailEntry

/**
 * Storage of the entries of the trails.
 */
interface TrailEntryRepository {

    /**
     * Locks the trail of a build until the end of the current transaction, so that two appends to
     * the same trail never compute the same `seq` nor fork its chain. Appends to the trails of other
     * builds are not blocked.
     *
     * @param buildId ID of the build
     */
    fun lockTrail(buildId: Int)

    /**
     * Last entry of the trail of a build.
     *
     * @param buildId ID of the build
     * @return Entry with the highest seq, or `null` when the trail has none
     */
    fun findLastEntry(buildId: Int): TrailEntry?

    /**
     * Stores an entry.
     *
     * @param entry Entry to store, its [TrailEntry.id] being ignored
     * @param canonicalPayload Canonical JSON text of the payload, as hashed
     * @param canonicalActor Canonical JSON text of the actor, as hashed
     * @param time Time of the entry, as hashed
     * @return Stored entry, with its ID
     */
    fun insert(entry: TrailEntry, canonicalPayload: String, canonicalActor: String, time: String): TrailEntry

    /**
     * Entries of the trail of a build.
     *
     * @param buildId ID of the build
     * @return Entries, by seq
     */
    fun findEntries(buildId: Int): List<TrailEntry>
}
