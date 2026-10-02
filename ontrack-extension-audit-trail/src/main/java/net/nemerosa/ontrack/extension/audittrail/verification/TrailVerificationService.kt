package net.nemerosa.ontrack.extension.audittrail.verification

import net.nemerosa.ontrack.model.structure.Build

/**
 * Verification of the trails of the builds, as stored.
 */
interface TrailVerificationService {

    /**
     * Verifies the trail of a build, whether the licence is on or not — what its export would hold,
     * with the same [TrailVerifier] as an offline verification.
     *
     * @param build Build, which the caller must be allowed to see
     * @param includeEvidence Whether to check the evidence the trail references too — its
     * [TrailVerification.missingEvidence] and [TrailVerification.alteredEvidence] are `null` otherwise
     * @return Result of the verification
     */
    fun verify(build: Build, includeEvidence: Boolean = false): TrailVerification
}
