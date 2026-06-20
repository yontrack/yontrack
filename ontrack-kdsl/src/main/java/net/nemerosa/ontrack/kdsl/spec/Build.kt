package net.nemerosa.ontrack.kdsl.spec

import com.apollographql.apollo.api.Optional
import net.nemerosa.ontrack.kdsl.connector.Connector
import net.nemerosa.ontrack.kdsl.connector.graphql.GraphQLMissingDataException
import net.nemerosa.ontrack.kdsl.connector.graphql.checkData
import net.nemerosa.ontrack.kdsl.connector.graphql.convert
import net.nemerosa.ontrack.kdsl.connector.graphql.paginate
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.*
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.LinksBuildInputItem
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.ProjectEntityType
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.PromotionRunFieldValueInput
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector
import net.nemerosa.ontrack.kdsl.connector.support.PaginatedList
import net.nemerosa.ontrack.kdsl.connector.support.emptyPaginatedList
import java.time.LocalDateTime

/**
 * Representation of a build.
 *
 * @property connector Ontrack connector
 * @property branch Parent branch
 * @property id Build ID
 * @property name Build name
 * @property description Build description
 */
class Build(
    connector: Connector,
    val branch: Branch,
    id: UInt,
    val name: String,
    val description: String?,
) : ProjectEntity(connector, ProjectEntityType.BUILD, id) {

    /**
     * Creates a promotion run for a build.
     *
     * @param promotion Name of the promotion level
     * @param description Description of the run
     * @return Promotion run
     */
    fun promote(
        promotion: String,
        description: String = "",
        dateTime: LocalDateTime? = null,
        fieldValues: List<PromotionRunFieldValueInput> = emptyList(),
    ): PromotionRun = graphqlConnector.mutate(
        CreatePromotionRunMutation(
            id.toInt(),
            promotion,
            description,
            Optional.presentIfNotNull(dateTime),
            Optional.presentIfNotNull(fieldValues.ifEmpty { null }),
        )
    ) {
        it?.createPromotionRunById?.payloadUserErrors?.convert()
    }
        ?.checkData { it.createPromotionRunById?.promotionRun }
        ?.promotionRunFragment?.toPromotionRun(this)
        ?: throw GraphQLMissingDataException("Did not get back the created promotion run")

    /**
     * Gets the list of validation runs for this build and a given validation stamp name (can be a regular expression).
     *
     * @param validationStamp Validation stamp name (can be a regular expression).
     * @param count Maximum number of runs to return
     */
    fun getValidationRuns(
        validationStamp: String,
        count: Int = 50,
    ): List<ValidationRun> =
        graphqlConnector.query(
            BuildValidationRunsQuery(id.toInt(), validationStamp, Optional.present(count))
        )?.builds?.firstOrNull()?.validationRuns?.map {
            it.validationRunFragment.toValidationRun(this)
        } ?: emptyList()

    /**
     * Gets the list of promotions run for this build and a given promotion level name
     */
    fun getPromotionRunsForPromotionLevel(
        promotionLevel: String,
    ): List<PromotionRun> =
        graphqlConnector.query(
            BuildPromotionRunsQuery(id.toInt(), promotionLevel)
        )?.builds?.firstOrNull()?.promotionRuns?.map {
            it.promotionRunFragment.toPromotionRun(this@Build)
        } ?: emptyList()

    /**
     * Gets the list of builds used by _this_ build.
     */
    fun getLinksUsing(
        offset: Int = 0,
        size: Int = 10,
    ): PaginatedList<Build> = graphqlConnector.query(
        BuildUsingQuery(id.toInt(), Optional.present(offset), Optional.present(size))
    )?.paginate(
        pageInfo = { it.builds?.firstOrNull()?.usingQualified?.pageInfo?.pageInfoContent },
        pageItems = { it.builds?.firstOrNull()?.usingQualified?.pageItems }
    )?.map {
        it.build.buildFragment.toBuild(this@Build)
    } ?: emptyPaginatedList()

    /**
     * Links this build to another build
     */
    fun linkTo(build: Build) {
        graphqlConnector.mutate(
            LinksBuildMutation(
                branch.project.name,
                name,
                listOf(
                    LinksBuildInputItem(
                        project = build.branch.project.name,
                        build = build.name
                    )
                )
            )
        ) {
            it?.linksBuild?.payloadUserErrors?.convert()
        }
    }

    /**
     * Links this build to other builds using their project names and their names.
     *
     * The project name may contain a "@qualifier".
     */
    fun linksTo(links: Map<String, String>) {
        graphqlConnector.mutate(
            LinksBuildMutation(
                branch.project.name,
                name,
                links.map { (project, name) ->
                    if (project.contains("@")) {
                        val projectName = project.substringBefore("@")
                        val qualifier = project.substringAfter("@")
                        LinksBuildInputItem(
                            project = projectName,
                            qualifier = Optional.presentIfNotNull(qualifier),
                            build = name,
                        )
                    } else {
                        LinksBuildInputItem(
                            project = project,
                            qualifier = Optional.absent(),
                            build = name,
                        )
                    }
                }
            )
        ) {
            it?.linksBuild?.payloadUserErrors?.convert()
        }
    }

    /**
     * See [linksTo].
     */
    fun linksTo(vararg links: Pair<String, String>) {
        linksTo(links.toMap())
    }

    /**
     * Updates the creation time for a build
     */
    fun updateCreationTime(time: LocalDateTime): Build =
        graphqlConnector.mutate(
            UpdateBuildCreationTimeMutation(
                id.toInt(),
                time
            )
        ) {
            it?.updateBuild?.payloadUserErrors?.convert()
        }
            ?.checkData { it.updateBuild?.build }
            ?.buildFragment?.toBuild(this)
            ?: throw GraphQLMissingDataException("Did not get back the updated build")

}
