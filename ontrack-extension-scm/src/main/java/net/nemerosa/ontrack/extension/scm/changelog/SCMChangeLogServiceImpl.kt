package net.nemerosa.ontrack.extension.scm.changelog

import net.nemerosa.ontrack.extension.issues.model.ConfiguredIssueService
import net.nemerosa.ontrack.extension.issues.model.Issue
import net.nemerosa.ontrack.extension.scm.service.SCMDetector
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.BuildLink
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Service

@Service
class SCMChangeLogServiceImpl(
    private val scmDetector: SCMDetector,
    private val structureService: StructureService,
) : SCMChangeLogService {

    private fun getQualifiedBuild(
        build: Build,
        dependencyProject: String,
        dependencyQualifier: String,
    ): Build? =
        structureService.getQualifiedBuildsUsedBy(build, size = 1) {
            it.build.branch.project.name == dependencyProject &&
                    it.qualifier == dependencyQualifier
        }.pageItems.firstOrNull()?.build

    private fun getQualifiedBuild(
        build: Build,
        dependencyProject: String,
        dependencyQualifier: String,
        defaultQualifierFallback: Boolean,
    ): Build? =
        getQualifiedBuild(build, dependencyProject, dependencyQualifier)
            ?: if (defaultQualifierFallback && dependencyQualifier != BuildLink.DEFAULT) {
                getQualifiedBuild(build, dependencyProject, BuildLink.DEFAULT)
            } else {
                null
            }

    override suspend fun getChangeLogBoundaries(
        from: Build,
        to: Build,
        dependencies: List<DependencyLink>,
        defaultQualifierFallback: Boolean,
    ): Pair<Build, Build>? {
        if (from.project.id() != to.project.id()) {
            throw SCMChangeLogNotSameProjectException()
        } else if (dependencies.isEmpty()) {
            return from to to
        } else {
            val project = dependencies.first()
            val restProjects = dependencies.drop(1)

            // Gets the link to the dependency
            val linkedFrom = getQualifiedBuild(from, project.project, project.qualifier, defaultQualifierFallback)
            val linkedTo = getQualifiedBuild(to, project.project, project.qualifier, defaultQualifierFallback)

            // If one of the links is absent, giving up
            if (linkedFrom == null || linkedTo == null) {
                return null
            } else {
                return getChangeLogBoundaries(linkedFrom, linkedTo, restProjects)
            }
        }
    }

    override suspend fun getChangeLog(
        from: Build,
        to: Build,
        dependencies: List<DependencyLink>,
        defaultQualifierFallback: Boolean,
    ): SCMChangeLog? {
        val boundaries = getChangeLogBoundaries(from, to, dependencies, defaultQualifierFallback)
        return boundaries?.let { (f, t) ->
            computeChangeLog(f, t)
        }
    }

    private suspend fun computeChangeLog(from: Build, to: Build): SCMChangeLog? {

        val scm = scmDetector.getSCM(from.project)
        if (scm == null || scm !is SCMChangeLogEnabled) {
            return null
        }

        // Gets the two boundaries
        val fromCommit = scm.getBuildCommit(from)
        val toCommit = scm.getBuildCommit(to)
        if (fromCommit.isNullOrBlank() || toCommit.isNullOrBlank()) {
            throw SCMChangeLogNoCommitException()
        }

        // Getting the list of commits
        val commits = scm.getCommits(fromCommit, toCommit)
            // ... sorted from the newest to the oldest
            .sortedByDescending { it.timestamp }

        // Decoration of the commits
        val decoratedCommits = commits.map { commit ->
            SCMDecoratedCommit(
                project = from.project,
                commit = commit,
            )
        }

        // Getting the issue service
        val configuredIssueService: ConfiguredIssueService? = scm.getConfiguredIssueService()

        // OK
        return SCMChangeLog(
            from = from,
            to = to,
            fromCommit = fromCommit,
            toCommit = toCommit,
            commits = decoratedCommits,
            // Resolving the issues means calling a remote issue service, which can be slow.
            // This is done only if & when the issues are actually needed.
            issuesProvider = { configuredIssueService?.let { getIssues(it, commits) } },
        )
    }

    private fun getIssues(
        configuredIssueService: ConfiguredIssueService,
        commits: List<SCMCommit>,
    ): SCMChangeLogIssues {
        // Index of issues
        val index = mutableMapOf<String, Issue>()
        // For all commits in this commit log
        commits.forEach { commit ->
            val keys = configuredIssueService.extractIssueKeysFromMessage(commit.message)
            keys.forEach { key ->
                val exisingIssue = index[key]
                if (exisingIssue == null) {
                    val issue = configuredIssueService.getIssue(key)
                    if (issue != null) {
                        index[key] = issue
                    }
                }
            }
        }
        // OK
        return SCMChangeLogIssues(
            configuredIssueService.issueServiceConfigurationRepresentation,
            index.values.sortedBy { it.key }
        )
    }

}