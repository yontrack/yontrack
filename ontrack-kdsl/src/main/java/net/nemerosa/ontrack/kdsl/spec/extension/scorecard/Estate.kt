package net.nemerosa.ontrack.kdsl.spec.extension.scorecard

import com.apollographql.apollo.api.Optional
import net.nemerosa.ontrack.kdsl.connector.Connector
import net.nemerosa.ontrack.kdsl.connector.graphql.GraphQLMissingDataException
import net.nemerosa.ontrack.kdsl.connector.graphql.checkData
import net.nemerosa.ontrack.kdsl.connector.graphql.convert
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.DeleteEstateMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.EstateProjectsQuery
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.RecomputeEstateMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.UpdateEstateMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.fragment.EstateFragment
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.EstateMarkerInput
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.EstateReadingConfigInput
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.MarkerKind
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.ReadingDirection
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector
import net.nemerosa.ontrack.kdsl.spec.Label
import net.nemerosa.ontrack.kdsl.spec.Project
import net.nemerosa.ontrack.kdsl.spec.Resource
import net.nemerosa.ontrack.kdsl.spec.toLabel
import net.nemerosa.ontrack.kdsl.spec.toProject
import java.time.Duration

/**
 * A group of projects selected by labels, all of them required, and read together against a
 * marker and targets.
 *
 * Needs the licensed feature "Delivery scorecard" (`extension.scorecard`); managing an estate needs
 * the `EstateManagement` global function.
 *
 * @property id ID of the estate
 * @property name Unique name of the estate
 * @property description Description of the estate
 * @property labels Labels selecting the projects of the estate
 * @property marker Marker the delivery readings are read up to, `null` for the default one: the
 * highest-ordered environment where the project owns a slot, else the last promotion level of each
 * branch
 * @property readingConfigs Window override and target of the readings which have one
 */
class Estate(
    connector: Connector,
    val id: Int,
    val name: String,
    val description: String?,
    val labels: List<Label>,
    val marker: EstateMarker?,
    val readingConfigs: List<EstateReadingConfig>,
) : Resource(connector) {

    /**
     * Window override and target of a reading, `null` when the estate sets none for it.
     *
     * @param key Key of the reading, see [ReadingKeys]
     */
    fun readingConfig(key: String): EstateReadingConfig? = readingConfigs.firstOrNull { it.key == key }

    /**
     * Projects the estate selects: the ones carrying all its labels, among the ones the user can see.
     */
    val projects: List<Project>
        get() = graphqlConnector.query(
            EstateProjectsQuery(name)
        )?.estate?.projects?.map {
            it.projectFragment.toProject(this)
        } ?: emptyList()

    /**
     * Replaces the definition of this estate. Every field defaults to its current value, so only the
     * fields to change need to be given. The snapshots of the estate are kept.
     *
     * @param name Unique name of the estate
     * @param description Description of the estate
     * @param labels Labels selecting the projects, as `category:name` or `name`
     * @param marker Marker of the delivery readings, `null` for the default one
     * @param readings Window override and target per reading, the ones of the estate replaced
     * @return Updated estate
     */
    fun update(
        name: String = this.name,
        description: String? = this.description,
        labels: List<String> = this.labels.map { it.display },
        marker: EstateMarker? = this.marker,
        readings: List<EstateReadingConfig> = this.readingConfigs,
    ): Estate =
        graphqlConnector.mutate(
            UpdateEstateMutation(
                id = id,
                name = name,
                description = Optional.presentIfNotNull(description),
                labels = labels,
                marker = Optional.presentIfNotNull(marker?.toInput()),
                readings = Optional.present(readings.map { it.toInput() }),
            )
        ) {
            it?.updateEstate?.payloadUserErrors?.convert()
        }
            ?.checkData { it.updateEstate?.estate }
            ?.estateFragment?.toEstate(connector)
            ?: throw GraphQLMissingDataException("Did not get back the updated estate")

    /**
     * Deletes this estate, with its snapshots.
     */
    fun delete() {
        graphqlConnector.mutate(
            DeleteEstateMutation(id)
        ) {
            it?.deleteEstate?.payloadUserErrors?.convert()
        }
    }

    /**
     * Queues the recompute of the readings of every project of this estate, overwriting the
     * snapshots of the day. Returns at once: see [recomputeAndWait] to wait for the readings.
     */
    fun recompute() {
        graphqlConnector.mutate(
            RecomputeEstateMutation(id)
        ) {
            it?.recomputeEstate?.payloadUserErrors?.convert()
        }
    }

    /**
     * Recomputes the readings of every project of this estate and waits until each of them has its
     * readings for the estate computed anew.
     *
     * A project whose computation fails gets no reading: the wait then times out.
     *
     * @param timeout Maximum time to wait for all the projects
     * @param interval Time between two checks
     * @return The scorecard of each project of the estate once recomputed, by project name
     */
    fun recomputeAndWait(
        timeout: Duration = Duration.ofMinutes(2),
        interval: Duration = Duration.ofSeconds(1),
    ): Map<String, Scorecard> {
        val projects = this.projects
        val before = projects.associateWith { it.scorecard().estate(name)?.computedAt }
        recompute()
        val deadline = System.nanoTime() + timeout.toNanos()
        return projects.associate { project ->
            project.name to waitForScorecard(
                project = project,
                deadline = deadline,
                interval = interval,
                task = "readings of the estate $name",
            ) { scorecard ->
                scorecard.estate(name)?.isComputedAfter(before[project]) ?: false
            }
        }
    }
}

