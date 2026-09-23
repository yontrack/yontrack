package net.nemerosa.ontrack.kdsl.spec.extension.license

import net.nemerosa.ontrack.kdsl.spec.Ontrack

/**
 * Features of the development licence.
 */
val Ontrack.devLicense: DevLicenseMgt get() = DevLicenseMgt(connector)
