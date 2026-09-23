package net.nemerosa.ontrack.extension.findings.report

import com.networknt.schema.Error
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.json.schema.JsonSchemaProvider
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FindingsJsonSchemaProviderIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var jsonSchemaProviders: List<JsonSchemaProvider>

    private val schemaRegistry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)

    @Test
    fun `The neutral format is published as a JSON schema`() {
        val provider = jsonSchemaProviders.single { it.key == "findings" }
        assertEquals("Security findings report", provider.title)
    }

    @Test
    fun `The example of the specification is valid, nulls included`() {
        assertEquals(
            emptyList(),
            validate(
                """
                    {
                      "scanner": "zap",
                      "kind": "DAST",
                      "findings": [
                        {
                          "externalId": "10038",
                          "location": "",
                          "severity": "MEDIUM",
                          "rawSeverity": "Medium",
                          "title": "Content Security Policy (CSP) Header Not Set",
                          "url": "https://www.zaproxy.org/docs/alerts/10038/",
                          "fixedVersion": null,
                          "installedVersion": null,
                          "acceptance": {
                            "statement": "Served behind a proxy setting the header",
                            "expiresAt": "2026-12-31",
                            "source": "security/dast/suppressions.yaml"
                          }
                        }
                      ]
                    }
                """
            )
        )
    }

    @Test
    fun `The schema rejects unknown fields`() {
        val errors = validate(
            """
                {
                  "scanner": "gitleaks",
                  "kind": "SECRETS",
                  "findings": [
                    {"externalId": "aws", "location": "1", "severity": "HIGH", "title": "AWS", "secret": "AKIA"}
                  ]
                }
            """
        )
        assertTrue(errors.any { it.instanceLocation.toString() == "/findings/0" && it.message.contains("secret") }, "$errors")
    }

    @Test
    fun `The schema rejects missing required fields`() {
        val errors = validate(
            """
                {
                  "findings": [
                    {"externalId": "aws", "severity": "HIGH"}
                  ]
                }
            """
        )
        assertTrue(errors.any { it.message.contains("location") }, "$errors")
        assertTrue(errors.any { it.message.contains("title") }, "$errors")
    }

    @Test
    fun `The schema rejects an unknown severity`() {
        val errors = validate(
            """
                {
                  "findings": [
                    {"externalId": "aws", "location": "", "severity": "INFO", "title": "AWS"}
                  ]
                }
            """
        )
        assertTrue(errors.any { it.instanceLocation.toString() == "/findings/0/severity" }, "$errors")
    }

    private fun validate(report: String): List<Error> {
        val provider = jsonSchemaProviders.single { it.key == "findings" }
        val schema = schemaRegistry.getSchema(provider.createJsonSchema())
        return schema.validate(report.parseAsJson())
    }
}
