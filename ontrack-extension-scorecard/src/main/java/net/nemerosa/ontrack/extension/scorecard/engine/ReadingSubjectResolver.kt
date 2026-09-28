package net.nemerosa.ontrack.extension.scorecard.engine

import net.nemerosa.ontrack.extension.scorecard.model.NoEstateReadingSet
import net.nemerosa.ontrack.extension.scorecard.model.ReadingSet
import net.nemerosa.ontrack.model.structure.BranchModelMatcherService
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Component

/**
 * Resolves what a project is read on for a set: its scope, and its marker.
 */
@Component
class ReadingSubjectResolver(
    private val structureService: StructureService,
    private val branchModelMatcherService: BranchModelMatcherService,
) {

    fun resolve(set: ReadingSet, project: Project): ReadingSubject {
        val scope = scope(project)
        return when (set) {
            NoEstateReadingSet -> ReadingSubject(
                set = set,
                project = project,
                scope = scope,
                markerKind = MarkerKind.PROMOTION,
                marker = lastPromotionLevels(scope),
            )
        }
    }

    /**
     * The non-disabled branches matched by the branch model of the project, or all of them when
     * the project has no branch model — as the findings do.
     */
    private fun scope(project: Project): ReadingScope {
        val matcher = branchModelMatcherService.getBranchModelMatcher(project)
        val branches = structureService.getBranchesForProject(project.id)
            .filter { !it.isDisabled && (matcher == null || matcher.matches(it)) }
            .sortedBy { it.name }
        return ReadingScope(
            kind = if (matcher == null) ReadingScopeKind.ALL_BRANCHES else ReadingScopeKind.BRANCH_MODEL,
            branches = branches,
        )
    }

    /**
     * The promotion marker with no estate: the last promotion level of each branch in scope.
     * A branch without any promotion level is not read; no branch with a level, no marker.
     */
    private fun lastPromotionLevels(scope: ReadingScope): PromotionMarker? {
        val levels = scope.branches.mapNotNull { branch ->
            structureService.getPromotionLevelListForBranch(branch.id).lastOrNull()
        }
        return levels.takeIf { it.isNotEmpty() }?.let { PromotionMarker(it) }
    }
}
