package net.nemerosa.ontrack.kdsl.spec.extension.license

import net.nemerosa.ontrack.kdsl.connector.Connected
import net.nemerosa.ontrack.kdsl.connector.Connector

/**
 * Features of the development licence, which enables every licensed feature unless one is
 * disabled here. Only available on an instance running with the `dev` profile, as the acceptance
 * tests do.
 */
class DevLicenseMgt(connector: Connector) : Connected(connector) {

    /**
     * Enables or disables a licensed feature.
     *
     * @param featureId ID of the licensed feature
     * @param enabled Whether the feature is enabled
     */
    fun setFeatureEnabled(featureId: String, enabled: Boolean) {
        connector.put(
            path = "/extension/license/dev/features/$featureId",
            body = mapOf("enabled" to enabled),
        )
    }

    /**
     * Runs [code] with a licensed feature disabled, enabling it again afterwards.
     *
     * @param featureId ID of the licensed feature
     * @param code Code to run without the feature
     */
    fun <T> withoutFeature(featureId: String, code: () -> T): T {
        setFeatureEnabled(featureId, false)
        return try {
            code()
        } finally {
            setFeatureEnabled(featureId, true)
        }
    }
}
