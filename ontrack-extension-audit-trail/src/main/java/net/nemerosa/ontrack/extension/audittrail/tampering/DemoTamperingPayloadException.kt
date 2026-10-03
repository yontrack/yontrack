package net.nemerosa.ontrack.extension.audittrail.tampering

import net.nemerosa.ontrack.model.exceptions.InputException

/**
 * The new payload of a tampered entry is not a JSON object a trail accepts.
 */
class DemoTamperingPayloadException(message: String) : InputException(message)
