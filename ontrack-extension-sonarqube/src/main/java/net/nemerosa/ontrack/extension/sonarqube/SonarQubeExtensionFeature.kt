package net.nemerosa.ontrack.extension.sonarqube

import net.nemerosa.ontrack.extension.casc.CascExtensionFeature
import net.nemerosa.ontrack.extension.support.AbstractExtensionFeature
import net.nemerosa.ontrack.job.JobCategory
import net.nemerosa.ontrack.model.extension.ExtensionFeatureOptions
import org.springframework.stereotype.Component

@Component
class SonarQubeExtensionFeature(
        cascExtensionFeature: CascExtensionFeature,
) : AbstractExtensionFeature(
        "sonarqube",
        "SonarQube",
        "Support for SonarQube metrics in Ontrack",
        ExtensionFeatureOptions.DEFAULT.withGui(true)
                .withDependency(cascExtensionFeature)
) {
    companion object {
        val SONARQUBE_JOB_CATEGORY: JobCategory = JobCategory.of("sonarqube").withName("SonarQube")
    }
}