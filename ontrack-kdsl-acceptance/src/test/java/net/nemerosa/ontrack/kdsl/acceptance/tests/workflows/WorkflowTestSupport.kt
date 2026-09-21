package net.nemerosa.ontrack.kdsl.acceptance.tests.workflows

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.yaml.Yaml
import kotlin.test.fail

object WorkflowTestSupport {

    /**
     * Utility method to convert a workflow defined as YAML
     * into a JSON object usable in notification workflows
     */
    fun yamlWorkflowToJson(yaml: String): JsonNode =
        Yaml().read(yaml).firstOrNull()
            ?: fail("Cannot parse YAML")

}