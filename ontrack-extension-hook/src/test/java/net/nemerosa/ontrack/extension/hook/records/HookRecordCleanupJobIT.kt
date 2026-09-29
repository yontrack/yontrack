package net.nemerosa.ontrack.extension.hook.records

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.hook.HookRequest
import net.nemerosa.ontrack.extension.hook.HookResponse
import net.nemerosa.ontrack.extension.hook.HookResponseType
import net.nemerosa.ontrack.extension.hook.HookTestSupport
import net.nemerosa.ontrack.extension.hook.settings.HookSettings
import net.nemerosa.ontrack.extension.recordings.RecordingsCleanupJobs
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.job.JobRunListener
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.util.*
import kotlin.test.assertNotNull

class HookRecordCleanupJobIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var hookTestSupport: HookTestSupport

    @Autowired
    private lateinit var recordingsCleanupJobs: RecordingsCleanupJobs

    private val ref = Time.now()

    /**
     * Testing the deletion of different hook records.
     *
     * ```
     * * running record to be deleted
     * * non-running record to be deleted
     * * --- cleanup cutoff
     * * running record to be kept
     * * non-running record to be deleted
     * * --- retention cutoff
     * * running record to be kept
     * * non-running record to be kept
     * ```
     */
    @Test
    fun `Cleanup of both running and non running records`() {
        asAdmin {
            withCleanSettings<HookSettings> {
                settingsManagerService.saveSettings(
                        HookSettings(
                                recordRetentionDuration = Duration.ofDays(10),
                                recordCleanupDuration = Duration.ofDays(10),
                        )
                )
                // Records before the cleanup cutoff (10 + 10)
                val beforeCleanupRunning = createPayload(daysBefore = 25, running = true)
                val beforeCleanupNotRunning = createPayload(daysBefore = 25, running = false)
                val beforeRetentionRunning = createPayload(daysBefore = 15, running = true)
                val beforeRetentionNotRunning = createPayload(daysBefore = 15, running = false)
                val afterRetentionRunning = createPayload(daysBefore = 5, running = true)
                val afterRetentionNotRunning = createPayload(daysBefore = 5, running = false)
                // Running the cleanup
                assertNotNull(recordingsCleanupJobs.jobRegistrations.find { it.job.key.id == "hook" }?.job, "Hook cleanup job") { job ->
                    job.task.run(JobRunListener.out())
                }
                // Checking the state of records
                hookTestSupport.assertRecordNotPresent(beforeCleanupRunning, "Running record before the cleanup cutoff must be deleted")
                hookTestSupport.assertRecordNotPresent(beforeCleanupNotRunning, "Not running record before the cleanup cutoff must be deleted")
                hookTestSupport.assertRecordPresent(beforeRetentionRunning, "Running record before the retention cutoff must NOT be deleted")
                hookTestSupport.assertRecordNotPresent(beforeRetentionNotRunning, "Not running record before the retention cutoff must be deleted")
                hookTestSupport.assertRecordPresent(afterRetentionRunning, "Running record after the retention cutoff must NOT be deleted")
                hookTestSupport.assertRecordPresent(afterRetentionNotRunning, "Not running record after the retention cutoff must NOT be deleted")
            }
        }
    }

    private fun createPayload(
            daysBefore: Int?,
            running: Boolean,
    ): String {
        val record = HookRecord(
                id = UUID.randomUUID().toString(),
                hook = "test",
                request = HookRequest(
                        body = "Some body",
                        parameters = emptyMap(),
                        headers = emptyMap(),
                ),
                startTime = daysBefore?.let { ref.minusDays(it.toLong()) } ?: ref,
                state = if (running) {
                    HookRecordState.RECEIVED
                } else {
                    HookRecordState.SUCCESS
                },
                message = null,
                exception = null,
                endTime = if (running) {
                    null
                } else if (daysBefore != null) {
                    ref.minusDays(daysBefore.toLong() - 1)
                } else {
                    ref
                },
                response = if (running) {
                    null
                } else {
                    HookResponse(
                            type = HookResponseType.PROCESSING,
                            infoLink = null,
                    )
                }
        )

        hookTestSupport.record(record)

        return record.id
    }

}