package net.nemerosa.ontrack.extension.license

import net.nemerosa.ontrack.test.TestUtils.uid

object LicenseFixtures {

    val sampleFeatureId = uid("f-")

    fun sampleProvidedFeature() = ProvidedLicensedFeature(
        id = sampleFeatureId,
        name = "Sample feature",
        alwaysEnabled = false,
    )

    fun testLicense(
        active: Boolean = true,
        message: String? = null,
        features: List<LicenseFeatureData> = emptyList(),
    ) = License(
        type = "test",
        name = "Test",
        assignee = "test",
        active = active,
        validUntil = null,
        maxProjects = 10,
        features = features,
        message = message,
    )

}