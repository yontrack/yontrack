package net.nemerosa.ontrack.kdsl.spec.extension.license

import net.nemerosa.ontrack.kdsl.connector.graphql.schema.LicensedFeaturesQuery
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector
import net.nemerosa.ontrack.kdsl.spec.Ontrack

/**
 * Whether the licence of the instance enables a licensed feature.
 *
 * Works on any instance, whatever its profile — unlike [devLicense], which only exists on a
 * development one.
 *
 * @param featureId ID of the licensed feature
 * @return `false` when the instance has no licence, or when its licence does not enable the feature
 */
fun Ontrack.isLicensedFeatureEnabled(featureId: String): Boolean =
    graphqlConnector.query(LicensedFeaturesQuery())
        ?.licenseInfo?.license?.licensedFeatures
        ?.any { it.id == featureId && it.enabled }
        ?: false
