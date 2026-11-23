package net.nemerosa.ontrack.extension.kubernetes.autoversioning

import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessingRegistry
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * With the extension enabled, `kubernetes` is a known post-processing. The Kubernetes client is
 * lazy: no cluster is needed for the context to start.
 */
@TestPropertySource(
    properties = [
        "ontrack.extension.kubernetes.enabled=true",
    ]
)
class KubernetesPostProcessingIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var postProcessingRegistry: PostProcessingRegistry

    @Test
    fun `Kubernetes post-processing is registered when the extension is enabled`() {
        val postProcessing = postProcessingRegistry.getPostProcessingById<Any>("kubernetes")
        assertNotNull(postProcessing, "Kubernetes post-processing is registered") {
            assertIs<KubernetesPostProcessing>(it)
            assertEquals("Kubernetes job post processing", it.name)
        }
    }

}
