package net.nemerosa.ontrack.extension.recordings.graphql

import graphql.schema.*
import net.nemerosa.ontrack.extension.api.ExtensionManager
import net.nemerosa.ontrack.extension.recordings.Recording
import net.nemerosa.ontrack.extension.recordings.RecordingsExtension
import net.nemerosa.ontrack.extension.recordings.RecordingsQueryService
import net.nemerosa.ontrack.graphql.schema.GQLContributor
import net.nemerosa.ontrack.graphql.schema.GQLRootQueries
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.GraphQLBeanConverter
import net.nemerosa.ontrack.graphql.support.pagination.GQLPaginatedListFactory
import net.nemerosa.ontrack.graphql.support.typedArgument
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseInto
import net.nemerosa.ontrack.model.pagination.PaginatedList
import org.springframework.stereotype.Component

/**
 * Contribution to the GraphQL schema by the recordings extensions.
 */
@Component
class GQLContributorRecordings(
    private val extensionManager: ExtensionManager,
    private val gqlPaginatedListFactory: GQLPaginatedListFactory,
    private val recordingsQueryService: RecordingsQueryService,
) : GQLContributor, GQLRootQueries {

    override fun contribute(cache: GQLTypeCache, dictionary: MutableSet<GraphQLType>): Set<GraphQLType> {
        val set = mutableSetOf<GraphQLType>()
        extensionManager.getExtensions(RecordingsExtension::class.java).forEach { extension ->
            set += extension.contribute(cache, dictionary)
        }
        return set
    }

    override val fieldDefinitions: List<GraphQLFieldDefinition>
        get() = extensionManager.getExtensions(RecordingsExtension::class.java).map { extension ->
            extension.createRootQuery()
        }

    private fun <R : Recording, F : Any> RecordingsExtension<R, F>.createRootQuery(): GraphQLFieldDefinition =
        gqlPaginatedListFactory.createRootPaginatedField(
            cache = GQLTypeCache(),
            fieldName = "${graphQLPrefix.replaceFirstChar { it.lowercase() }}Recordings",
            fieldDescription = "List of recordings for $lowerDisplayName",
            itemType = "${graphQLPrefix}Recording",
            itemPaginatedListProvider = { env, offset, size ->
                getPaginatedList(env, offset, size)
            },
            arguments = listOf(
                typedArgument(ARG_FILTER, "${graphQLPrefix}RecordingFilterInput", "Filter", nullable = true)
            )
        )

    private fun <R : Recording, F : Any> RecordingsExtension<R, F>.getPaginatedList(
        environment: DataFetchingEnvironment,
        offset: Int,
        size: Int
    ): PaginatedList<R> {
        // Parsing of the filter
        val filterJson = environment.getArgument<Any>(ARG_FILTER)?.asJson()
        val filter = filterJson?.run { parseInto(filterType) }
        // Pagination
        return recordingsQueryService.findByFilter(this, filter, offset, size)
    }

    private fun <R : Recording, F : Any> RecordingsExtension<R, F>.contribute(
        cache: GQLTypeCache,
        dictionary: MutableSet<GraphQLType>
    ): Set<GraphQLType> =
        graphQLContributions + setOf(
            // Record
            createRecordType(cache),
            // Filter input
            createFilterInput(dictionary),
        )

    private fun <R : Recording, F : Any> RecordingsExtension<R, F>.createFilterInput(dictionary: MutableSet<GraphQLType>): GraphQLInputObjectType =
        GraphQLInputObjectType.newInputObject()
            .name("${graphQLPrefix}RecordingFilterInput")
            .description("Recording filter input for $lowerDisplayName")
            .fields(GraphQLBeanConverter.asInputFields(filterType, dictionary))
            .build()

    private fun <R : Recording, F : Any> RecordingsExtension<R, F>.createRecordType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name("${graphQLPrefix}Recording")
            .description("Recording for $lowerDisplayName")
            // Common fields
            .fields(GraphQLBeanConverter.asObjectFields(Recording::class, cache))
            // Specific fields
            .fields(graphQLRecordFields(cache))
            // Ok
            .build()

    private val <R : Recording, F : Any> RecordingsExtension<R, F>.lowerDisplayName: String get() = displayName.replaceFirstChar { it.lowercase() }

    companion object {
        private const val ARG_FILTER = "filter"
    }

}