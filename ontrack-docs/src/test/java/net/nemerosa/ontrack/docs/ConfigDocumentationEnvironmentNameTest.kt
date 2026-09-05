package net.nemerosa.ontrack.docs

import net.nemerosa.ontrack.common.camelCaseToEnvironmentName
import net.nemerosa.ontrack.common.camelCaseToKebabCase
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.env.SystemEnvironmentPropertySource
import kotlin.test.assertEquals

/**
 * The `Name` and `Environment` columns of the generated configuration documentation are only useful
 * if Spring actually binds what they publish.
 *
 * These tests drive the same helpers the generator uses — [camelCaseToKebabCase] for the `Name`
 * column and [camelCaseToEnvironmentName] for the `Environment` one — against real fields through
 * the real Spring [Binder], so a divergence from Spring's binding rules fails here rather than in a
 * user's deployment.
 *
 * Binding against an actual field is the point: asserting only that the two columns agree with each
 * other would pass for a pair of names that neither of them binds.
 */
class ConfigDocumentationEnvironmentNameTest {

    /**
     * Field names deliberately covering the shapes where dashing rules disagree: a single word, a
     * plain camel case boundary, a digit before a capital, and consecutive capitals.
     */
    data class SampleProperties(
        var url: String = "",
        var keyStore: String = "",
        var asyncCheckInterval: String = "",
        var oauth2Token: String = "",
        var apiURL: String = "",
    )

    @Test
    fun `Single word property`() {
        assertDocumentedNamesBind("url") { it.url }
    }

    @Test
    fun `Multiple word property`() {
        assertDocumentedNamesBind("keyStore") { it.keyStore }
    }

    @Test
    fun `Deeply nested multiple word property`() {
        assertDocumentedNamesBind("asyncCheckInterval") { it.asyncCheckInterval }
    }

    @Test
    fun `Property with a digit before a capital`() {
        assertDocumentedNamesBind("oauth2Token") { it.oauth2Token }
    }

    @Test
    fun `Property with consecutive capitals`() {
        assertDocumentedNamesBind("apiURL") { it.apiURL }
    }

    /**
     * Asserts that both the property name and the environment variable published for [fieldName]
     * reach the field itself.
     */
    private fun assertDocumentedNamesBind(
        fieldName: String,
        getter: (SampleProperties) -> String,
    ) {
        val qualifiedName = "$PREFIX.$fieldName"
        val propertyName = qualifiedName.camelCaseToKebabCase()
        val envName = qualifiedName.camelCaseToEnvironmentName()

        assertEquals(
            "bound",
            getter(bind(propertyName, "bound")),
            "Property $propertyName must bind to field $fieldName",
        )
        assertEquals(
            "bound",
            getter(bind(envName, "bound")),
            "Environment variable $envName must bind to field $fieldName",
        )
    }

    private fun bind(name: String, value: String): SampleProperties {
        val environment = StandardEnvironment()
        environment.propertySources.addFirst(
            SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                mapOf<String, Any>(name to value),
            )
        )
        return Binder.get(environment)
            .bind(PREFIX, Bindable.of(SampleProperties::class.java))
            .orElseGet { SampleProperties() }
    }

    companion object {
        private const val PREFIX = "ontrack.config.sample"
    }

}
