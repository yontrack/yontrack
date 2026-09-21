package net.nemerosa.ontrack.extension.casc

import com.networknt.schema.Error
import com.networknt.schema.Schema
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import net.nemerosa.ontrack.extension.casc.schema.json.CascJsonSchemaService
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.yaml.Yaml
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Support for testing CasC
 */
abstract class AbstractCascTestSupport : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var cascService: CascService

    @Autowired
    protected lateinit var cascJsonSchemaService: CascJsonSchemaService

    private val schemaRegistry: SchemaRegistry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)

    /**
     * Runs a CasC from a series of YAML texts
     */
    protected fun casc(vararg yaml: String) {
        asAdmin {
            cascService.runYaml(*yaml)
        }
    }

    protected fun assertValidYaml(yamlSource: String) {
        val validationMessages = validateYaml(yamlSource)
        if (validationMessages.isNotEmpty()) {
            validationMessages.forEach {
                println("* $it")
            }
            fail("YAML failed to validate")
        }
    }

    protected fun assertInvalidYaml(yamlSource: String, message: String) {
        val validationMessages = validateYaml(yamlSource)
        assertTrue(
            validationMessages.any {
                it.toString().equals(message, ignoreCase = true)
            },
            "Expected validation message to be equal to $message but was $validationMessages",
        )
    }

    /**
     * Each [Error] reads as `<instance location, as a JSON pointer>: <message>`.
     */
    protected fun validateYaml(yamlSource: String): List<Error> {
        val schemaNode = asAdmin {
            cascJsonSchemaService.createJsonSchema()
        }
        val yamlNode = Yaml().read(yamlSource).single()
        val schema: Schema = schemaRegistry.getSchema(schemaNode)
        return schema.validate(yamlNode)
    }


}