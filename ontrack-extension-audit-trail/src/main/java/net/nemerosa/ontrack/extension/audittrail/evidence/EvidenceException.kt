package net.nemerosa.ontrack.extension.audittrail.evidence

import net.nemerosa.ontrack.common.BaseException

/**
 * An evidence is refused, for a [typed reason][EvidenceError].
 *
 * @property error Why the evidence is refused
 */
class EvidenceException(
    val error: EvidenceError,
    message: String,
) : BaseException(message)
