package net.nemerosa.ontrack.extension.audittrail.repository

import net.nemerosa.ontrack.extension.audittrail.model.TrailEndorsement

/**
 * Storage of the endorsements of the entries.
 */
interface TrailEndorsementRepository {

    /**
     * Stores an endorsement.
     *
     * @param endorsement Endorsement of a stored entry
     */
    fun insert(endorsement: TrailEndorsement)

    /**
     * Endorsements of the entries of the trail of a build.
     *
     * @param buildId ID of the build
     * @return Endorsements, by seq of their entries
     */
    fun findEndorsements(buildId: Int): List<TrailEndorsement>
}
