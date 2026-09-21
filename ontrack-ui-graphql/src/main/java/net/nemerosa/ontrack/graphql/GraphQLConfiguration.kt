package net.nemerosa.ontrack.graphql

import graphql.execution.instrumentation.Instrumentation
import graphql.schema.visibility.NoIntrospectionGraphqlFieldVisibility
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.graphql.autoconfigure.GraphQlProperties
import org.springframework.boot.graphql.autoconfigure.GraphQlSourceBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.core.io.support.ResourcePatternResolver
import org.springframework.graphql.execution.DataFetcherExceptionResolver
import org.springframework.graphql.execution.GraphQlSource
import org.springframework.graphql.execution.RuntimeWiringConfigurer
import org.springframework.graphql.execution.SubscriptionExceptionResolver

@Configuration
class GraphQLConfiguration {

    private val logger: Logger = LoggerFactory.getLogger(GraphQLConfiguration::class.java)

    @Bean
    fun graphQlSource(
        resourcePatternResolver: ResourcePatternResolver,
        exceptionResolvers: ObjectProvider<DataFetcherExceptionResolver>,
        subscriptionExceptionResolvers: ObjectProvider<SubscriptionExceptionResolver>,
        instrumentations: ObjectProvider<Instrumentation>,
        wiringConfigurers: ObjectProvider<RuntimeWiringConfigurer>,
        sourceCustomizers: ObjectProvider<GraphQlSourceBuilderCustomizer>,
        properties: GraphQlProperties,
    ): GraphQlSource {
        // Gets the list of schema resources
        val schemaResources: Array<Resource> = getSchemaResources(resourcePatternResolver)

        // Logging
        if (logger.isInfoEnabled) {
            schemaResources.forEach {
                logger.info("GraphQL resource: {}", it)
            }
        }

        // Building a GraphQL source
        val builder = GraphQlSource.schemaResourceBuilder()
            .schemaResources(*schemaResources)
            .exceptionResolvers(exceptionResolvers.orderedStream().toList())
            .subscriptionExceptionResolvers(subscriptionExceptionResolvers.orderedStream().toList())
            .instrumentation(instrumentations.orderedStream().toList())
            .configureRuntimeWiring { wiring ->
                if (!properties.schema.introspection.isEnabled) {
                    wiring.fieldVisibility(NoIntrospectionGraphqlFieldVisibility.NO_INTROSPECTION_FIELD_VISIBILITY)
                }
            }

        wiringConfigurers.orderedStream()
            .forEach { configurer ->
                builder.configureRuntimeWiring(configurer)
            }

        sourceCustomizers.orderedStream()
            .forEach { customizer ->
                customizer.customize(builder)
            }

        return builder.build()
    }

    private fun getSchemaResources(resourcePatternResolver: ResourcePatternResolver): Array<Resource> {
        val listResources = resourcePatternResolver.getResources("classpath*:graphql/**/*.graphqls")

        // Logging
        if (logger.isInfoEnabled) {
            listResources.forEach {
                logger.info("[init] GraphQL resource: {}", it)
            }
        }

        val finalResources = listResources
            .groupBy { it.filename }
            .mapNotNull { (filename, resources) ->
                if (filename != null && resources.isNotEmpty()) {
                    if (resources.size == 1) {
                        resources.first()
                    } else {
                        // Takes the first file based URL
                        resources.firstOrNull { it is FileSystemResource }
                        // ... or the first one (with a warning)
                            ?: resources.first().apply {
                                logger.warn("[init] {} GraphQL resource has two matches but taking the first one: $resources")
                            }
                    }
                } else {
                    null // Not using an unnamed resource
                }
            }

        // Final list
        if (logger.isInfoEnabled) {
            finalResources.forEach {
                logger.info("[final] GraphQL resource: {}", it)
            }
        }

        // OK
        return finalResources.toTypedArray()
    }

}