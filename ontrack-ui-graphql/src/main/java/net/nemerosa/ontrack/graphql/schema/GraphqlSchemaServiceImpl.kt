package net.nemerosa.ontrack.graphql.schema

import graphql.schema.*
import graphql.schema.GraphQLObjectType.newObject
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.TypeDefinitionRegistry
import net.nemerosa.ontrack.common.UserException
import net.nemerosa.ontrack.graphql.support.GQLScalarJSON
import net.nemerosa.ontrack.graphql.support.MutationInputValidationException
import org.springframework.graphql.execution.RuntimeWiringConfigurer
import org.springframework.stereotype.Service

@Service
class GraphqlSchemaServiceImpl(
        private val types: List<GQLType>,
        private val inputTypes: List<GQLInputType<*>>,
        private val interfaces: List<GQLInterface>,
        private val enums: List<GQLEnum>,
        private val rootQueries: List<GQLRootQuery>,
        private val rootQueriesPlus: List<GQLRootQueries>,
        private val mutationProviders: List<MutationProvider>,
        private val contributors: List<GQLContributor>,
) : GraphqlSchemaService, RuntimeWiringConfigurer {

    override fun configure(builder: RuntimeWiring.Builder) {
        builder.scalar(GQLScalarJSON.TYPE)
    }

    override fun createSchema(typeDefinitionRegistry: TypeDefinitionRegistry, runtimeWiring: RuntimeWiring): GraphQLSchema {

        // IDL schema
        val idlSchema = SchemaGenerator().makeExecutableSchema(typeDefinitionRegistry, runtimeWiring)

        val queryType = idlSchema.queryType
        val mutationType = idlSchema.mutationType

        // All types
        val cache = GQLTypeCache()
        val dictionary = mutableSetOf<GraphQLType>()
        dictionary.addAll(types.map { it.createType(cache) })
        dictionary.addAll(interfaces.map { it.createInterface() })
        dictionary.addAll(enums.map { it.createEnum() })
        dictionary.addAll(inputTypes.map { it.createInputType(dictionary) })
        dictionary.addAll(contributors.flatMap { it.contribute(cache, dictionary) })

        // Merging the computed schema & the IDL one

        return idlSchema.transform { builder ->
            // Root queries
            builder.query(
                    queryType.transform { qb ->
                        qb.fields(
                                rootQueries.map { it.getFieldDefinition() }
                        )
                        qb.fields(
                                rootQueriesPlus.flatMap { it.fieldDefinitions }
                        )
                    }
            )
            //  Mutations
            builder.mutation(
                    mutationType.transform { mb ->
                        mb.fields(
                                mutationProviders.flatMap { provider ->
                                    provider.mutations.map { mutation ->
                                        createMutation(mutation, dictionary)
                                    }
                                }
                        )
                    }
            )
            // Adds all types in the cache
            builder.additionalTypes(cache.types.toSet())
            // Adds all types in the dictionary
            builder.additionalTypes(dictionary)
        }
    }

    private fun createMutation(mutation: Mutation, dictionary: MutableSet<GraphQLType>): GraphQLFieldDefinition {
        // Create the mutation input type
        val inputType = createMutationInputType(mutation, dictionary)
        // Create the mutation output type
        val outputType = createMutationOutputType(mutation, dictionary)
        // Create the mutation field
        return GraphQLFieldDefinition.newFieldDefinition()
                .name(mutation.name)
                .description(mutation.description)
                .apply {
                    mutation.deprecation?.let { deprecate(it) }
                }
                .apply {
                    if (inputType != null) {
                        argument {
                            it.name("input")
                                    .description("Input for the mutation")
                                    .type(inputType)
                        }
                    }
                }
                .type(outputType)
                .dataFetcher { env -> mutationFetcher(mutation, env) }
                .build()
    }

    private fun mutationFetcher(mutation: Mutation, env: DataFetchingEnvironment): Any {
        return try {
            mutation.fetch(env)
        } catch (ex: Exception) {
            when (ex) {
                is MutationInputValidationException -> {
                    mapOf("errors" to ex.violations.map { cv ->
                        MutationInputValidationException.asUserError(cv)
                    })
                }

                is UserException -> {
                    val exception = ex::class.java.name
                    val error = UserError(
                            message = ex.message ?: exception,
                            exception = exception
                    )
                    mapOf("errors" to listOf(error))
                }

                else -> {
                    throw ex
                }
            }
        }
    }

    private fun createMutationOutputType(mutation: Mutation, dictionary: MutableSet<GraphQLType>): GraphQLObjectType =
            newObject()
                    .name("${mutation.typePrefix}Payload")
                    .description("Output type for the ${mutation.name} mutation.")
                    .fields(mutation.outputFields)
                    // All mutation payloads inherit from the Payload type
                    .withInterface(GraphQLTypeReference(GQLInterfacePayload.PAYLOAD))
                    // Errors field
                    .field(GQLInterfacePayload.payloadErrorsField())
                    // OK
                    .build()
                    .apply { dictionary.add(this) }

    private fun createMutationInputType(mutation: Mutation, dictionary: MutableSet<GraphQLType>): GraphQLInputType? =
            mutation.inputFields(dictionary)
                    .takeIf { it.isNotEmpty() }
                    ?.let { fields ->
                        GraphQLInputObjectType.newInputObject()
                                .name("${mutation.typePrefix}Input")
                                .description("Input type for the ${mutation.name} mutation.")
                                .fields(fields)
                                .build()
                                .apply { dictionary.add(this) }
                    }

    private val Mutation.typePrefix: String get() = name.replaceFirstChar { it.titlecase() }

    companion object {
        const val QUERY = "Query"
        const val MUTATION = "Mutation"
    }

}
