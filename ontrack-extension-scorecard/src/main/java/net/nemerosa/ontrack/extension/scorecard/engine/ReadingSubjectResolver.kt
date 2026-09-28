package net.nemerosa.ontrack.extension.scorecard.engine

import net.nemerosa.ontrack.extension.environments.Slot
import net.nemerosa.ontrack.extension.environments.service.EnvironmentService
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.extension.scorecard.estates.EstateEnvironmentMarker
import net.nemerosa.ontrack.extension.scorecard.estates.EstatePromotionMarker
import net.nemerosa.ontrack.extension.scorecard.license.ScorecardLicense
import net.nemerosa.ontrack.extension.scorecard.model.EstateReadingSet
import net.nemerosa.ontrack.extension.scorecard.model.NoEstateReadingSet
import net.nemerosa.ontrack.extension.scorecard.model.ReadingSet
import net.nemerosa.ontrack.extension.scorecard.model.ReadingUnknownReason
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
    private val environmentService: EnvironmentService,
    private val slotService: SlotService,
    private val scorecardLicense: ScorecardLicense,
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

            is EstateReadingSet -> when (val marker = set.estate.marker) {
                // Default marker: the highest-ordered environment where the project owns a slot,
                // else the promotion rule of the no-estate set
                null -> defaultEnvironmentSlot(project)
                    ?.let { slot -> environmentSubject(set, project, scope) { slot } }
                    ?: ReadingSubject(
                        set = set,
                        project = project,
                        scope = scope,
                        markerKind = MarkerKind.PROMOTION,
                        marker = lastPromotionLevels(scope),
                    )

                is EstatePromotionMarker -> ReadingSubject(
                    set = set,
                    project = project,
                    scope = scope,
                    markerKind = MarkerKind.PROMOTION,
                    marker = namedPromotionLevels(scope, marker.levelName),
                )

                is EstateEnvironmentMarker -> environmentSubject(set, project, scope) {
                    namedEnvironmentSlot(project, marker)
                }
            }
        }
    }

    /**
     * Subject read up to the deployments done in a slot: `NO_MARKER` when there is no slot, and
     * `NOT_LICENSED` without the licence of the environments, whatever the slot.
     */
    private fun environmentSubject(
        set: ReadingSet,
        project: Project,
        scope: ReadingScope,
        slot: () -> Slot?,
    ): ReadingSubject {
        val licensed = scorecardLicense.environmentsEnabled
        return ReadingSubject(
            set = set,
            project = project,
            scope = scope,
            markerKind = MarkerKind.ENVIRONMENT,
            marker = if (licensed) slot()?.let { EnvironmentMarker(it) } else null,
            noMarkerReason = if (licensed) ReadingUnknownReason.NO_MARKER else ReadingUnknownReason.NOT_LICENSED,
        )
    }

    /**
     * The default environment marker of an estate: the slot, with the default qualifier, of the
     * highest-ordered environment where the project owns one. `null` when it owns none.
     */
    private fun defaultEnvironmentSlot(project: Project): Slot? =
        slotService.findSlotsByProject(project, qualifier = Slot.DEFAULT_QUALIFIER)
            .maxByOrNull { it.environment.order }

    /**
     * The slot of the project in the environment of the marker, with its qualifier. `null` when the
     * environment does not exist, or when the project has no slot in it for this qualifier.
     */
    private fun namedEnvironmentSlot(project: Project, marker: EstateEnvironmentMarker): Slot? =
        environmentService.findByName(marker.environment)?.let { environment ->
            slotService.findSlotByProjectAndEnvironment(environment, project, marker.qualifier)
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

    /**
     * The promotion marker of an estate: the promotion level of the given name on each branch in scope.
     * A branch without such a level is not read; no branch with it, no marker.
     */
    private fun namedPromotionLevels(scope: ReadingScope, levelName: String): PromotionMarker? {
        val levels = scope.branches.mapNotNull { branch ->
            structureService.findPromotionLevelByName(branch.project.name, branch.name, levelName).orElse(null)
        }
        return levels.takeIf { it.isNotEmpty() }?.let { PromotionMarker(it) }
    }
}
