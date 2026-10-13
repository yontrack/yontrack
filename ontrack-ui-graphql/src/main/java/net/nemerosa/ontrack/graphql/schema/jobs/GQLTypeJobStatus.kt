package net.nemerosa.ontrack.graphql.schema.jobs

import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.common.parseDuration
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.*
import net.nemerosa.ontrack.job.JobStatus
import net.nemerosa.ontrack.model.job.JobHistoryService
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime

@Component
class GQLTypeJobStatus(
    private val gqlTypeJobHistoryItem: GQLTypeJobHistoryItem,
    private val gqlTypeJobHistogram: GQLTypeJobHistogram,
    private val jobHistoryService: JobHistoryService,
) : GQLType {

    override fun getTypeName(): String = JobStatus::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description(getTypeDescription(JobStatus::class))
            .longField(JobStatus::id)
            .field(JobStatus::key)
            .field(JobStatus::schedule)
            .field(JobStatus::actualSchedule)
            .stringField(JobStatus::description)
            .booleanField(JobStatus::isRunning)
            .booleanField(JobStatus::isValid)
            .booleanField(JobStatus::isPaused)
            .booleanField(JobStatus::isDisabled)
            .field(JobStatus::progress)
            .stringField(JobStatus::progressText)
            .longField(JobStatus::runCount)
            .localDateTimeField(JobStatus::lastRunDate)
            .longField(JobStatus::lastRunDurationMs)
            .localDateTimeField(JobStatus::nextRunDate)
            .longField(JobStatus::lastErrorCount)
            .longField(JobStatus::lastTimeoutCount)
            .stringField(JobStatus::lastError)
            .stringField(JobStatus::runningOn, "Replica running the job, if running: its host name, the name of its pod in Kubernetes")
            .localDateTimeField(JobStatus::runningSince, "Start of the current run, if running")
            .field(JobStatus::state)
            .booleanField(JobStatus::isError)
            .booleanField(JobStatus::isTimeout)
            .booleanFieldFunction<JobStatus>(name = "canRun", description = "Checks if the job can be run") {
                it.canRun()
            }
            .booleanFieldFunction<JobStatus>(name = "canPause", description = "Checks if the job can be paused") {
                it.canPause()
            }
            .booleanFieldFunction<JobStatus>(name = "canResume", description = "Checks if the job can be resumed") {
                it.canResume()
            }
            .booleanFieldFunction<JobStatus>(name = "canBeDeleted", description = "Checks if the job can be deleted") {
                it.canBeDeleted()
            }
            .booleanFieldFunction<JobStatus>(name = "canBeStopped", description = "Checks if the job can be stopped") {
                it.canBeStopped()
            }
            // Job history
            .field {
                it.name("jobHistory")
                    .description("List of runs for this job")
                    .type(listType(gqlTypeJobHistoryItem.typeRef))
                    .argument(dateTimeArgument(ARG_FROM, "Start of the history"))
                    .argument(dateTimeArgument(ARG_TO, "End of the history"))
                    .argument(booleanArgument(ARG_SKIP_ERRORS, "Skipping the runs in error"))
                    .dataFetcher { env ->
                        val jobStatus = env.getSource<JobStatus>()!!
                        val jobKey = jobStatus.key
                        val from = env.getArgument<LocalDateTime>(ARG_FROM)!!
                        val to = env.getArgument<LocalDateTime>(ARG_TO)!!
                        val skipErrors = env.getArgument<Boolean>(ARG_SKIP_ERRORS) ?: false
                        jobHistoryService.getHistory(
                            jobKey = jobKey,
                            from = from,
                            to = to,
                            skipErrors = skipErrors,
                        )
                    }
            }
            // Job histogram
            .field {
                it.name("jobHistogram")
                    .description("Histogram of run durations and statuses for this job")
                    .type(gqlTypeJobHistogram.typeRef)
                    .argument(dateTimeArgument(ARG_FROM, "Start of the history"))
                    .argument(dateTimeArgument(ARG_TO, "End of the history"))
                    .argument(booleanArgument(ARG_SKIP_ERRORS, "Skipping the runs in error"))
                    .argument(stringArgument(ARG_PERIOD, "Period for the histogram (used instead of from/to"))
                    .argument(stringArgument(ARG_INTERVAL, "Interval for the histogram"))
                    .dataFetcher { env ->
                        val jobStatus = env.getSource<JobStatus>()!!
                        val jobKey = jobStatus.key
                        val from = env.getArgument<LocalDateTime>(ARG_FROM)
                        val to = env.getArgument<LocalDateTime>(ARG_TO)
                        val skipErrors = env.getArgument<Boolean>(ARG_SKIP_ERRORS) ?: false

                        val period: Duration = env.getArgument<String>(ARG_PERIOD)
                            ?.takeIf(String::isNotBlank)
                            ?.let { s -> parseDuration(s) }
                            ?: Duration.ofDays(7)

                        val interval: Duration = env.getArgument<String>(ARG_INTERVAL)
                            ?.takeIf(String::isNotBlank)
                            ?.let { s -> parseDuration(s) }
                            ?: Duration.ofDays(1)

                        val actualFrom: LocalDateTime
                        val actualTo: LocalDateTime
                        if (from != null && to != null) {
                            actualFrom = from
                            actualTo = to
                        } else {
                            actualTo = to ?: Time.now
                            actualFrom = actualTo.minus(period)
                        }

                        jobHistoryService.getHistogram(
                            jobKey = jobKey,
                            from = actualFrom,
                            to = actualTo,
                            interval = interval,
                            skipErrors = skipErrors,
                        )
                    }
            }
            // OK
            .build()

    companion object {
        private const val ARG_FROM = "from"
        private const val ARG_TO = "to"
        private const val ARG_SKIP_ERRORS = "skipErrors"
        private const val ARG_PERIOD = "period"
        private const val ARG_INTERVAL = "interval"
    }
}