package net.nemerosa.ontrack.extension.av.properties.yaml

import org.junit.jupiter.api.Test
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.SimpleEvaluationContext
import kotlin.test.assertEquals


/**
 * Testing the support for SpringEL when updating / reading complex structures
 */
class SpringELTest {

    @Test
    fun `Reading and updating complex structures`() {
        // Creating a structure simulating a complex YAML structure
        val structure = listOf(
            mapOf(
                "apiVersion" to "v1",
                "kind" to "Namespace",
                "metadata" to mapOf(
                    "name" to "test-listener"
                )
            ),
            mapOf(
                "apiVersion" to "apps/v1",
                "kind" to "Deployment",
                "metadata" to mapOf(
                    "name" to "test-subscriber",
                    "namespace" to "test-listener"
                )
            ),
            mapOf(
                "apiVersion" to "apps/v1",
                "kind" to "Deployment",
                "metadata" to mapOf(
                    "name" to "test-listener",
                    "namespace" to "test-listener"
                ),
                "spec" to mapOf(
                    "template" to mapOf(
                        "spec" to mapOf(
                            "containers" to listOf(
                                mapOf(
                                    "name" to "ontrack",
                                    "image" to "nemerosa/ontrack:3.41.0"
                                ),
                                mapOf(
                                    "name" to "listener-cnt",
                                    // TARGET IS HERE
                                    "image" to "docker-delivery.repository.sample.io/test/listener:0.1.1"
                                )
                            )
                        )
                    )
                )
            )
        )
        // Spring EL setup
        val parser = SpelExpressionParser()
        val context = SimpleEvaluationContext.forReadWriteDataBinding().build()
        // Expression
        val x =
            "#root.^[['kind'] == 'Deployment' and ['metadata']['name'] == 'test-listener']['spec']['template']['spec']['containers'].^[['name'] == 'listener-cnt']['image']"
        val expression = parser.parseExpression(x)
        // Reading
        val value = expression.getValue(context, structure)
        assertEquals("docker-delivery.repository.sample.io/test/listener:0.1.1", value)
        // Writing
        expression.setValue(context, structure, "docker-delivery.repository.sample.io/test/listener:0.2.0")
        val newValue = expression.getValue(context, structure)
        assertEquals("docker-delivery.repository.sample.io/test/listener:0.2.0", newValue)
    }

}