/**
 * Marker an estate reads its delivery readings up to.
 */
sealed interface EstateMarker {

    /**
     * Promotion marker: the promotion level of this name, on each branch in scope which has one.
     *
     * @property levelName Name of the promotion level
     */
    data class Promotion(
        val levelName: String,
    ) : EstateMarker

    /**
     * Environment marker: the deployments done in the slot of the project in this environment.
     *
     * @property environment Name of the environment
     * @property qualifier Qualifier of the slot, empty for the default one. Qualifiers are never pooled.
     */
    data class Environment(
        val environment: String,
        val qualifier: String = "",
    ) : EstateMarker
}

/**
 * Window override and target of one reading in an estate.
 *
 * @property key Key of the reading, see [ReadingKeys]
 * @property windowDays Number of days the reading is taken over, `null` for the window of the settings
 * @property target Threshold the reading is judged against, in the unit of the reading (seconds for
 * durations, per week for frequencies, 0 to 100 for rates), `null` for none
 * @property direction Which way the reading is better, and so how the target judges it. Set by the
 * reading, read-only: ignored when creating or updating an estate.
 */
data class EstateReadingConfig(
    val key: String,
    val windowDays: Int? = null,
    val target: Double? = null,
    val direction: ReadingDirection? = null,
)

internal fun EstateMarker.toInput(): EstateMarkerInput = when (this) {
    is EstateMarker.Promotion -> EstateMarkerInput(
        kind = MarkerKind.PROMOTION,
        levelName = Optional.present(levelName),
    )

    is EstateMarker.Environment -> EstateMarkerInput(
        kind = MarkerKind.ENVIRONMENT,
        environment = Optional.present(environment),
        qualifier = Optional.present(qualifier),
    )
}

internal fun EstateReadingConfig.toInput() = EstateReadingConfigInput(
    key = key,
    windowDays = Optional.presentIfNotNull(windowDays),
    target = Optional.presentIfNotNull(target),
)

internal fun EstateFragment.toEstate(connector: Connector) = Estate(
    connector = connector,
    id = id,
    name = name,
    description = description,
    labels = labels.map { it.labelFragment.toLabel() },
    marker = marker?.let {
        when (it.kind) {
            MarkerKind.PROMOTION -> EstateMarker.Promotion(levelName = it.levelName ?: "")
            MarkerKind.ENVIRONMENT -> EstateMarker.Environment(
                environment = it.environment ?: "",
                qualifier = it.qualifier ?: "",
            )

            else -> error("Unknown kind of marker: ${it.kind}")
        }
    },
    readingConfigs = readingConfigs.map {
        EstateReadingConfig(
            key = it.key,
            windowDays = it.windowDays,
            target = it.target,
            direction = it.direction,
        )
    },
)
