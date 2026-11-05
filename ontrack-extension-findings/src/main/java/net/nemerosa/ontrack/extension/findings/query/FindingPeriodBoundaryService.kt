package net.nemerosa.ontrack.extension.findings.query

import net.nemerosa.ontrack.extension.findings.model.FindingPeriodBoundary
import net.nemerosa.ontrack.model.structure.StandardBuildFilterData

/**
 * Reading a period of an exposure against a build filter chosen by the reader. Nothing about a
 * boundary is stored.
 */
interface FindingPeriodBoundaryService {

    /**
     * The boundary of a period.
     *
     * @param view Period, as returned by [FindingQueryService.getExposurePeriods]
     * @param filter Filter, applied by name on the branch of the period
     * @return The boundary, `null` while the period is open, or for a user who cannot see the
     * findings of the project
     */
    fun getBoundary(view: FindingExposurePeriodView, filter: StandardBuildFilterData): FindingPeriodBoundary?
}
