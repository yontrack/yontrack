package net.nemerosa.ontrack.extension.audittrail.service

import net.nemerosa.ontrack.extension.audittrail.canonical.CanonicalJsonException
import net.nemerosa.ontrack.extension.audittrail.model.TrailEndorsement
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
     * Every entry is endorsed by the instance key, in the same transaction. When the key is not
     * provisioned, the entry is written all the same, unendorsed — the trail never stops a change.
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

    /**
     * Endorsements of the entries of the trail of a build, whether the licence is on or not.
     *
     * @param build Build, which the caller is allowed to see
     * @return Endorsements, by seq of their entries — none for the entries written while the
     * instance key was not provisioned
     */
    fun getEndorsements(build: Build): List<TrailEndorsement>

    /**
     * Whether the build has a trail to read: the licence is on — its trail is being written — or
     * the trail has entries — written before the licence lapsed. A build with no entry while the
     * licence is off has no trail, and never will until the licence is back.
     *
     * @param build Build, which the caller is allowed to see
     * @return Whether the trail of the build is available
     */
    fun isTrailAvailable(build: Build): Boolean
}
