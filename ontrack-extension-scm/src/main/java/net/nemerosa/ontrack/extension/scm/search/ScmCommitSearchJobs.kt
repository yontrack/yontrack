package net.nemerosa.ontrack.extension.scm.search

import net.nemerosa.ontrack.extension.scm.SCMExtensionConfigProperties
import net.nemerosa.ontrack.extension.scm.SCMJobs
import net.nemerosa.ontrack.job.*
import net.nemerosa.ontrack.model.support.JobProvider
import org.springframework.stereotype.Component

/**
 * The incremental scan of the commits, for their search documents: hourly by default
 * (`ontrack.config.extension.scm.search.schedule`), manual only when the scheduled indexation is
 * disabled (`ontrack.config.extension.scm.search.scheduled`).
 *
 * The weekly full scan is the reconciliation job of the `scm-commit` type.
 */
@Component
class ScmCommitSearchJobs(
    private val scmExtensionConfigProperties: SCMExtensionConfigProperties,
    private val scmCommitSearchExtension: ScmCommitSearchExtension,
) : JobProvider {

    companion object {
        val jobType: JobType = SCMJobs.category
            .getType("search-commits").withName("SCM commits search")
    }

    override fun getStartingJobs(): Collection<JobRegistration> = listOf(
        JobRegistration(
            job = createIncrementalJob(),
            schedule = scmExtensionConfigProperties.search.toSchedule(),
        )
    )

    private fun createIncrementalJob() = object : Job {

        override fun isDisabled(): Boolean = false

        override fun getKey(): JobKey = jobType.getKey("incremental")

        override fun getDescription(): String = "Indexation of the new SCM commits for the search"

        override fun getTask() = JobRun { listener ->
            listener.message("Indexing the new SCM commits")
            val count = scmCommitSearchExtension.indexNewCommits()
            listener.message("$count SCM commits scanned")
        }
    }
}
