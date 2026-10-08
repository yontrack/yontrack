package net.nemerosa.ontrack.kdsl.spec.extension.findings

import net.nemerosa.ontrack.kdsl.connector.graphql.schema.FindingHistoryQuery
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.FindingHistoryEntryType
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector
import net.nemerosa.ontrack.kdsl.spec.Project
import java.time.LocalDateTime

/**
 * One continuous stretch of the exposure of a finding on a branch, for one validation stamp.
 *
 * @property branch Name of the branch
 * @property validationStamp Name of the validation stamp of the scans
 * @property startedAt Time of the run which started the period
 * @property startedInBuild Display name of the build of this run, if known
 * @property endedAt Time of the run which ended the period, `null` while it is open
 * @property endedInBuild Display name of the build of this run, if known
 * @property ongoing Whether the period is still open
 * @property durationSeconds Duration of the period, until now while it is open
 */
data class FindingExposurePeriod(
    val branch: String,
    val validationStamp: String,
    val startedAt: LocalDateTime,
    val startedInBuild: String?,
    val endedAt: LocalDateTime?,
    val endedInBuild: String?,
    val ongoing: Boolean,
    val durationSeconds: Long,
)

/**
 * Entry in the history of a finding.
 *
 * @property type Type of the entry
 * @property time Time of the entry
 * @property branch Name of the branch
 * @property validationStamp Name of the validation stamp of the scans
 * @property build Display name of the build of the entry, if known
 * @property count For a group of observations, their number
 */
data class FindingHistoryEntry(
    val type: FindingHistoryEntryType,
    val time: LocalDateTime,
    val branch: String,
    val validationStamp: String,
    val build: String?,
    val count: Int,
)

/**
 * Periods of the exposure of a finding of this project, on all branches, the oldest first for each
 * branch and stamp. Empty for a user who is not granted the view of the findings of the project.
 */
fun Project.exposurePeriods(finding: Finding): List<FindingExposurePeriod> =
    graphqlConnector.query(FindingHistoryQuery(id = finding.id, size = 0))
        ?.finding?.exposures?.flatMap { exposure ->
            exposure.periods.map { period ->
                FindingExposurePeriod(
                    branch = exposure.branch.name!!,
                    validationStamp = exposure.validationStamp.name!!,
                    startedAt = period.startedAt,
                    startedInBuild = period.startedInBuild,
                    endedAt = period.endedAt,
                    endedInBuild = period.endedInBuild,
                    ongoing = period.ongoing,
                    durationSeconds = period.durationSeconds,
                )
            }
        }
        ?: emptyList()

/**
 * History of a finding of this project, the most recent first: the start and the end of each
 * period of its exposure, the changes of acceptance, and its observations, grouped between these.
 * Empty for a user who is not granted the view of the findings of the project.
 *
 * @param finding Finding
 * @param size Maximum number of entries
 */
fun Project.history(finding: Finding, size: Int = 100): List<FindingHistoryEntry> =
    graphqlConnector.query(FindingHistoryQuery(id = finding.id, size = size))
        ?.finding?.history?.pageItems?.map { entry ->
            FindingHistoryEntry(
                type = entry.type,
                time = entry.time,
                branch = entry.branch.name!!,
                validationStamp = entry.validationStamp.name!!,
                build = entry.build,
                count = entry.count,
            )
        }
        ?: emptyList()
