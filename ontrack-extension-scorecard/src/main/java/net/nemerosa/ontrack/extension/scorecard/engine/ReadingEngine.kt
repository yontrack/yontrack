package net.nemerosa.ontrack.extension.scorecard.engine

import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.chart.support.Interval
import net.nemerosa.ontrack.extension.scorecard.metrics.ScorecardMetrics
import net.nemerosa.ontrack.extension.scorecard.model.EstateReadingSet
import net.nemerosa.ontrack.extension.scorecard.model.NoEstateReadingSet
import net.nemerosa.ontrack.extension.scorecard.model.Reading
import net.nemerosa.ontrack.extension.scorecard.model.ReadingKeys
import net.nemerosa.ontrack.extension.scorecard.model.ReadingSet
import net.nemerosa.ontrack.extension.scorecard.settings.ScorecardSettings
import net.nemerosa.ontrack.extension.scorecard.storage.ReadingRepository
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.structure.Project
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * Computes and stores the readings of projects for a set.
 *
 * Every [computer][ReadingComputer] is run for the project; the engine adds to each outcome the
 * common details — `markerKind`, `marker` and `scope` — and stores the readings as the snapshot
 * of the day. A project whose computation throws gets no reading for the set that day: the error
 * is logged and counted, and the other projects are computed.
 *
 * The caller is expected to run with the rights to read the projects (the jobs run as admin).
 */
@Component
class ReadingEngine(
    private val readingSubjectResolver: ReadingSubjectResolver,
    computers: List<ReadingComputer>,
    private val readingRepository: ReadingRepository,
    private val cachedSettingsService: CachedSettingsService,
    private val meterRegistry: MeterRegistry,
    private val readingsListeners: List<ReadingsListener>,
) {

    private val logger: Logger = LoggerFactory.getLogger(ReadingEngine::class.java)

    private val computers: List<ReadingComputer> = computers.sortedWith(
        compareBy({ ReadingKeys.rank(it.key) }, { it.key })
    )

    /**
     * Computes the readings of every project for the set, isolating the failures.
     *
     * @return Number of projects whose computation has failed
     */
    fun computeSet(set: ReadingSet, projects: List<Project>, progress: (String) -> Unit = {}): Int =
        projects.count { project ->
            progress("Computing the readings of ${project.name} for ${set.name}")
            computeProject(set, project) == null
        }

    /**
     * Computes and stores the readings of one project for the set, overwriting the snapshot of the day.
     *
     * @return The stored readings, or `null` if the computation has failed
     */
    fun computeProject(set: ReadingSet, project: Project): List<Reading>? {
        val timer = meterRegistry.timer(ScorecardMetrics.computation, ScorecardMetrics.Tags.ESTATE, set.tag)
        return timer.recordCallable {
            try {
                val readings = compute(set, project, Time.now)
                readingRepository.save(readings)
                readingsListeners.forEach { it.onReadings(set, project, readings) }
                readings
            } catch (any: Exception) {
                logger.error("Cannot compute the readings of ${project.name} for ${set.name}", any)
                meterRegistry.counter(ScorecardMetrics.errors, ScorecardMetrics.Tags.ESTATE, set.tag).increment()
                null
            }
        }
    }

    private fun compute(set: ReadingSet, project: Project, now: LocalDateTime): List<Reading> {
        val subject = readingSubjectResolver.resolve(set, project)
        val common = mapOf(
            "markerKind" to subject.markerKind.name,
            "marker" to subject.marker?.details,
            "scope" to subject.scope.details,
        )
        return computers.map { computer ->
            val window = Interval(now.minusDays(windowDays(set, computer.key).toLong()), now)
            val outcome = computer.compute(subject, window)
            Reading(
                estateId = set.estateId,
                projectId = project.id(),
                key = computer.key,
                day = now.toLocalDate(),
                computedAt = now,
                windowStart = window.start,
                windowEnd = window.end,
                value = outcome.value,
                basis = outcome.basis,
                unknownReason = outcome.unknownReason,
                details = (common + outcome.details).asJson(),
            )
        }
    }

    /**
     * Window of a reading in a set: the override of the estate, or the one of the settings.
     */
    private fun windowDays(set: ReadingSet, key: String): Int = when (set) {
        NoEstateReadingSet -> null
        is EstateReadingSet -> set.estate.readingConfig(key)?.windowDays
    } ?: cachedSettingsService.getCachedSettings(ScorecardSettings::class.java).windowDays
}
