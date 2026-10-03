package net.nemerosa.ontrack.kdsl.spec.extension.audittrail

import net.nemerosa.ontrack.kdsl.connector.graphql.schema.AuditTrailStorageStateQuery
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.AuditTrailStorageState
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector
import net.nemerosa.ontrack.kdsl.spec.Ontrack

/**
 * State of the evidence storage of the instance, as its last probe found it — readable by every
 * user. Evidence can only be attached while it is [AuditTrailStorageState.OK].
 */
val Ontrack.auditTrailStorageState: AuditTrailStorageState
    get() = graphqlConnector.query(
        AuditTrailStorageStateQuery()
    )?.auditTrailStorageState
        ?: error("Could not read the state of the evidence storage")
