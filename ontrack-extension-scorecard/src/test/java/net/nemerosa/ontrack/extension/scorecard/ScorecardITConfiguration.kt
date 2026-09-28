package net.nemerosa.ontrack.extension.scorecard

import net.nemerosa.ontrack.common.RunProfile
import net.nemerosa.ontrack.extension.api.support.TestBranchModelMatcherProvider
import net.nemerosa.ontrack.extension.chart.support.Interval
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingComputer
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingOutcome
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingSubject
import net.nemerosa.ontrack.extension.scorecard.model.ReadingUnknownReason
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * Test configuration shared by the integration tests of the scorecard.
 */
@Configuration
@Profile(RunProfile.DEV)
class ScorecardITConfiguration {

    /**
     * Branch model, for the projects which register themselves.
     */
    @Bean
    fun testBranchModelMatcherProvider() = TestBranchModelMatcherProvider()

    /**
     * Computer which throws for the projects registered in it, to check how failures are isolated.
     */
    @Bean
    fun failingReadingComputer() = FailingReadingComputer()
}

/**
 * Reading `test.failing`: throws for the projects whose ID is registered in [failingProjects],
 * unknown for the others.
 */
class FailingReadingComputer : ReadingComputer {

    val failingProjects: MutableSet<Int> = mutableSetOf()

    override val key: String = KEY

    override fun compute(subject: ReadingSubject, window: Interval): ReadingOutcome =
        if (subject.project.id() in failingProjects) {
            error("Failing on purpose for ${subject.project.name}")
        } else {
            ReadingOutcome.unknown(ReadingUnknownReason.NO_SAMPLES)
        }

    companion object {
        const val KEY = "test.failing"
    }
}
