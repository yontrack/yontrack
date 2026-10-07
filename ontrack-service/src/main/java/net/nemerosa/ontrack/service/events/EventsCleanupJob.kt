package net.nemerosa.ontrack.service.events

import net.nemerosa.ontrack.job.*
import net.nemerosa.ontrack.model.events.EventsCleanupService
import net.nemerosa.ontrack.model.support.JobProvider
import org.springframework.stereotype.Component

/**
 * Daily deletion of the events older than their retention, which is disabled by default.
 */
@Component
class EventsCleanupJob(
    private val eventsCleanupService: EventsCleanupService,
) : JobProvider {

    override fun getStartingJobs(): Collection<JobRegistration> = listOf(
        JobRegistration(
            job = createEventsCleanupJob(),
            schedule = Schedule.EVERY_DAY,
        )
    )

    private fun createEventsCleanupJob() = object : Job {

        override fun getKey(): JobKey =
            JobCategory.CORE.getType("events-cleanup").withName("Events cleanup").getKey("events-cleanup")

        override fun getTask() = JobRun { listener ->
            val count = eventsCleanupService.cleanup()
            listener.message("Deleted $count events")
        }

        override fun getDescription(): String = "Events cleanup"

        override fun isDisabled(): Boolean = false
    }
}
