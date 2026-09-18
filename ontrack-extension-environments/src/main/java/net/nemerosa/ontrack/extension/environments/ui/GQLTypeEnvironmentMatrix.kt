package net.nemerosa.ontrack.extension.environments.ui

import graphql.Scalars.GraphQLBoolean
import graphql.Scalars.GraphQLInt
import graphql.Scalars.GraphQLString
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.extension.environments.EnvironmentMatrix
import net.nemerosa.ontrack.extension.environments.EnvironmentMatrixProject
import net.nemerosa.ontrack.extension.environments.EnvironmentMatrixRow
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.schema.GQLTypeProject
import net.nemerosa.ontrack.graphql.support.listType
import net.nemerosa.ontrack.graphql.support.toNotNull
import org.springframework.stereotype.Component

/**
 * One page of the project x environment matrix.
 *
 * The paging fields are flat - `totalProjects`, `offset`, `size` - rather than the usual `pageInfo`
 * wrapper, because the matrix pages *projects* while its items are rows and columns: a
 * `PaginatedList` of rows would name the wrong thing, and the screen's pager is an antd table
 * pagination which wants exactly these three numbers.
 */
@Component
class GQLTypeEnvironmentMatrix(
    private val gqlTypeEnvironment: GQLTypeEnvironment,
    private val gqlTypeEnvironmentMatrixProject: GQLTypeEnvironmentMatrixProject,
) : GQLType {

    override fun getTypeName(): String = EnvironmentMatrix::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description("One page of the project x environment matrix")
            .field {
                it.name(EnvironmentMatrix::environments.name)
                    .description(
                        "Columns of the matrix, by environment order. Only the environments having " +
                                "at least one slot among the rows of this page."
                    )
                    .type(listType(gqlTypeEnvironment.typeRef))
            }
            .field {
                it.name(EnvironmentMatrix::projects.name)
                    .description("Rows of the matrix, by project name")
                    .type(listType(gqlTypeEnvironmentMatrixProject.typeRef))
            }
            .field {
                it.name(EnvironmentMatrix::totalProjects.name)
                    .description(
                        "Total number of projects matching the filter, before per-project " +
                                "visibility is applied"
                    )
                    .type(GraphQLInt.toNotNull())
            }
            .field {
                it.name(EnvironmentMatrix::offset.name)
                    .description("Index of the first project of this page")
                    .type(GraphQLInt.toNotNull())
            }
            .field {
                it.name(EnvironmentMatrix::size.name)
                    .description("Number of projects asked for in this page")
                    .type(GraphQLInt.toNotNull())
            }
            .field {
                it.name(EnvironmentMatrix::hasFavourites.name)
                    .description(
                        "Does the current user have any favourite project at all? The matrix opens " +
                                "on Favourites, and on All for a user who has never starred anything."
                    )
                    .type(GraphQLBoolean.toNotNull())
            }
            .build()
}

/**
 * One project of the matrix, with one row per qualifier.
 */
@Component
class GQLTypeEnvironmentMatrixProject(
    private val gqlTypeEnvironmentMatrixRow: GQLTypeEnvironmentMatrixRow,
) : GQLType {

    override fun getTypeName(): String = EnvironmentMatrixProject::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description("One project of the matrix, with one row per qualifier")
            .field {
                it.name(EnvironmentMatrixProject::project.name)
                    .description("The project")
                    .type(GraphQLTypeReference(GQLTypeProject.PROJECT).toNotNull())
            }
            .field {
                it.name(EnvironmentMatrixProject::rows.name)
                    .description(
                        "Rows for this project, the default (empty) qualifier first. A project with " +
                                "only the default qualifier has exactly one row, which is the project's own row."
                    )
                    .type(listType(gqlTypeEnvironmentMatrixRow.typeRef))
            }
            .build()
}

/**
 * One row of the matrix: a project and a qualifier, across the environments.
 */
@Component
class GQLTypeEnvironmentMatrixRow(
    private val gqlTypeSlot: GQLTypeSlot,
) : GQLType {

    override fun getTypeName(): String = EnvironmentMatrixRow::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description("One row of the matrix: a project and a qualifier, across the environments")
            .field {
                it.name(EnvironmentMatrixRow::qualifier.name)
                    .description("Qualifier of this row, the empty string being the default one")
                    .type(GraphQLString.toNotNull())
            }
            .field {
                it.name(EnvironmentMatrixRow::slots.name)
                    .description(
                        "Slots of this row, by environment order. Only the slots which exist: an " +
                                "environment with no slot for this project and qualifier is simply absent."
                    )
                    .type(listType(gqlTypeSlot.typeRef))
            }
            .build()
}
