package net.nemerosa.ontrack.extension.audittrail.service

import net.nemerosa.ontrack.extension.audittrail.canonical.CanonicalJsonException
import net.nemerosa.ontrack.extension.audittrail.model.TrailEntry
import net.nemerosa.ontrack.model.structure.Build
import tools.jackson.databind.JsonNode

/**
 * Writing and reading the trails of the builds.
 */
interface TrailService {

    /**
     * Appends an entry to the trail of a build, in the transaction of the caller — the entry is
     * committed or rolled back with the change it records.
     *
     * The trail of the build is locked until the end of that transaction: concurrent appends to one
     * trail are serialized, and its `seq` and chain never fork.
     *
     * When the trail has no entry yet and the entry is not [build.created][net.nemerosa.ontrack.extension.audittrail.model.TrailEntryTypes.BUILD_CREATED],
     * the build predates its trail: a [trail.opened][net.nemerosa.ontrack.extension.audittrail.model.TrailEntryTypes.TRAIL_OPENED]
     * entry is written first, as seq 1, naming the build and marking the trail as partial.
     *
     * The time of the entry is the server's, at the millisecond. No authorization is checked: the
     * caller records a change which was authorized already.
     *
     * @param build Build whose trail is appended to
     * @param type Type of the entry
     * @param payload What changed: a JSON object, within the subset of canonical JSON
     * @param actor Who made the change: a JSON object, within the subset of canonical JSON
     * @return Appended entry, or `null` when the licence is off and nothing is written
     * @throws CanonicalJsonException When the payload or the actor is outside the subset of canonical JSON
     */
    fun append(build: Build, type: String, payload: JsonNode, actor: JsonNode): TrailEntry?

    /**
     * Entries of the trail of a build, whether the licence is on or not.
     *
     * @param build Build, which the caller is allowed to see
     * @return Entries, by seq
     */
    fun getEntries(build: Build): List<TrailEntry>
}
