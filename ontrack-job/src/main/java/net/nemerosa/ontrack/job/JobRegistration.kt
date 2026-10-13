package net.nemerosa.ontrack.job

/**
 * Association of a job, a schedule and a [scope][JobScope], given by a provider or a supplier of
 * jobs.
 */
data class JobRegistration @JvmOverloads constructor(
        val job: Job,
        val schedule: Schedule,
        val scope: JobScope = JobScope.CLUSTER,
) {

    fun withSchedule(schedule: Schedule) = copy(schedule = schedule)

    fun everyMinutes(minutes: Long): JobRegistration {
        return withSchedule(Schedule.everyMinutes(minutes))
    }

    companion object {
        @JvmStatic
        fun of(job: Job): JobRegistration {
            return JobRegistration(job, Schedule.NONE)
        }
    }
}
