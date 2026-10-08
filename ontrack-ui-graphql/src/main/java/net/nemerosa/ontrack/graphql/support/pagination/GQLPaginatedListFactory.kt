package net.nemerosa.ontrack.graphql.support.pagination

import graphql.Scalars.GraphQLInt
import graphql.schema.*
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.listType
import net.nemerosa.ontrack.model.pagination.PageRequest
import net.nemerosa.ontrack.model.pagination.PaginatedList
import org.springframework.stereotype.Component

/**
 * Creates a paginated GraphQL list type, linked to an actual
 * [PaginatedList] of objects.
 */
@Component
class GQLPaginatedListFactory(
        private val pageInfo: GQLTypePageInfo
) {

    companion object {
        const val ARG_OFFSET = "offset"
        const val ARG_SIZE = "size"
    }

    /**
     * Creates a paginated field from a [PaginatedList] provider.
     *
     * @param fieldName Name of the field
     * @param fieldDescription Description of the field
     * @param deprecation Deprecation reason
     * @param itemType Type of the items being paginated
     * @param itemTypeSuffix Suffix to add to the item type when creating the paginated list type name
     * @param itemPaginatedListProvider Function to provide the paginated list directly.
     * @param arguments Optional list of arguments to add to the field
     * @param additionalFields Optional list of fields to add, additionally to the page info and the page items.
     *
     * @param P Type of the context (see [DataFetchingEnvironment.getSource])
     * @param T Type of item in the list
     */
    fun <P, T> createPaginatedField(
            cache: GQLTypeCache,
            fieldName: String,
            fieldDescription: String,
            deprecation: String? = null,
            itemType: String,
            itemPaginatedListProvider: (env: DataFetchingEnvironment, source: P, offset: Int, size: Int) -> PaginatedList<T>,
            arguments: List<GraphQLArgument> = emptyList(),
            additionalFields: List<GraphQLFieldDefinition> = emptyList(),
            itemTypeSuffix: String = ""
    ): GraphQLFieldDefinition =
            createBasePaginatedListField(
                cache = cache,
                fieldName = fieldName,
                fieldDescription = fieldDescription,
                deprecation = deprecation,
                itemType = itemType,
                itemTypeSuffix = itemTypeSuffix,
                arguments = arguments,
                additionalFields = additionalFields,
            ).dataFetcher { environment ->
                val source: P = environment.getSource()!!
                val offset = environment.getArgument<Int>(ARG_OFFSET) ?: 0
                val size = environment.getArgument<Int>(ARG_SIZE) ?: PageRequest.DEFAULT_PAGE_SIZE
                itemPaginatedListProvider(
                        environment,
                        source,
                        offset,
                        size
                )
            }.build()

    /**
     * Creates a paginated field from a [PaginatedList] provider.
     *
     * Suitable for root queries (non source available)
     *
     * @param fieldName Name of the field
     * @param fieldDescription Description of the field
     * @param deprecation Deprecation reason
     * @param itemType Type of the items being paginated
     * @param itemPaginatedListProvider Function to provide the paginated list directly.
     * @param arguments Optional list of arguments to add to the field
     * @param additionalFields Optional list of fields to add, additionally to the page info and the page items.
     * @param itemTypeSuffix Suffix to add to the item type when creating the paginated list type name, for
     * a list of the same items whose type must not clash with another one
     *
     * @param T Type of item in the list
     */
    fun <T> createRootPaginatedField(
            cache: GQLTypeCache,
            fieldName: String,
            fieldDescription: String,
            deprecation: String? = null,
            itemType: String,
            itemPaginatedListProvider: (env: DataFetchingEnvironment, offset: Int, size: Int) -> PaginatedList<T>,
            arguments: List<GraphQLArgument> = emptyList(),
            additionalFields: List<GraphQLFieldDefinition> = emptyList(),
            itemTypeSuffix: String = "",
    ): GraphQLFieldDefinition =
            createBasePaginatedListField(
                    cache,
                    fieldName,
                    fieldDescription,
                    deprecation,
                    itemType,
                    arguments,
                    additionalFields,
                    itemTypeSuffix,
            ).dataFetcher { environment ->
                val offset = environment.getArgument<Int>(ARG_OFFSET) ?: 0
                val size = environment.getArgument<Int>(ARG_SIZE) ?: PageRequest.DEFAULT_PAGE_SIZE
                itemPaginatedListProvider(
                        environment,
                        offset,
                        size
                )
            }.build()

    /**
     * Creates a paginated field from a counter and list provider.
     *
     * @param fieldName Name of the field
     * @param fieldDescription Description of the field
     * @param deprecation Deprecation reason
     * @param itemType Type of the items being paginated
     * @param itemListCounter Function to provide the _total_ number of items, regardless of pagination
     * @param itemListProvider Function to provide a list of items restricted by pagination
     * @param arguments Optional list of arguments to add to the field
     *
     * @param P Type of the context (see [DataFetchingEnvironment.getSource])
     * @param T Type of item in the list
     */
    fun <P, T> createPaginatedField(
            cache: GQLTypeCache,
            fieldName: String,
            fieldDescription: String,
            deprecation: String? = null,
            itemType: String,
            itemListCounter: (DataFetchingEnvironment, P) -> Int,
            itemListProvider: (DataFetchingEnvironment, P, Int, Int) -> List<T>,
            arguments: List<GraphQLArgument> = emptyList()
    ): GraphQLFieldDefinition =
            createBasePaginatedListField(
                    cache, fieldName, fieldDescription, deprecation, itemType, arguments
            ).dataFetcher { environment ->
                val source: P = environment.getSource()!!
                val offset = environment.getArgument<Int>(ARG_OFFSET) ?: 0
                val size = environment.getArgument<Int>(ARG_SIZE) ?: PageRequest.DEFAULT_PAGE_SIZE
                val total = itemListCounter(environment, source)
                val items = itemListProvider(
                        environment,
                        source,
                        offset,
                        size
                )
                PaginatedList.create(
                        items = items,
                        offset = offset,
                        pageSize = size,
                        total = total)
            }.build()

    /**
     * Creates a root paginated field from a counter and list provider.
     *
     * @param fieldName Name of the field
     * @param fieldDescription Description of the field
     * @param deprecation Deprecation reason
     * @param itemType Type of the items being paginated
     * @param itemListCounter Function to provide the _total_ number of items, regardless of pagination
     * @param itemListProvider Function to provide a list of items restricted by pagination
     * @param arguments Optional list of arguments to add to the field
     *
     * @param T Type of item in the list
     */
    fun <T> createRootPaginatedField(
            cache: GQLTypeCache,
            fieldName: String,
            fieldDescription: String,
            deprecation: String? = null,
            itemType: String,
            itemListCounter: (DataFetchingEnvironment) -> Int,
            itemListProvider: (DataFetchingEnvironment, Int, Int) -> List<T>,
            arguments: List<GraphQLArgument> = emptyList()
    ): GraphQLFieldDefinition =
            createBasePaginatedListField(
                    cache, fieldName, fieldDescription, deprecation, itemType, arguments
            ).dataFetcher { environment ->
                val offset = environment.getArgument<Int>(ARG_OFFSET) ?: 0
                val size = environment.getArgument<Int>(ARG_SIZE) ?: PageRequest.DEFAULT_PAGE_SIZE
                val total = itemListCounter(environment)
                val items = itemListProvider(
                        environment,
                        offset,
                        size
                )
                PaginatedList.create(
                        items = items,
                        offset = offset,
                        pageSize = size,
                        total = total)
            }.build()

    private fun createBasePaginatedListField(
            cache: GQLTypeCache,
            fieldName: String,
            fieldDescription: String,
            deprecation: String?,
            itemType: String,
            arguments: List<GraphQLArgument>,
            additionalFields: List<GraphQLFieldDefinition> = emptyList(),
            itemTypeSuffix: String = "",
    ): GraphQLFieldDefinition.Builder =
            GraphQLFieldDefinition.newFieldDefinition()
                    .name(fieldName)
                    .description(fieldDescription)
                    .deprecate(deprecation)
                    .argument {
                        it.name(ARG_OFFSET)
                                .description("Offset for the page")
                                .type(GraphQLInt)
                                .defaultValue(0)
                    }
                    .argument {
                        it.name(ARG_SIZE)
                                .description("Size of the page")
                                .type(GraphQLInt)
                                .defaultValue(PageRequest.DEFAULT_PAGE_SIZE)
                    }
                    .arguments(arguments)
                    .type(createPaginatedList(cache, itemType, itemTypeSuffix = itemTypeSuffix, additionalFields = additionalFields))

    /**
     * Creates a paginated GraphQL list type, linked to an actual
     * [PaginatedList] of objects.
     *
     * @param itemType Type of item in the list
     */
    private fun createPaginatedList(
            cache: GQLTypeCache,
            itemType: String,
            itemTypeSuffix: String = "",
            additionalFields: List<GraphQLFieldDefinition> = emptyList(),
    ): GraphQLObjectType {
        val paginatedListTypeName = "${itemType}${itemTypeSuffix}Paginated"
        return cache.getOrCreate(
                paginatedListTypeName
        ) {
            GraphQLObjectType.newObject()
                    .name(paginatedListTypeName)
                    .field {
                        it.name("pageInfo")
                                .description("Information about the current page")
                                .type(pageInfo.typeRef)
                    }
                    .field {
                        it.name("pageItems")
                                .description("Items in the current page")
                                .type(listType(GraphQLTypeReference(itemType)))
                    }
                    .fields(additionalFields)
                    .build()
        }
    }
}