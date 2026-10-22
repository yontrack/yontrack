package net.nemerosa.ontrack.extension.license

/**
 * What the development key of an instance running the `dev` profile grants: the licence
 * [DevLicenseService] starts from.
 */
interface DevLicenseKeyService {

    /**
     * Licence granted by the development key, or null when there is no key, or when it is not a
     * valid development key.
     */
    val keyLicense: License?

}
