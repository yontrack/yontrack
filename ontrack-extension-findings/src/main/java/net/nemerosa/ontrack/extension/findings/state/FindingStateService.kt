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
 * A finding exposed only on disabled branches has no state at all, rather than reading as resolved:
 * nothing was fixed. Enabling one of these branches gives it back its state. A finding without any
 * exposure left, its branches having been deleted, is resolved.
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
     * @return State of the finding, `null` when it is exposed only on disabled branches
     */
    fun getFindingState(finding: Finding, date: LocalDate = Time.now.toLocalDate()): FindingState?

    /**
     * States of findings of one project.
     *
     * @param project Project the findings belong to
     * @param findings Findings of this project
     * @param date Day against which the expiry of the acceptances is evaluated
     * @return State per finding ID, the findings exposed only on disabled branches left out
     */
    fun getFindingStates(
        project: Project,
        findings: Collection<Finding>,
        date: LocalDate = Time.now.toLocalDate(),
    ): Map<Int, FindingState>

    /**
     * IDs of the branches of a project which count for the state of its findings.
     */
    fun getCountingBranchIds(project: Project): Set<Int>

    /**
     * IDs of the disabled branches of a project, which the findings do not take into account.
     */
    fun getDisabledBranchIds(project: Project): Set<Int>
}
