package net.nemerosa.ontrack.extension.findings.query

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.findings.model.FindingPeriodBoundary
import net.nemerosa.ontrack.extension.findings.security.ProjectFindingsView
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.buildfilter.BuildFilterService
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.BuildDisplayNameService
import net.nemerosa.ontrack.model.structure.PropertyService
import net.nemerosa.ontrack.model.structure.StandardBuildFilterData
import net.nemerosa.ontrack.model.structure.StructureService
import net.nemerosa.ontrack.model.structure.ValidationRun
import net.nemerosa.ontrack.repository.BuildBound
import net.nemerosa.ontrack.repository.CoreBuildFilterInvalidException
import net.nemerosa.ontrack.repository.CoreBuildFilterRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import kotlin.jvm.optionals.getOrNull

@Service
@Transactional(readOnly = true)
class FindingPeriodBoundaryServiceImpl(
    private val securityService: SecurityService,
    private val structureService: StructureService,
    private val propertyService: PropertyService,
    private val buildFilterService: BuildFilterService,
    private val buildDisplayNameService: BuildDisplayNameService,
    private val coreBuildFilterRepository: CoreBuildFilterRepository,
) : FindingPeriodBoundaryService {

    override fun getBoundary(view: FindingExposurePeriodView, filter: StandardBuildFilterData): FindingPeriodBoundary? {
        if (view.period.ongoing) return null
        val branch = view.branch
        if (!securityService.isProjectFunctionGranted(branch.project.id(), ProjectFindingsView::class.java)) return null

        var notApplicable = FindingPeriodBoundaryComputation.sinceCriterionReason(filter)
            ?: validate(branch, filter)
        val build = if (notApplicable != null) {
            null
        } else {
            try {
                firstMatchingBuild(branch, view, filter)
            } catch (ex: CoreBuildFilterInvalidException) {
                notApplicable = ex.message ?: "This filter is not valid."
                null
            }
        }
        return FindingPeriodBoundaryComputation.boundary(
            period = view.period,
            hasNextPeriod = view.nextPeriod != null,
            notApplicable = notApplicable,
            match = build?.let { it to matchingSince(branch, it, filter) },
            now = Time.now,
        )
    }

    /**
     * The checks of the filter on the branch: a promotion level or a stamp it does not have, an
     * invalid regular expression...
     */
    private fun validate(branch: Branch, filter: StandardBuildFilterData): String? =
        buildFilterService.validateBuildFilterProviderData(
            branch,
            buildFilterService.standardFilterProviderData(filter.count).build().provider.type,
            filter.asJson(),
        )

    /**
     * From the build which ended the period to the one which started the next, in build order. When
     * one of them was purged, the time of its run takes its place, on the creation of the builds.
     */
    private fun firstMatchingBuild(
        branch: Branch,
        view: FindingExposurePeriodView,
        filter: StandardBuildFilterData,
    ): Build? {
        val from = view.endedBy?.let { BuildBound.ById(it.build.id()) }
            ?: BuildBound.ByTime(view.period.endedAt!!)
        val until = view.nextStartedBy?.let { BuildBound.ById(it.build.id()) }
            ?: view.nextPeriod?.let { BuildBound.ByTime(it.startedAt) }
        return coreBuildFilterRepository.firstMatchingBuild(
            branch = branch,
            data = filter,
            from = from,
            until = until,
            displayNameProperty = buildDisplayNameService.displayNameProperty,
        ) { type -> propertyService.getPropertyTypeByName<Any>(type) }
    }

    private fun matchingSince(branch: Branch, build: Build, filter: StandardBuildFilterData): LocalDateTime =
        FindingPeriodBoundaryComputation.matchingSince(
            filter = filter,
            created = build.signature.time,
            promoted = filter.withPromotionLevel?.takeIf { it.isNotBlank() }?.let { name ->
                structureService.findPromotionLevelByName(branch.project.name, branch.name, name).getOrNull()
                    ?.let { level -> structureService.getPromotionRunsForBuildAndPromotionLevel(build, level) }
                    ?.minOfOrNull { it.signature.time }
            },
            validated = filter.withValidationStamp?.takeIf { it.isNotBlank() }?.let { name ->
                FindingPeriodBoundaryComputation.validatedSince(
                    runs = validationRuns(branch, build, name),
                    status = filter.withValidationStampStatus,
                )
            },
        )

    private fun validationRuns(branch: Branch, build: Build, stampName: String): List<ValidationRun> =
        structureService.findValidationStampByName(branch.project.name, branch.name, stampName).getOrNull()
            ?.let { stamp ->
                structureService.getValidationRunsForBuildAndValidationStamp(build, stamp, count = MAX_RUNS)
            }
            ?: emptyList()

    private companion object {
        const val MAX_RUNS = 1000
    }
}
