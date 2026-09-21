package net.nemerosa.ontrack.extension.workflows.schema

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.workflows.definition.Workflow
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.json.schema.AbstractJsonSchemaProvider
import net.nemerosa.ontrack.model.json.schema.JsonSchemaBuilderService
import net.nemerosa.ontrack.model.support.EnvService
import org.springframework.stereotype.Service

@Service
class WorkflowSchemaServiceImpl(
    envService: EnvService,
    private val jsonSchemaBuilderService: JsonSchemaBuilderService,
) : AbstractJsonSchemaProvider(envService), WorkflowSchemaService {

    override val key: String = "workflow"
    override val title: String = "Yontrack workflow"
    override val description: String = "Workflow definition for Yontrack"

    override fun createJsonSchema(): JsonNode =
        jsonSchemaBuilderService.createSchema(
            ref = "workflow",
            id = id,
            title = title,
            description = description,
            root = Workflow::class,
        ).asJson()

}