package net.nemerosa.ontrack.extension.audittrail.ui

import net.nemerosa.ontrack.model.exceptions.NotFoundException

/**
 * The build has no trail: the licence is off and no entry was ever written for it.
 */
class AuditTrailNotFoundException(buildId: Int) : NotFoundException("Build $buildId has no audit trail.")
