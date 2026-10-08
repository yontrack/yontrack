package net.nemerosa.ontrack.model.security

import net.nemerosa.ontrack.model.dashboards.DashboardEdition
import net.nemerosa.ontrack.model.dashboards.DashboardGlobal
import net.nemerosa.ontrack.model.dashboards.DashboardSharing
import net.nemerosa.ontrack.model.labels.LabelManagement
import net.nemerosa.ontrack.model.labels.ProjectLabelManagement

/**
 * The agent policy: what a registered agent may do, whatever its owner may do.
 *
 * An agent has no rights of its own. Its effective rights are **its owner's, intersected with
 * this allowlist** - see [AccountAuthenticatedUser.isGranted]. Nothing is assigned to the agent
 * itself, so demoting or deleting the owner bounds the agent with nothing to keep in sync.
 *
 * **Every function not in [allowedFunctions] is denied**, including the functions that extensions
 * add later. [deniedFunctions] only records the functions which were *decided* to be never granted,
 * so that a test can check that every registered function has been decided upon: a new function
 * fails it until someone adds it to one list or the other.
 *
 * The functions are listed by their fully qualified class names: the functions of the extensions
 * are not visible from the model, and the allowlist must stay in one place.
 *
 * Two allowed functions are gates rather than rights, and are checked further by the services:
 *
 * - `PromotionRunCreate` - only on a promotion level which admits agents
 *   (see [PromotionLevelAgentAdmission]);
 * - the slot pipeline functions (`SlotPipelineCreate`, `SlotPipelineStart`, `SlotPipelineFinish`,
 *   `SlotPipelineData`, and `SlotPipelineWorkflowRun` for the workflows these state changes trigger) -
 *   only on a slot which admits agents.
 */
object AgentPolicy {

    private const val ENVIRONMENTS = "net.nemerosa.ontrack.extension.environments.security"
    private const val NOTIFICATIONS = "net.nemerosa.ontrack.extension.notifications"
    private const val WORKFLOWS = "net.nemerosa.ontrack.extension.workflows.acl"

    /**
     * Functions an agent may hold, when its owner holds them.
     */
    val allowedFunctions: Set<String> = setOf(
        // Reading everything the owner can
        ProjectList::class.java.name,
        ProjectView::class.java.name,
        "$ENVIRONMENTS.EnvironmentList",
        "$ENVIRONMENTS.SlotView",
        "net.nemerosa.ontrack.extension.findings.security.ProjectFindingsView",
        "$NOTIFICATIONS.subscriptions.ProjectSubscriptionsRead",
        "net.nemerosa.ontrack.extension.scm.catalog.SCMCatalogAccessFunction",
        // Recording evidence
        BuildCreate::class.java.name,
        BuildConfig::class.java.name,
        ValidationRunCreate::class.java.name,
        ValidationRunStatusChange::class.java.name,
        ValidationRunStatusCommentEditOwn::class.java.name,
        // Gated: only on a promotion level which admits agents
        PromotionRunCreate::class.java.name,
        // Gated: only on a slot which admits agents
        "$ENVIRONMENTS.SlotPipelineCreate",
        "$ENVIRONMENTS.SlotPipelineStart",
        "$ENVIRONMENTS.SlotPipelineFinish",
        "$ENVIRONMENTS.SlotPipelineData",
        // The workflows of a slot run when its pipelines change state: never on their own
        "$ENVIRONMENTS.SlotPipelineWorkflowRun",
    )

    /**
     * Functions which have been decided to be never granted to an agent.
     *
     * Not needed for the check itself (an unlisted function is denied anyway), but it makes the
     * decision explicit.
     */
    val deniedFunctions: Set<String> = setOf(
        // Administration
        ProjectCreation::class.java.name,
        ApplicationManagement::class.java.name,
        // Reading the agent markers settings is gated by `BuildCreate` instead, see `AgentMarkersSettingsManager`
        GlobalSettings::class.java.name,
        AccountManagement::class.java.name,
        AccountGroupManagement::class.java.name,
        EventsAudit::class.java.name,
        LabelManagement::class.java.name,
        ValidationStampBulkUpdate::class.java.name,
        DashboardEdition::class.java.name,
        DashboardSharing::class.java.name,
        DashboardGlobal::class.java.name,
        "net.nemerosa.ontrack.extension.scorecard.security.EstateManagement",
        "$NOTIFICATIONS.recording.NotificationRecordingAccess",
        "$NOTIFICATIONS.subscriptions.GlobalSubscriptionsManage",
        "$NOTIFICATIONS.webhooks.WebhookManagement",
        "$ENVIRONMENTS.EnvironmentSave",
        "$ENVIRONMENTS.EnvironmentDelete",
        "$WORKFLOWS.WorkflowAudit",
        "$WORKFLOWS.WorkflowRegistration",
        "$WORKFLOWS.WorkflowStop",
        // Configuration of the project and of its branches, levels and stamps
        ProjectEdit::class.java.name,
        ProjectConfig::class.java.name,
        ProjectDisable::class.java.name,
        ProjectDelete::class.java.name,
        ProjectAuthorisationMgt::class.java.name,
        ProjectLabelManagement::class.java.name,
        BranchCreate::class.java.name,
        BranchEdit::class.java.name,
        BranchDisable::class.java.name,
        BranchFilterMgt::class.java.name,
        BranchDelete::class.java.name,
        PromotionLevelCreate::class.java.name,
        PromotionLevelEdit::class.java.name,
        PromotionLevelDelete::class.java.name,
        ValidationStampCreate::class.java.name,
        ValidationStampEdit::class.java.name,
        ValidationStampDelete::class.java.name,
        ValidationStampFilterCreate::class.java.name,
        ValidationStampFilterShare::class.java.name,
        ValidationStampFilterMgt::class.java.name,
        "$NOTIFICATIONS.subscriptions.ProjectSubscriptionsWrite",
        "$ENVIRONMENTS.SlotCreate",
        "$ENVIRONMENTS.SlotUpdate",
        "$ENVIRONMENTS.SlotDelete",
        // Removing or overriding the record
        BuildEdit::class.java.name,
        BuildDelete::class.java.name,
        PromotionRunDelete::class.java.name,
        ValidationRunStatusCommentEdit::class.java.name,
        "net.nemerosa.ontrack.extension.audittrail.security.EvidenceDelete",
        "$ENVIRONMENTS.SlotPipelineOverride",
        "$ENVIRONMENTS.SlotPipelineCancel",
        "$ENVIRONMENTS.SlotPipelineDelete",
    )

    /**
     * Does the policy allow an agent to hold this function?
     *
     * The function is matched exactly: holding `BuildCreate` does not make `ProjectEdit` allowed,
     * even though both imply `ProjectView`.
     */
    fun isGranted(fn: Class<*>): Boolean = fn.name in allowedFunctions

}
