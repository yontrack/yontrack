package net.nemerosa.ontrack.extension.findings.state

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.findings.model.Finding
import net.nemerosa.ontrack.extension.findings.model.FindingState
import net.nemerosa.ontrack.model.structure.Project
import java.time.LocalDate

/**
 * State of the findings in their project, rolled up from their exposure on the branches which
 * count: the branches matched by the branch model of the project (all of them when it has
 * none), the disabled branches left out.
 *
 * The state is evaluated when it is read: an acceptance past its expiry stops counting then,
 * without any job.
 *
 * The caller must be able to see the project.
 */
interface FindingStateService {

    /**
     * State of one finding.
     *
     * @param finding Finding
     * @param date Day against which the expiry of the acceptances is evaluated
     */
    fun getFindingState(finding: Finding, date: LocalDate = Time.now.toLocalDate()): FindingState

    /**
     * States of findings of one project.
     *
     * @param project Project the findings belong to
     * @param findings Findings of this project
     * @param date Day against which the expiry of the acceptances is evaluated
     * @return State per finding ID
     */
    fun getFindingStates(
        project: Project,
        findings: Collection<Finding>,
        date: LocalDate = Time.now.toLocalDate(),
    ): Map<Int, FindingState>
}
