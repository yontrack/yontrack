package net.nemerosa.ontrack.extension.findings.search

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.findings.FindingsExtensionFeature
import net.nemerosa.ontrack.extension.findings.model.Finding
import net.nemerosa.ontrack.extension.findings.model.FindingExposureState
import net.nemerosa.ontrack.extension.findings.repository.FindingRepository
import net.nemerosa.ontrack.extension.findings.security.ProjectFindingsView
import net.nemerosa.ontrack.extension.findings.state.FindingStateService
import net.nemerosa.ontrack.job.Schedule
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventListener
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Search documents for the findings, found by their external ID — a CVE, a rule ID.
 *
 * One document per finding, in the project of the finding, visible to the users granted
 * [ProjectFindingsView] on it. Its external ID is its title and its only identifier, and it is
 * not matched by similarity: a CVE similar to the one looked for is another vulnerability. Its
 * title is the text, and its last sighting its recency.
 *
 * The document carries the branches the finding is exposed on, so it is written again whenever
 * they may change, in the transaction of the change:
 *
 * - by the ingestion of a scan, for the findings it reports or resolves;
 * - when the project is updated (its name), or when a branch is updated, enabled, disabled or
 *   deleted, for the findings exposed on it.
 *
 * What changes without any event — an acceptance expiring, the branching model of the project
 * changing, the state of a finding in its project after the deletion of a branch — is caught up
 * by the reconciliation job, which runs every day.
 */
