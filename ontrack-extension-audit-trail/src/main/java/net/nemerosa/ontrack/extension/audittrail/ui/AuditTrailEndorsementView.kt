package net.nemerosa.ontrack.extension.audittrail.ui

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.api.APIName

/**
 * An endorsement of an entry, in GraphQL: as exported, with the seq of its entry.
 */
@APIName("AuditTrailEndorsement")
@APIDescription("Endorsement of an entry by the instance key: the Ed25519 signature of its hash")
data class AuditTrailEndorsementView(
    @APIDescription("Technical ID of the endorsed entry")
    val entryId: Int,
    @APIDescription("Seq of the endorsed entry")
    val seq: Int,
    @APIDescription("ID of the key which endorsed the entry")
    val keyId: String,
    @APIDescription("Signature of the hash of the entry, in base64")
    val signature: String,
    @APIDescription("Server time of the endorsement: ISO-8601 in UTC with milliseconds")
    val time: String,
)
