package net.nemerosa.ontrack.kdsl.acceptance.tests.actuator

import net.nemerosa.ontrack.kdsl.acceptance.tests.AbstractACCTestSupport
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ACCActuator : AbstractACCTestSupport() {

    @Test
    fun `Access to Prometheus metrics`() {
        val metrics = getMetrics()
        assertTrue(metrics.list.isNotEmpty(), "Metrics are available")
        val record = metrics.list.first()
        val value = record.values.first()
        val tags = value.tags
        assertNotNull(
            tags.find { it.name == "application" && it.value == "ontrack" },
            "Tag application is present and is equal to `ontrack`"
        )
        // The host name of the backend, the name of its pod in Kubernetes (#2088)
        assertNotNull(
            tags.find { it.name == "application_instance" && it.value.isNotBlank() },
            "Tag application_instance is present and names the backend"
        )
    }

}