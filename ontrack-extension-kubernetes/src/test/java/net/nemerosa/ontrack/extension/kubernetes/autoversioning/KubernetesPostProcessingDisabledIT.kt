package net.nemerosa.ontrack.extension.kubernetes.autoversioning

import io.fabric8.kubernetes.client.KubernetesClient
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessingRegistry
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * With the default properties, the Kubernetes extension is off: `kubernetes` is an unknown
 * post-processing.
 */
class KubernetesPostProcessingDisabledIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Autowired
    private lateinit var postProcessingRegistry: PostProcessingRegistry

    @Test
    fun `No Kubernetes post-processing bean by default`() {
        assertTrue(
            applicationContext.getBeansOfType(KubernetesPostProcessing::class.java).isEmpty(),
            "No Kubernetes post-processing bean"
        )
    }

    @Test
    fun `No Kubernetes client, builder nor runner by default`() {
        assertTrue(applicationContext.getBeanNamesForType(KubernetesClient::class.java).isEmpty(), "No client")
        assertTrue(applicationContext.getBeanNamesForType(KubernetesJobBuilder::class.java).isEmpty(), "No builder")
        assertTrue(applicationContext.getBeanNamesForType(KubernetesJobRunner::class.java).isEmpty(), "No runner")
    }

    @Test
    fun `Kubernetes is an unknown post-processing by default`() {
        assertNull(postProcessingRegistry.getPostProcessingById<Any>("kubernetes"))
        assertTrue(
            postProcessingRegistry.allPostProcessings.none { it.id == "kubernetes" },
            "Kubernetes not listed among the post-processings"
        )
    }

}
