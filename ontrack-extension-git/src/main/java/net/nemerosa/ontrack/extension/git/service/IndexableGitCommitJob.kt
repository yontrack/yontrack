package net.nemerosa.ontrack.extension.git.service

import net.nemerosa.ontrack.extension.git.casc.GitConfigService
import net.nemerosa.ontrack.extension.git.model.gitRepository
import net.nemerosa.ontrack.git.GitRepositoryClient
import net.nemerosa.ontrack.git.GitRepositoryClientFactory
import net.nemerosa.ontrack.job.*
import net.nemerosa.ontrack.job.orchestrator.JobOrchestratorSupplier
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.Project
import org.springframework.stereotype.Component

/**
 * Manual job to collect Git commits for all builds of a project.
 */
@Component
class IndexableGitCommitJob(
    private val securityService: SecurityService,
    private val gitService: GitService,
    private val gitRepositoryClientFactory: GitRepositoryClientFactory,
    private val gitConfigService: GitConfigService,
) : JobOrchestratorSupplier {

    override val jobRegistrations: Collection<JobRegistration>
        get() = securityService.asAdmin {
            // Projects whose Git configuration cannot be read are skipped
            val projects = mutableListOf<Project>()
            gitService.forEachConfiguredProject { project, _ -> projects += project }
            projects
                .flatMap { project ->
                    sequenceOf(
                        createIndexableGitCommitJobRegistration(project, overrides = true, schedule = Schedule.NONE),
                        createIndexableGitCommitJobRegistration(
                            project,
                            overrides = false,
                            schedule = Schedule.EVERY_DAY
                        )
                    )
                }
        }

    private fun createIndexableGitCommitJobRegistration(
        project: Project,
        overrides: Boolean,
        schedule: Schedule
    ): JobRegistration =
        JobRegistration.of(
            object : Job {
                override fun getKey(): JobKey = if (overrides) {
                    GIT_COMMIT_REINDEX_JOB.getKey(project.name)
                } else {
                    GIT_COMMIT_INDEX_JOB.getKey(project.name)
                }

                override fun getTask() = JobRun { listener ->
                    collectIndexableGitCommitForProject(project, overrides, listener)
                }

                override fun getDescription(): String = if (overrides) {
                    "Re-indexation of Git commits for project ${project.name}"
                } else {
                    "Incremental indexation of Git commits for project ${project.name}"
                }

                override fun isDisabled(): Boolean = project.isDisabled

            }
        ).withSchedule(schedule)

    private fun collectIndexableGitCommitForProject(project: Project, overrides: Boolean, listener: JobRunListener) {
        securityService.asAdmin {
            val projectConfiguration = gitService.getProjectConfiguration(project)
            if (projectConfiguration != null) {
                val client: GitRepositoryClient =
                    gitRepositoryClientFactory.getClient(projectConfiguration.gitRepository, gitConfigService.gitConnectionConfig)
                gitService.forEachConfiguredBranchInProject(project) { branch, config ->
                    gitService.collectIndexableGitCommitForBranch(branch, client, config, overrides, listener)
                }
            }
        }
    }

    companion object {
        private val GIT_COMMIT_REINDEX_JOB =
            GIT_JOB_CATEGORY.getType("git-commit-reindexation").withName("Git commit re-indexation")
        private val GIT_COMMIT_INDEX_JOB =
            GIT_JOB_CATEGORY.getType("git-commit-indexation").withName("Git commit indexation")
    }
}