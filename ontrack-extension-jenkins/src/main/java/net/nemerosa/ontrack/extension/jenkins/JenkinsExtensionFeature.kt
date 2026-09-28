package net.nemerosa.ontrack.extension.jenkins

import net.nemerosa.ontrack.extension.scm.SCMExtensionFeature
import net.nemerosa.ontrack.extension.support.AbstractExtensionFeature
import net.nemerosa.ontrack.model.extension.ExtensionFeatureOptions
import org.springframework.stereotype.Component

@Component
class JenkinsExtensionFeature(
    scmExtensionFeature: SCMExtensionFeature,
) : AbstractExtensionFeature(
    id = "jenkins",
    name = "Jenkins",
    description = "Provides links with Jenkins",
    options = ExtensionFeatureOptions.DEFAULT
        .withGui(true)
        .withDependency(scmExtensionFeature)
)