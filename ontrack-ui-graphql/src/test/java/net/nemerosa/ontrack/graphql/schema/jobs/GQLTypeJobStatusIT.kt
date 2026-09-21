package net.nemerosa.ontrack.graphql.schema.jobs

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.job.*
import net.nemerosa.ontrack.model.job.JobHistoryItemStatus
import net.nemerosa.ontrack.model.job.JobHistoryService
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@AsAdminTest
class GQLTypeJobStatusIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var jobHistoryService: JobHistoryService

    @Autowired
    private lateinit var jobScheduler: JobScheduler

    @Test
    fun `Getting the history of a single job`() {
        withJobHistory { now, jobKey ->
            run(
                """
                    query {
                        jobs(
                            category: "${jobKey.type.category.key}",
                            type: "${jobKey.type.key}",
                        ) {
                            pageItems {
                                jobHistory(
                                    from: "${Time.store(now.minusDays(14))}",
                                    to: "${Time.store(now)}",
                                    skipErrors: false
                                ) {
                                    status
                                    duration
                                    durationMs
                                }
                            }
                        }
                    }
                """.trimIndent()
            ) { data ->
                val items = data.path("jobs").path("pageItems")[0]
                    .path("jobHistory")
                    .values().map { it.path("status").asText() }
                assertEquals(14, items.size)
                assertEquals(7, items.count { it == JobHistoryItemStatus.ERROR.name })
            }
        }
    }

    @Test
    fun `Getting the history of a single job, skipping the errors`() {
        withJobHistory { now, jobKey ->
            run(
                """
                    query {
                        jobs(
                            category: "${jobKey.type.category.key}",
                            type: "${jobKey.type.key}",
                        ) {
                            pageItems {
                                jobHistory(
                                    from: "${Time.store(now.minusDays(14))}",
                                    to: "${Time.store(now)}",
                                    skipErrors: true,
                                ) {
                                    status
                                }
                            }
                        }
                    }
                """.trimIndent()
            ) { data ->
                val items = data.path("jobs").path("pageItems")[0]
                    .path("jobHistory")
                    .values().map { it.path("status").asText() }
                assertEquals(7, items.size)
                assertEquals(0, items.count { it == JobHistoryItemStatus.ERROR.name })
            }
        }
    }

    @Test
    fun `Getting the histogram of a single job`() {
        withJobHistory { now, jobKey ->
            run(
                """
                    query {
                        jobs(
                            category: "${jobKey.type.category.key}",
                            type: "${jobKey.type.key}",
                        ) {
                            pageItems {
                                jobHistogram(
                                    from: "${Time.store(now.minusDays(21))}",
                                    to: "${Time.store(now)}",
                                    interval: "7d",
                                    skipErrors: false,
                                ) {
                                    items {
                                        count
                                        errorCount
                                        avgDurationMs
                                        minDurationMs
                                        maxDurationMs
                                        error
                                    }
                                }
                            }
                        }
                    }
                """.trimIndent()
            ) { data ->
                val items = data.path("jobs").path("pageItems")[0]
                    .path("jobHistogram").path("items")
                assertEquals(3, items.size())
                val item = items[0]
                assertTrue(item.path("minDurationMs").asLong() >= 0)
                assertTrue(item.path("maxDurationMs").asLong() >= 0)
            }
        }
    }

    @Test
    fun `Getting the histogram of a single job using a period`() {
        withJobHistory { now, jobKey ->
            run(
                """
                    query {
                        jobs(
                            category: "${jobKey.type.category.key}",
                            type: "${jobKey.type.key}",
                        ) {
                            pageItems {
                                jobHistogram(
                                    period: "7d"
                                    interval: "1d",
                                    skipErrors: false,
                                ) {
                                    items {
                                        count
                                        errorCount
                                        avgDurationMs
                                        error
                                    }
                                }
                            }
                        }
                    }
                """.trimIndent()
            ) { data ->
                val items = data.path("jobs").path("pageItems")[0]
                    .path("jobHistogram").path("items")
                assertEquals(7, items.size())
            }
        }
    }

    private fun withJobHistory(
        block: (
            now: LocalDateTime,
            jobKey: JobKey,
        ) -> Unit,
    ) {
        val now = Time.now
        val jobKey = jobKey()

        val job = object : Job {
            override fun getKey(): JobKey = jobKey
            override fun getTask() = JobRun {}
            override fun getDescription(): String = "Test job"
            override fun isDisabled(): Boolean = false
        }

        jobScheduler.schedule(job, Schedule.NONE)

        repeat(21) {
            val start = now.minusDays(21 - it.toLong()).plusHours(12)
            jobHistoryService.record(
                jobKey = jobKey,
                startedAt = start,
                endedAt = start.plusMinutes(Random.nextLong(60)),
                error = if (it % 2 == 0) "Error $it" else null,
            )
        }
        block(now, jobKey)
    }

    private fun jobKey() = JobKey(
        type = jobType(),
        id = uid("jk-"),
    )

    private fun jobType() = JobType(
        category = jobCategory(),
        key = uid("jt-"),
        name = uid("JT "),
    )

    private fun jobCategory() = JobCategory(
        key = uid("jc-"),
        name = uid("JC "),
    )

}