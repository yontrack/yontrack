package net.nemerosa.ontrack.extension.audittrail.tampering

import net.nemerosa.ontrack.model.exceptions.NotFoundException

/**
 * The trail of a build has no entry at this position.
 */
class TrailEntryNotFoundException(buildId: Int, seq: Int) :
    NotFoundException("The trail of the build $buildId has no entry at position $seq.")
