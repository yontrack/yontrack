package net.nemerosa.ontrack.extension.kubernetes

import net.nemerosa.ontrack.extension.av.AutoVersioningExtensionFeature
import net.nemerosa.ontrack.extension.scm.SCMExtensionFeature
import net.nemerosa.ontrack.extension.support.AbstractExtensionFeature
import net.nemerosa.ontrack.model.extension.ExtensionFeatureOptions
import org.springframework.stereotype.Component

/**
 * Work run as Kubernetes Jobs in the cluster Yontrack runs in.
 *
 * The feature itself is always there; what it does is behind
 * [KubernetesConfigProperties.ENABLED], off by default.
 */
@Component
class KubernetesExtensionFeature(
    scmExtensionFeature: SCMExtensionFeature,
    autoVersioningExtensionFeature: AutoVersioningExtensionFeature,
) : AbstractExtensionFeature(
    id = "kubernetes",
    name = "Kubernetes",
    description = "Runs work as Kubernetes Jobs in the cluster Yontrack runs in",
    options = ExtensionFeatureOptions.DEFAULT
        .withDependency(scmExtensionFeature)
        .withDependency(autoVersioningExtensionFeature)
)
