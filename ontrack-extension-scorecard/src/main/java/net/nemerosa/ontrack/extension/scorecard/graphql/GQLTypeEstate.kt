package net.nemerosa.ontrack.extension.scorecard.graphql

import graphql.Scalars.GraphQLInt
import graphql.Scalars.GraphQLString
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.extension.findings.model.FindingKind
import net.nemerosa.ontrack.extension.scorecard.engine.MarkerKind
import net.nemerosa.ontrack.extension.scorecard.estates.*
import net.nemerosa.ontrack.extension.scorecard.model.ReadingDirection
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.schema.GQLTypeProject
import net.nemerosa.ontrack.graphql.support.doubleField
import net.nemerosa.ontrack.graphql.support.intField
import net.nemerosa.ontrack.graphql.support.listType
import net.nemerosa.ontrack.model.labels.Label
import org.springframework.stereotype.Component

/**
 * An estate: a group of projects selected by labels, read together against a marker and targets.
 */
@Component
class GQLTypeEstate(
    private val estateService: EstateService,
) : GQLType {

    override fun getTypeName(): String = Estate::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description(
                "A group of projects selected by labels, every one of them required, and read together, " +
                        "with the marker and the targets its projects are read against."
            )
            .field {
                it.name(Estate::id.name)
                    .description("ID of the estate")
                    .type(GraphQLNonNull(GraphQLInt))
            }
            .field {
                it.name(Estate::name.name)
                    .description("Unique name of the estate")
                    .type(GraphQLNonNull(GraphQLString))
            }
            .field {
                it.name(Estate::description.name)
                    .description("Description of the estate")
                    .type(GraphQLString)
            }
            .field {
                it.name(Estate::labels.name)
                    .description("Labels selecting the projects of the estate, all of them required")
                    .type(listType(GraphQLTypeReference(Label::class.java.simpleName)))
            }
            .field {
                it.name(Estate::marker.name)
                    .description("Marker the delivery readings of the estate are read up to. Null for the default marker: the highest-ordered environment where the project owns a slot, else the last promotion level of each branch.")
                    .type(GraphQLTypeReference(ESTATE_MARKER))
            }
            .field {
                it.name(Estate::readingConfigs.name)
                    .description("Window override and target of the readings which have one, by reading key")
                    .type(listType(GraphQLTypeReference(EstateReadingConfig::class.java.simpleName)))
            }
            .field {
                it.name(Estate::security.name)
                    .description("What the estate expects of the security scans of its projects")
                    .type(GraphQLNonNull(GraphQLTypeReference(EstateSecurity::class.java.simpleName)))
            }
            .field {
                it.name("projects")
                    .description("Projects the estate selects, the ones carrying all its labels, among the ones the user can see")
                    .type(listType(GraphQLTypeReference(GQLTypeProject.PROJECT)))
                    .dataFetcher { env -> estateService.getProjects(env.getSource<Estate>()!!) }
            }
            .build()

    companion object {
        const val ESTATE_MARKER = "EstateMarker"
    }
}

/**
 * The marker an estate names, flattened.
 */
@Component
class GQLTypeEstateMarker : GQLType {

    override fun getTypeName(): String = GQLTypeEstate.ESTATE_MARKER

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description("Marker an estate names for its delivery readings")
            .field {
                it.name("kind")
                    .description("Kind of marker")
                    .type(GraphQLNonNull(GraphQLTypeReference(MarkerKind::class.java.simpleName)))
                    .dataFetcher { env -> env.getSource<EstateMarker>()!!.kind }
            }
            .field {
                it.name(EstatePromotionMarker::levelName.name)
                    .description("For a PROMOTION marker, name of the promotion level, read on each branch which has one")
                    .type(GraphQLString)
                    .dataFetcher { env -> (env.getSource<EstateMarker>() as? EstatePromotionMarker)?.levelName }
            }
            .field {
                it.name(EstateEnvironmentMarker::environment.name)
                    .description("For an ENVIRONMENT marker, name of the environment")
                    .type(GraphQLString)
                    .dataFetcher { env -> (env.getSource<EstateMarker>() as? EstateEnvironmentMarker)?.environment }
            }
            .field {
                it.name(EstateEnvironmentMarker::qualifier.name)
                    .description("For an ENVIRONMENT marker, qualifier of the slots read, empty for the default one")
                    .type(GraphQLString)
                    .dataFetcher { env -> (env.getSource<EstateMarker>() as? EstateEnvironmentMarker)?.qualifier }
            }
            .build()
}

/**
 * Window override and target of one reading in an estate.
 */
@Component
class GQLTypeEstateReadingConfig : GQLType {

    override fun getTypeName(): String = EstateReadingConfig::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description("Window override and target of one reading in an estate")
            .field {
                it.name(EstateReadingConfig::key.name)
                    .description("Key of the reading, like delivery.leadTime")
                    .type(GraphQLNonNull(GraphQLString))
            }
            .intField(EstateReadingConfig::windowDays, "Number of days the reading is taken over, null for the window of the settings")
            .doubleField(EstateReadingConfig::target, "Threshold the reading is judged against, in the unit of the reading (seconds for durations, per week for frequencies, 0 to 100 for rates), null for none")
            .field {
                it.name(EstateReadingConfig::direction.name)
                    .description("Which way the reading is better, and so how the target judges it")
                    .type(GraphQLTypeReference(ReadingDirection::class.java.simpleName))
            }
            .build()
}

/**
 * What an estate expects of the security scans of its projects.
 */
@Component
class GQLTypeEstateSecurity : GQLType {

    override fun getTypeName(): String = EstateSecurity::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description("What an estate expects of the security scans of its projects")
            .field {
                it.name(EstateSecurity::expectedKinds.name)
                    .description("Kinds of scan every project must have run, each fresher than the freshness, to be covered. Empty: any fresh scan covers a project.")
                    .type(listType(GraphQLTypeReference(FindingKind::class.java.simpleName)))
            }
            .intField(EstateSecurity::freshnessDays, "Number of days a scan stays fresh, null for the freshness of the settings")
            .intField(EstateSecurity::criticalTargetDays, "Number of days a CRITICAL finding may stay open, null for no target")
            .intField(EstateSecurity::highTargetDays, "Number of days a HIGH finding may stay open, null for no target")
            .build()
}
