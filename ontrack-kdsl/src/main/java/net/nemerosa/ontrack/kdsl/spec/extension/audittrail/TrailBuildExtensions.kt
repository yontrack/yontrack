package net.nemerosa.ontrack.kdsl.spec.extension.audittrail

import net.nemerosa.ontrack.kdsl.connector.graphql.schema.BuildTrailQuery
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.BuildTrailVerificationQuery
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector
import net.nemerosa.ontrack.kdsl.spec.Build
import tools.jackson.databind.JsonNode

/**
 * Trail of this build: its entries and their endorsements.
 *
 * Readable whatever the licence, once written: `null` only when the build has no trail — the
 * licence is off and no entry was ever written for it.
 */
val Build.trail: Trail?
    get() = graphqlConnector.query(
        BuildTrailQuery(id.toInt())
    )?.builds?.firstOrNull()?.auditTrail?.let { trail ->
        Trail(
            entries = trail.entries.map {
                TrailEntry(
                    id = it.id,
                    seq = it.seq,
                    schemaVersion = it.schemaVersion,
                    type = it.type,
                    time = it.time,
                    actor = it.actor,
                    prevHash = it.prevHash,
                    payload = it.payload,
                    hash = it.hash,
                )
            },
            endorsements = trail.endorsements.map {
                TrailEndorsement(
                    entryId = it.entryId,
                    seq = it.seq,
                    keyId = it.keyId,
                    signature = it.signature,
                    time = it.time,
                )
            },
        )
    }

/**
 * Verifies the trail of this build, on the server: its chain, its endorsements and, on demand,
 * its evidence.
 *
 * @param includeEvidence Whether to check that the evidence the trail references is still in the
 * storage, with the recorded digest. Fails when the storage cannot be used.
 * @return Verification of the trail, `null` when the build has no trail
 */
fun Build.verifyTrail(includeEvidence: Boolean = false): TrailVerification? =
    graphqlConnector.query(
        BuildTrailVerificationQuery(id.toInt(), includeEvidence)
    )?.builds?.firstOrNull()?.auditTrail?.verification?.let { verification ->
        TrailVerification(
            chainIntact = verification.chainIntact,
            firstBrokenSeq = verification.firstBrokenSeq,
            endorsementsValid = verification.endorsementsValid,
            firstInvalidEndorsementSeq = verification.firstInvalidEndorsementSeq,
            partial = verification.partial,
            unendorsedFromSeq = verification.unendorsedFromSeq,
            missingEvidence = verification.missingEvidence,
            alteredEvidence = verification.alteredEvidence,
            problems = verification.problems.map {
                TrailVerificationProblem(
                    seq = it.seq,
                    type = it.type,
                    message = it.message,
                )
            },
        )
    }

/**
 * Downloads the JSON export of the trail of this build: its entries, their endorsements and the
 * public keys of the instance — what `yontrack audit-trail verify` checks offline.
 *
 * Fails when the build has no trail.
 *
 * @return The export, as a JSON document whose shape is set by its `exportVersion`
 */
fun Build.exportTrail(): JsonNode =
    connector.get("/rest/extension/audit-trail/builds/$id/export").body.asJson()
