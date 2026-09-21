package net.nemerosa.ontrack.extension.jenkins.autoversioning

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse

/**
 * Configuration for the [JenkinsPostProcessing] service.
 *
 * @property dockerImage Docker image defining the environment
 * @property dockerCommand Command to run in the working copy inside the Docker container
 * @property commitMessage Commit message for the post processed files. If not defined, a default message will be provided
 * @property config Jenkins configuration to use for the connection (optional, using defaults if not specified)
 * @property job Path to the job to launch for the post processing (optional, using defaults if not specified)
 * @property parameters List of extra parameters to pass to the job (optional, none by default)
 * @property credentials List of credentials to inject in the command
 */
data class JenkinsPostProcessingConfig(
    val dockerImage: String,
    val dockerCommand: String,
    val commitMessage: String? = null,
    val config: String? = null,
    val job: String? = null,
    val parameters: List<JenkinsPostProcessingConfigParam> = emptyList(),
    val credentials: List<JenkinsPostProcessingConfigCredentials>? = null,
) {

    companion object {
        fun parseJson(config: JsonNode): JenkinsPostProcessingConfig {
            // Switching between short & expanded format for the credentials
            val credentials: JsonNode = config.path(JenkinsPostProcessingConfig::credentials.name)
            return if (credentials.isMissingNode || credentials.isNull || credentials.isArray) {
                // Standard parsing
                config.parse()
            } else if (credentials.isTextual) {
                // Parsing the credentials from text
                val credentialsList = JenkinsPostProcessingConfigCredentials.parseLines(credentials.asText())
                (config as ObjectNode).set(
                    JenkinsPostProcessingConfig::credentials.name,
                    credentialsList.asJson()
                )
                // Parsing
                config.parse()
            } else {
                error("Jenkins auto versioning config credentials format error: ${credentials}")
            }
        }
    }

}

