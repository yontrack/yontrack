package net.nemerosa.ontrack.kdsl.spec

import net.nemerosa.ontrack.kdsl.connector.Connector
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.ValidationRunRunInfoQuery
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.ProjectEntityType
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector
import java.time.LocalDateTime

/**
 * Representation of a validation run.
 *
 * @property connector Ontrack connector
 * @property id Validation run ID
 * @property name Validation run name
 * @property description Validation run description
 */
class ValidationRun(
    connector: Connector,
    id: UInt,
    val description: String?,
    val data: ValidationRunData?,
    val statuses: List<ValidationRunStatus>,
    /**
     * Time of the run - the one given when it was created, when it was backdated.
     */
    val time: LocalDateTime? = null,
) : ProjectEntity(connector, ProjectEntityType.VALIDATION_RUN, id) {
    val lastStatus: ValidationRunStatus get() = statuses.first()

    /**
     * Information about the run.
     */
    val runInfo: RunInfo?
        get() = graphqlConnector.query(
            ValidationRunRunInfoQuery(id.toInt())
        )?.validationRuns?.firstOrNull()?.runInfo?.run {
            RunInfo(
                runTime = runTime,
            )
        }
}
