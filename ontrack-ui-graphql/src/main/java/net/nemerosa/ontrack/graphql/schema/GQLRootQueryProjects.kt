package net.nemerosa.ontrack.graphql.schema

import graphql.Scalars.*
import graphql.schema.DataFetcher
import graphql.schema.GraphQLArgument.newArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import graphql.schema.GraphQLList
import net.nemerosa.ontrack.common.and
import net.nemerosa.ontrack.graphql.support.checkArgList
import net.nemerosa.ontrack.graphql.support.listType
import net.nemerosa.ontrack.model.labels.ProjectLabelManagementService
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.ProjectFavouriteService
import net.nemerosa.ontrack.model.structure.StructureService
import org.apache.commons.lang3.StringUtils
import org.springframework.stereotype.Component

@Component
class GQLRootQueryProjects(
        private val structureService: StructureService,
        private val project: GQLTypeProject,
        private val propertyFilter: GQLInputPropertyFilter,
        private val projectLabelManagementService: ProjectLabelManagementService,
        private val projectFavouriteService: ProjectFavouriteService
) : GQLRootQuery {

    override fun getFieldDefinition(): GraphQLFieldDefinition {
        return newFieldDefinition()
                .name("projects")
                .type(listType(project.typeRef, nullable = true))
                .argument(
                        newArgument()
                                .name(ARG_ID)
                                .description("ID of the project to look for")
                                .type(GraphQLInt)
                                .build()
                )
                .argument(
                        newArgument()
                                .name(ARG_NAME)
                                .description("Name of the project to look for")
                                .type(GraphQLString)
                                .build()
                )
                .argument(
                        newArgument()
                                .name(ARG_NAME_PATTERN)
                                .description("Part of the name of the project to look for")
                                .type(GraphQLString)
                                .build()
                )
                .argument { a ->
                    a.name(ARG_FAVOURITES)
                            .description("Favourite projects only")
                            .type(GraphQLBoolean)
                }
                .argument { a ->
                    a.name(ARG_LABELS)
                            .description("List of labels the project must have")
                            .type(GraphQLList(GraphQLString))
                }
                .argument(propertyFilter.asArgument())
                .dataFetcher(projectFetcher())
                .build()
    }

    private fun projectFetcher(): DataFetcher<List<Project>> {
        return DataFetcher { environment ->
            val id: Int? = environment.getArgument(ARG_ID)
            val name: String? = environment.getArgument(ARG_NAME)
            val namePattern: String? = environment.getArgument(ARG_NAME_PATTERN)
            val favourites = environment.getArgument<Boolean>(ARG_FAVOURITES) ?: false
            val labels: List<String>? = environment.getArgument<List<String>>(ARG_LABELS)
            // Per ID
            when {
                id != null -> {
                    // No other argument is expected
                    checkArgList(environment, ARG_ID)
                    // Fetch by ID
                    val project = structureService.getProject(ID.of(id))
                    // As list
                    return@DataFetcher listOf(project)
                }
                !name.isNullOrBlank() -> {
                    // No other argument is expected
                    checkArgList(environment, ARG_NAME)
                    return@DataFetcher structureService
                            .findProjectByNameIfAuthorized(name)
                            ?.let { listOf(it) }
                            ?: emptyList()
                }
                favourites -> {
                    // No other argument is expected
                    checkArgList(environment, ARG_FAVOURITES)
                    return@DataFetcher projectFavouriteService.getFavouriteProjects()
                }
                !namePattern.isNullOrBlank() -> {
                    // No other argument is expected
                    checkArgList(environment, ARG_NAME_PATTERN)
                    return@DataFetcher structureService.findProjectsByNamePattern(namePattern)
                }
                else -> {
                    // Filter to use
                    var filter: (Project) -> Boolean = { _ -> true }
                    // Property filter?
                    val propertyFilterArg: Map<String, *>? = environment.getArgument(GQLInputPropertyFilter.ARGUMENT_NAME)
                    if (propertyFilterArg != null) {
                        val filterObject = propertyFilter.convert(propertyFilterArg)
                        if (filterObject != null && StringUtils.isNotBlank(filterObject.type)) {
                            val propertyPredicate = propertyFilter.getFilter(filterObject)
                            filter = filter and { propertyPredicate.test(it) }
                        }
                    }
                    // Labels
                    if (labels != null) {
                        filter = filter and { project ->
                            val projectLabels = projectLabelManagementService.getLabelsForProject(project).map {
                                it.getDisplay()
                            }
                            // OK if ALL labels mentioned in the filter are in the list of the labels for the project
                            projectLabels.containsAll(labels)
                        }
                    }
                    // Whole list
                    return@DataFetcher structureService.projectList.filter(filter)
                }
            }
        }
    }

    companion object {
        const val ARG_ID = "id"
        const val ARG_NAME = "name"
        const val ARG_NAME_PATTERN = "pattern"
        const val ARG_FAVOURITES = "favourites"
        const val ARG_LABELS = "labels"
    }
}
