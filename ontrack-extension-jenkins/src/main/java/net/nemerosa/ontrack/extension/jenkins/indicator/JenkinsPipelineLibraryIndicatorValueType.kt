package net.nemerosa.ontrack.extension.jenkins.indicator

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.NullNode
import tools.jackson.databind.node.StringNode
import net.nemerosa.ontrack.common.Version
import net.nemerosa.ontrack.extension.indicators.model.IndicatorCompliance
import net.nemerosa.ontrack.extension.indicators.model.IndicatorValueType
import net.nemerosa.ontrack.extension.jenkins.JenkinsExtensionFeature
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.getBooleanField
import net.nemerosa.ontrack.json.getTextField
import net.nemerosa.ontrack.json.parse
import org.springframework.stereotype.Component

/**
 * The settings may define the following configuration for a given library:
 *
 * * is the version required?
 * * what is the latest supported version?
 * * what is the last deprecated version?
 * * what is the last unsupported version?
 *
 * Depending on the actual version being used and the `required` flag, the
 * status compliance will be computed as follows:
 *
 * * if the version is not set:
 *    * if the version is required --> 0% compliance
 *    * if the version is not required --> 100% compliance
 * * if the version is set:
 *    * version >= latest supported version (good) --> 100% compliance
 *    * version > latest deprecated version (not latest, but not deprecated) --> 66% compliance
 *    * version > latest unsupported version (deprecated, but still working) --> 33% compliance
 *    * version <= latest unsupported version (cannot work) --> 0% compliance
 */
@Component
class JenkinsPipelineLibraryIndicatorValueType(
    extension: JenkinsExtensionFeature,
) : AbstractExtension(extension),
    IndicatorValueType<JenkinsPipelineLibraryVersion?, JenkinsPipelineLibraryIndicatorValueTypeConfig> {

    override val name: String = "Jenkins pipeline library"

    override fun status(
        config: JenkinsPipelineLibraryIndicatorValueTypeConfig,
        value: JenkinsPipelineLibraryVersion?,
    ): IndicatorCompliance =
        config.settings.compliance(Version.parseVersion(value?.value))

    override fun toClientString(
        config: JenkinsPipelineLibraryIndicatorValueTypeConfig,
        value: JenkinsPipelineLibraryVersion?,
    ): String = value?.value ?: ""

    override fun toClientJson(
        config: JenkinsPipelineLibraryIndicatorValueTypeConfig,
        value: JenkinsPipelineLibraryVersion?,
    ): JsonNode = mapOf("version" to value?.value).asJson()

    override fun fromClientJson(
        config: JenkinsPipelineLibraryIndicatorValueTypeConfig,
        value: JsonNode,
    ): JenkinsPipelineLibraryVersion? {
        val version = value.path("version").asText()
        return if (version.isNullOrBlank()) {
            null
        } else {
            JenkinsPipelineLibraryVersion(version)
        }
    }

    override fun fromStoredJson(
        valueConfig: JenkinsPipelineLibraryIndicatorValueTypeConfig,
        value: JsonNode,
    ): JenkinsPipelineLibraryVersion? =
        value.takeIf { it.isValueNode && !it.isNull }?.asText()?.takeIf { it.isNotBlank() }?.run { JenkinsPipelineLibraryVersion(this) }

    override fun toStoredJson(
        config: JenkinsPipelineLibraryIndicatorValueTypeConfig,
        value: JenkinsPipelineLibraryVersion?,
    ): JsonNode = value?.let { StringNode.valueOf(it.value) } ?: NullNode.instance

    override fun toConfigForm(config: JenkinsPipelineLibraryIndicatorValueTypeConfig): JsonNode =
        config.settings.asJson()

    override fun fromConfigForm(config: JsonNode): JenkinsPipelineLibraryIndicatorValueTypeConfig {
        val settings = config.parse<JenkinsPipelineLibraryIndicatorLibrarySettings>()
        return JenkinsPipelineLibraryIndicatorValueTypeConfig(
            settings = settings,
        )
    }

    override fun toConfigClientJson(config: JenkinsPipelineLibraryIndicatorValueTypeConfig): JsonNode =
        config.asJson()

    override fun toConfigStoredJson(config: JenkinsPipelineLibraryIndicatorValueTypeConfig): JsonNode =
        config.asJson()

    override fun fromConfigStoredJson(config: JsonNode): JenkinsPipelineLibraryIndicatorValueTypeConfig =
        if (config.has(JenkinsPipelineLibraryIndicatorValueTypeConfig::settings.name)) {
            config.parse()
        }
        // Backward compatibility ==> starting from scratch
        else {
            val required = config.getBooleanField("versionRequired") ?: false
            val version = config.getTextField("versionMinimum")
            JenkinsPipelineLibraryIndicatorValueTypeConfig(
                settings = JenkinsPipelineLibraryIndicatorLibrarySettings(
                    library = "n/a",
                    required = required,
                    lastSupported = version,
                )
            )
        }
}