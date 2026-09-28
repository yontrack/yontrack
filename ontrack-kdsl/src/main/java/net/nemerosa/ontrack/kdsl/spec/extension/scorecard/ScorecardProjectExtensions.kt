package net.nemerosa.ontrack.kdsl.spec.extension.scorecard

import net.nemerosa.ontrack.kdsl.connector.graphql.convert
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.ProjectScorecardQuery
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.RecomputeProjectScorecardMutation
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector
import net.nemerosa.ontrack.kdsl.spec.Project
import java.time.Duration

/**
 * Gets the delivery scorecard of this project: its readings in every set it is in.
 *
 * @param historyDays Number of days of daily snapshots to get for each reading, none when `null`
 */
fun Project.scorecard(historyDays: Int? = null): Scorecard =
    Scorecard(
        sets = graphqlConnector.query(
            ProjectScorecardQuery(
                projectId = id.toInt(),
                withHistory = historyDays != null,
                historyDays = historyDays ?: 0,
            )
        )?.project?.scorecard?.sets?.map { set ->
            ScorecardSet(
                name = set.name,
                estate = set.estate?.name,
                readings = set.readings.map { reading ->
                    reading.readingFragment.toReading(
                        history = reading.history?.map { it.readingFragment.toReading() } ?: emptyList()
                    )
                },
            )
        } ?: emptyList()
    )

/**
 * Queues the recompute of the readings of this project, in every set it is in, overwriting the
 * snapshots of the day. Returns at once: see [recomputeScorecardAndWait] to wait for the readings.
 *
 * Needs the right to configure the project.
 */
fun Project.recomputeScorecard() {
    graphqlConnector.mutate(
        RecomputeProjectScorecardMutation(id.toInt())
    ) {
        it?.recomputeProjectScorecard?.payloadUserErrors?.convert()
    }
}

/**
 * Recomputes the readings of this project, in every set it is in, and waits until each set has its
 * readings computed anew.
 *
 * @param timeout Maximum time to wait
 * @param interval Time between two checks
 * @return The recomputed scorecard
 */
fun Project.recomputeScorecardAndWait(
    timeout: Duration = Duration.ofMinutes(1),
    interval: Duration = Duration.ofSeconds(1),
): Scorecard {
    val before = scorecard().sets.associate { it.estate to it.computedAt }
    recomputeScorecard()
    return waitForScorecard(
        project = this,
        deadline = System.nanoTime() + timeout.toNanos(),
        interval = interval,
        task = "readings",
    ) { scorecard ->
        scorecard.sets.all { set -> set.isComputedAfter(before[set.estate]) }
    }
}

/**
 * Polls the scorecard of a project until a condition is met.
 *
 * @param deadline Value of [System.nanoTime] after which the wait fails
 * @param task What is waited for, for the message of the timeout
 */
internal fun waitForScorecard(
    project: Project,
    deadline: Long,
    interval: Duration,
    task: String,
    condition: (Scorecard) -> Boolean,
): Scorecard {
    while (true) {
        val scorecard = project.scorecard()
        if (condition(scorecard)) {
            return scorecard
        }
        check(System.nanoTime() < deadline) {
            "Timeout waiting for the $task of the project ${project.name} to be computed. " +
                    "A project whose computation fails gets no reading: see the logs of the server."
        }
        Thread.sleep(interval.toMillis())
    }
}