@Component
class FindingSearchIndexer(
    extensionFeature: FindingsExtensionFeature,
    private val findingRepository: FindingRepository,
    private val findingStateService: FindingStateService,
    private val structureService: StructureService,
    private val securityService: SecurityService,
    private val searchDocumentService: SearchDocumentService,
) : SearchDocumentIndexer, EventListener {

    private val logger: Logger = LoggerFactory.getLogger(FindingSearchIndexer::class.java)

    override val indexerName: String = "Security findings"

    override val searchResultType = SearchResultType(
        feature = extensionFeature.featureDescription,
        id = FINDING_SEARCH_RESULT_TYPE,
        name = "Security finding",
        description = "External ID of a security finding, like a CVE or the ID of a scanner rule",
        order = SearchResultType.ORDER_PROPERTIES + 70,
    )

    override val projectFunction = ProjectFindingsView::class.java

    override val fuzzyMatching: Boolean = false

    /**
     * Daily reconciliation, for what changes without an event: the expiry of the acceptances, the
     * branching model of the project
     */
    override val indexerSchedule: Schedule = Schedule.EVERY_DAY

    override fun indexAll(processor: (SearchDocument) -> Unit) {
        structureService.projectList.forEach { project ->
            findingRepository.findFindingsByProject(project.id()).chunked(CHUNK).forEach { findings ->
                documents(project, findings).forEach(processor)
            }
        }
    }

    /**
     * Writes the documents of findings of a project, whose content or exposure have just changed.
     *
     * A failure is logged and does not fail the caller: search must never block the ingestion
     * of a scan. The reconciliation job repairs the documents.
     *
     * @param deletedBranchId Branch being deleted, left out of the exposures
     */
    fun indexFindings(project: Project, findings: Collection<Finding>, deletedBranchId: Int? = null) {
        if (findings.isEmpty()) return
        try {
            findings.chunked(CHUNK).forEach { chunk ->
                searchDocumentService.index(documents(project, chunk, deletedBranchId))
            }
        } catch (any: Exception) {
            logger.error("[search][findings] Cannot index ${findings.size} findings of ${project.name}", any)
        }
    }

    override fun onEvent(event: Event) {
        when (event.eventType) {
            EventFactory.UPDATE_PROJECT -> {
                val project = event.getEntity<Project>(ProjectEntityType.PROJECT)
                indexFindings(project, findingRepository.findFindingsByProject(project.id()))
            }

            EventFactory.UPDATE_BRANCH,
            EventFactory.ENABLE_BRANCH,
            EventFactory.DISABLE_BRANCH -> {
                val branch = event.getEntity<Branch>(ProjectEntityType.BRANCH)
                onBranchChanged(branch.project, branch.id())
            }

            // Posted before the deletion: the branch is left out of the documents
            EventFactory.DELETE_BRANCH -> {
                val branchId = event.getIntValue("BRANCH_ID")
                onBranchChanged(event.getEntity(ProjectEntityType.PROJECT), branchId, deletedBranchId = branchId)
            }
        }
    }

    /**
     * Rewrites the documents of the findings exposed on a branch, resolved or not.
     */
    private fun onBranchChanged(project: Project, branchId: Int, deletedBranchId: Int? = null) {
        val findingIds = findingRepository.findExposuresByBranch(branchId).map { it.findingId }.distinct()
        if (findingIds.isNotEmpty()) {
            indexFindings(project, findingRepository.findFindingsByIds(findingIds), deletedBranchId)
        }
    }

    /**
     * Documents of findings of a project, with their exposure, as of today.
     *
     * Built as administrator: the documents are what all the users may see, their access being
     * filtered by the search.
     *
     * @param deletedBranchId Branch being deleted, left out of the exposures
     */
    private fun documents(
        project: Project,
        findings: Collection<Finding>,
        deletedBranchId: Int? = null,
    ): List<SearchDocument> = securityService.asAdmin {
        val today = Time.now.toLocalDate()
        val branches = structureService.getBranchesForProject(project.id)
            .filter { it.id() != deletedBranchId }
            .associateBy { it.id() }
        val states = findingStateService.getFindingStates(project, findings, today)
        val exposures = findingRepository.findExposuresByFindings(findings.map { it.id })
            .groupBy { it.findingId }
        findings.map { finding ->
            val exposed = (exposures[finding.id] ?: emptyList())
                .groupBy { it.branchId }
                .mapNotNull { (branchId, branchExposures) ->
                    val branch = branches[branchId] ?: return@mapNotNull null
                    FindingExposureState.of(branchExposures.map { it.stateOn(today) })
                        ?.takeIf { it != FindingExposureState.RESOLVED }
                        ?.let { state ->
                            FindingSearchResultBranch(id = branch.id(), name = branch.name, state = state)
                        }
                }
                .sortedBy { it.name }
            SearchDocument(
                type = FINDING_SEARCH_RESULT_TYPE,
                key = finding.id.toString(),
                projectId = project.id(),
                entity = null,
                title = finding.externalId,
                identifiers = listOf(finding.externalId),
                text = finding.title,
                data = mapOf(
                    SEARCH_RESULT_FINDING to FindingSearchResultFinding(
                        id = finding.id,
                        externalId = finding.externalId,
                        scanner = finding.scanner,
                        location = finding.location,
                        kind = finding.kind.name,
                        title = finding.title,
                        maxSeverity = finding.maxSeverity.name,
                        state = states[finding.id]?.name,
                    ),
                    SearchResult.SEARCH_RESULT_PROJECT to project.searchDocumentData(),
                    SEARCH_RESULT_BRANCHES to exposed,
                ).asJson(),
                updatedAt = finding.lastSeen,
            )
        }
    }

    companion object {
        const val SEARCH_RESULT_FINDING = "finding"
        const val SEARCH_RESULT_BRANCHES = "branches"

        /**
         * Number of findings whose documents are built together
         */
        private const val CHUNK = 500
    }
}

/**
 * Search result type of the findings.
 */
const val FINDING_SEARCH_RESULT_TYPE = "finding"

/**
 * Finding of a search result, what the result needs to be displayed and linked.
 */
data class FindingSearchResultFinding(
    val id: Int,
    val externalId: String,
    val scanner: String,
    val location: String,
    val kind: String,
    val title: String,
    val maxSeverity: String,
    val state: String?,
)

/**
 * Branch a found finding is exposed on, accepted or not.
 */
data class FindingSearchResultBranch(
    val id: Int,
    val name: String,
    val state: FindingExposureState,
)
