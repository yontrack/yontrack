package net.nemerosa.ontrack.extension.scm.catalog.api

import graphql.Scalars.GraphQLBoolean
import graphql.Scalars.GraphQLString
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.extension.scm.catalog.*
import net.nemerosa.ontrack.graphql.schema.GQLRootQuery
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.pagination.GQLPaginatedListFactory
import net.nemerosa.ontrack.json.JDKLocalDateDeserializer
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class GQLRootQuerySCMCatalog(
        private val paginatedListFactory: GQLPaginatedListFactory,
        private val scmCatalogEntry: GQLTypeSCMCatalogProjectEntry,
        private val scmCatalogFilterService: SCMCatalogFilterService
) : GQLRootQuery {
    override fun getFieldDefinition(): GraphQLFieldDefinition =
            paginatedListFactory.createRootPaginatedField(
                    cache = GQLTypeCache(),
                    fieldName = "scmCatalog",
                    fieldDescription = "List of SCM catalog entries and/or orphan projects",
                    itemType = scmCatalogEntry.typeName,
                    itemListCounter = { env, -> loadSCMCatalogEntries(env, 0, Int.MAX_VALUE).size },
                    itemListProvider = { env, offset, size -> loadSCMCatalogEntries(env, offset, size) },
                    arguments = listOf(
                            GraphQLArgument.newArgument().name(ARG_SCM)
                                    .description("Filters on SCM type (exact match)")
                                    .type(GraphQLString)
                                    .build(),
                            GraphQLArgument.newArgument().name(ARG_CONFIG)
                                    .description("Filters on SCM config name (exact match)")
                                    .type(GraphQLString)
                                    .build(),
                            GraphQLArgument.newArgument().name(ARG_REPOSITORY)
                                    .description("Filters on repository (regular expression)")
                                    .type(GraphQLString)
                                    .build(),
                            GraphQLArgument.newArgument().name(ARG_LINK)
                                    .description("Filters on entries which are linked or not to projects (ALL, ENTRY, LINKED, UNLINKED, ORPHAN)")
                                    .type(GraphQLString)
                                    .build(),
                            GraphQLArgument.newArgument().name(ARG_PROJECT)
                                    .description("Filters on the name of the orphan projects")
                                    .type(GraphQLString)
                                    .build(),
                            GraphQLArgument.newArgument().name(ARG_SORT_ON)
                                    .description("Property to sort on. Supported: ${SCMCatalogProjectFilterSort.entries.joinToString(", ")}")
                                    .type(GraphQLString)
                                    .build(),
                            GraphQLArgument.newArgument().name(ARG_SORT_ASCENDING)
                                    .description("Sorting direction.")
                                    .type(GraphQLBoolean)
                                    .build(),
                            GraphQLArgument.newArgument().name(ARG_BEFORE_LAST_ACTIVITY)
                                    .description("Selects entries whose last activity is before this date.")
                                    .type(GraphQLString)
                                    .build(),
                            GraphQLArgument.newArgument().name(ARG_AFTER_LAST_ACTIVITY)
                                    .description("Selects entries whose last activity is after this date.")
                                    .type(GraphQLString)
                                    .build(),
                            GraphQLArgument.newArgument().name(ARG_BEFORE_CREATED_AT)
                                    .description("Selects entries whose creation date is before this date.")
                                    .type(GraphQLString)
                                    .build(),
                            GraphQLArgument.newArgument().name(ARG_AFTER_CREATED_AT)
                                    .description("Selects entries whose creation date is after this date.")
                                    .type(GraphQLString)
                                    .build(),
                            GraphQLArgument.newArgument().name(ARG_TEAM)
                                    .description("ID of the SCM team. Empty string to select entries without a team.")
                                    .type(GraphQLString)
                                    .build()
                    )
            )

    private fun loadSCMCatalogEntries(env: DataFetchingEnvironment, offset: Int, size: Int): List<SCMCatalogEntryOrProject> {
        val scm: String? = env.getArgument(ARG_SCM)
        val config: String? = env.getArgument(ARG_CONFIG)
        val repository: String? = env.getArgument<String>(ARG_REPOSITORY)
        val link: SCMCatalogProjectFilterLink = env.getArgument<String>(ARG_LINK)
                ?.run { SCMCatalogProjectFilterLink.valueOf(this) }
                ?: SCMCatalogProjectFilterLink.ALL
        val project: String? = env.getArgument<String>(ARG_PROJECT)
        val beforeLastActivity: LocalDate? = env.getArgument<String>(ARG_BEFORE_LAST_ACTIVITY)?.run { JDKLocalDateDeserializer.parse(this) }
        val afterLastActivity: LocalDate? = env.getArgument<String>(ARG_AFTER_LAST_ACTIVITY)?.run { JDKLocalDateDeserializer.parse(this) }
        val beforeCreatedAt: LocalDate? = env.getArgument<String>(ARG_BEFORE_CREATED_AT)?.run { JDKLocalDateDeserializer.parse(this) }
        val afterCreatedAt: LocalDate? = env.getArgument<String>(ARG_AFTER_CREATED_AT)?.run { JDKLocalDateDeserializer.parse(this) }
        val team: String? = env.getArgument(ARG_TEAM)
        val sortOn: SCMCatalogProjectFilterSort? = env.getArgument<String>(ARG_SORT_ON)?.run { SCMCatalogProjectFilterSort.valueOf(this) }
        val sortAscending: Boolean = env.getArgument(ARG_SORT_ASCENDING) ?: true
        val filter = SCMCatalogProjectFilter(
                offset = offset,
                size = size,
                scm = scm,
                config = config,
                repository = repository,
                link = link,
                project = project,
                beforeLastActivity = beforeLastActivity,
                afterLastActivity = afterLastActivity,
                beforeCreatedAt = beforeCreatedAt,
                afterCreatedAt = afterCreatedAt,
                team = team,
                sortOn = sortOn,
                sortAscending = sortAscending
        )
        return scmCatalogFilterService.findCatalogProjectEntries(filter)
    }

    companion object {
        private const val ARG_SCM = "scm"
        private const val ARG_CONFIG = "config"
        private const val ARG_REPOSITORY = "repository"
        private const val ARG_LINK = "link"
        private const val ARG_PROJECT = "project"
        private const val ARG_SORT_ON = "sortOn"
        private const val ARG_SORT_ASCENDING = "sortAscending"
        private const val ARG_BEFORE_LAST_ACTIVITY = "beforeLastActivity"
        private const val ARG_AFTER_LAST_ACTIVITY = "afterLastActivity"
        private const val ARG_BEFORE_CREATED_AT = "beforeCreatedAt"
        private const val ARG_AFTER_CREATED_AT = "afterCreatedAt"
        private const val ARG_TEAM = "team"
    }

}