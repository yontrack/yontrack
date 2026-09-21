package net.nemerosa.ontrack.extension.av.properties.yaml.path

import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.JsonPathException
import com.jayway.jsonpath.spi.json.Jackson3JsonProvider
import com.jayway.jsonpath.spi.mapper.Jackson3MappingProvider
import net.nemerosa.ontrack.extension.av.processing.AutoVersioningMissingTargetPropertyException
import net.nemerosa.ontrack.extension.av.processing.AutoVersioningReadVersionException
import net.nemerosa.ontrack.extension.av.properties.AbstractTextFilePropertyType
import net.nemerosa.ontrack.yaml.Yaml
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

@Component
class YamlPathFilePropertyType : AbstractTextFilePropertyType() {

    private val yaml = Yaml()

    private val mapper = JsonMapper.builderWithJackson2Defaults().build()

    /**
     * Jackson-based configuration so JsonPath can handle the JSON trees read from YAML
     */
    private val jsonPathConfiguration = Configuration.builder()
        .jsonProvider(Jackson3JsonProvider(mapper))
        .mappingProvider(Jackson3MappingProvider(mapper))
        .build()

    override fun readProperty(content: String, targetProperty: String?): String? {
        if (targetProperty.isNullOrBlank()) {
            throw AutoVersioningMissingTargetPropertyException("Target property is required for the `yaml-path` file type.")
        }
        return try {
            val tree = yaml.read(content)
                .singleOrNull()
                ?: throw AutoVersioningReadVersionException("The YAML file does not contain any document or more than one.")
            // Parse the JsonNode as a JSON string so JsonPath can work with it correctly
            val context = JsonPath.using(jsonPathConfiguration).parse(tree.toString())
            context.read<String>(targetProperty)
        } catch (any: JsonPathException) {
            throw AutoVersioningReadVersionException("Cannot read version from YAML file using path `$targetProperty`: [${any::class.java.simpleName}] ${any.message}")
        }
    }

    override fun replaceProperty(
        content: String,
        targetProperty: String?,
        targetVersion: String
    ): String {
        if (targetProperty.isNullOrBlank()) {
            throw AutoVersioningMissingTargetPropertyException("Target property is required for the `yaml-path` file type.")
        }
        return try {
            val tree = yaml.read(content)
                .singleOrNull()
                ?: throw AutoVersioningReadVersionException("The YAML file does not contain any document or more than one.")

            // Parse the JsonNode as a JSON string and set the new value
            val jsonString = tree.toString()
            val updatedJson = JsonPath.using(jsonPathConfiguration).parse(jsonString).set(targetProperty, targetVersion).jsonString()

            // Parse the updated JSON back to JsonNode and convert to YAML
            val updatedTree = mapper.readTree(updatedJson)

            // Convert back to YAML
            yaml.write(listOf(updatedTree))
        } catch (any: JsonPathException) {
            throw AutoVersioningReadVersionException("Cannot write version to YAML file using path `$targetProperty`: [${any::class.java.simpleName}] ${any.message}")
        }
    }

    override val id: String = "yaml-path"
}