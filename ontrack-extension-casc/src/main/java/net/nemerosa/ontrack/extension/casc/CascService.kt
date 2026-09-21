package net.nemerosa.ontrack.extension.casc

import tools.jackson.databind.JsonNode

/**
 * Runs some Configuration as Code.
 */
interface CascService {

    /**
     * Runs CasC from a series of YAML texts
     *
     * @param yaml List of YAML content
     */
    fun runYaml(vararg yaml: String) = runYaml(yaml.toList())

    /**
     * Runs CasC from a series of YAML texts
     *
     * @param yaml List of YAML content
     */
    fun runYaml(yaml: List<String>)

    /**
     * Renders the current settings as a YAML content
     */
    fun renderAsYaml(): String

    /**
     * Renders the current settings as a JSON
     */
    fun renderAsJson(): JsonNode
}