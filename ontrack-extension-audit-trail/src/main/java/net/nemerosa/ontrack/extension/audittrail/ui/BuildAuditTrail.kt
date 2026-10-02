package net.nemerosa.ontrack.extension.audittrail.ui

import net.nemerosa.ontrack.model.structure.Build

/**
 * The trail of a build, as the source of its GraphQL fields.
 *
 * @property build Build of the trail
 */
data class BuildAuditTrail(
    val build: Build,
)
