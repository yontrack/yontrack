package net.nemerosa.ontrack.graphql.schema.configurations

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode
import net.nemerosa.ontrack.graphql.schema.Mutation
import net.nemerosa.ontrack.graphql.support.TypedMutationProvider
import net.nemerosa.ontrack.json.parseInto
import net.nemerosa.ontrack.model.support.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ConfigurationsMutations(
    private val configurationServiceFactory: ConfigurationServiceFactory,
    private val ontrackConfigProperties: OntrackConfigProperties,
) : TypedMutationProvider() {

    private val logger: Logger = LoggerFactory.getLogger(ConfigurationsMutations::class.java)

    override val mutations: List<Mutation> = listOf(
        simpleMutation(
            name = "testConfiguration",
            description = "Tests a configuration",
            input = TestConfigurationInput::class,
            outputName = "connectionResult",
            outputDescription = "Result of the test",
            outputType = ConnectionResult::class,
        ) { input ->
            val service = configurationServiceFactory.findConfigurationService(input.type)
            if (service == null) {
                ConnectionResult.error("Cannot find configuration type: ${input.type}")
            } else if (input.data is ObjectNode) {
                parseAndTest(service, input.name, input.data)
            } else {
                ConnectionResult.error("Expected an object as data")
            }
        },
        unitMutation<CreateConfigurationInput>(
            name = "createConfiguration",
            description = "Creates a configuration",
        ) { input ->
            val service = configurationServiceFactory.getConfigurationService(input.type)
            parseAndCreate(service, input.name, input.data as ObjectNode)
        },
        unitMutation<UpdateConfigurationInput>(
            name = "updateConfiguration",
            description = "Updates a configuration",
        ) { input ->
            val service = configurationServiceFactory.getConfigurationService(input.type)
            parseAndUpdate(service, input.name, input.data as ObjectNode)
        },
        unitMutation<DeleteConfigurationInput>(
            name = "deleteConfiguration",
            description = "Deletes a configuration",
        ) { input ->
            configurationServiceFactory
                .findConfigurationService(input.type)
                ?.deleteConfiguration(input.name)
        },
    )

    private fun <T : Configuration<T>> parse(
        service: ConfigurationService<T>,
        name: String,
        data: ObjectNode,
    ): T {
        data.put("name", name)
        return data.parseInto(service.configurationType.kotlin)
    }

    private fun <T : Configuration<T>> parseAndTest(
        service: ConfigurationService<T>,
        name: String,
        data: ObjectNode,
    ): ConnectionResult {
        val config = parse(service, name, data)
        return if (ontrackConfigProperties.configurationTest) {
            service.test(config)
        } else {
            logger.warn("Testing configurations is disabled.")
            ConnectionResult.ok()
        }
    }

    private fun <T : Configuration<T>> parseAndCreate(
        service: ConfigurationService<T>,
        name: String,
        data: ObjectNode,
    ) {
        val config = parse(service, name, data)
        service.newConfiguration(config)
    }

    private fun <T : Configuration<T>> parseAndUpdate(
        service: ConfigurationService<T>,
        name: String,
        data: ObjectNode,
    ) {
        val config = parse(service, name, data)
        service.updateConfiguration(name, config)
    }

    private data class TestConfigurationInput(
        val type: String,
        val name: String,
        val data: JsonNode,
    )

    private data class CreateConfigurationInput(
        val type: String,
        val name: String,
        val data: JsonNode,
    )

    private data class UpdateConfigurationInput(
        val type: String,
        val name: String,
        val data: JsonNode,
    )

    private data class DeleteConfigurationInput(
        val type: String,
        val name: String,
    )

}