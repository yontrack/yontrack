package net.nemerosa.ontrack.model.security

import net.nemerosa.ontrack.model.dashboards.DashboardEdition
import net.nemerosa.ontrack.model.dashboards.DashboardGlobal
import net.nemerosa.ontrack.model.dashboards.DashboardSharing
import net.nemerosa.ontrack.model.labels.LabelManagement
import net.nemerosa.ontrack.model.labels.ProjectLabelManagement

/**
 * The read-only token policy: what the holder of a read-only token may do, whatever its account may do.
 *
 * The effective rights of a read-only token are **those of its account, intersected with this
 * allowlist** - see [AccountAuthenticatedUser.isGranted]. For an agent, this comes on top of the
 * [agent policy][AgentPolicy], whose allowlist cannot be reused: it contains writes, and leaves out
 * some reads.
 *
 * **Every function not in [allowedFunctions] is denied**, including the functions which gate both
 * reads and writes (`AccountManagement`, `GlobalSettings`, `ApplicationManagement`) and the functions
 * that extensions add later. [deniedFunctions] only records the functions which were *decided* to be
 * never granted, so that a test can check that every registered function has been decided upon.
 *
 * This policy does not apply inside `asAdmin`, on which the reads rely: what stops the writes using
 * `asAdmin`, or checking no function, is the refusal of the mutations and of the REST writes on entry.
 *
 * The functions are listed by their fully qualified class names: the functions of the extensions
 * are not visible from the model, and the allowlist must stay in one place.
 */
object ReadOnlyTokenPolicy {

    private const val ENVIRONMENTS = "net.nemerosa.ontrack.extension.environments.security"
    private const val NOTIFICATIONS = "net.nemerosa.ontrack.extension.notifications"
    private const val WORKFLOWS = "net.nemerosa.ontrack.extension.workflows.acl"

    /**
     * Functions a read-only token may hold, when its account holds them.
     */
    val allowedFunctions: Set<String> = setOf(
        ProjectList::class.java.name,
        ProjectView::class.java.name,
        "$ENVIRONMENTS.EnvironmentList",
        "$ENVIRONMENTS.SlotView",
        "net.nemerosa.ontrack.extension.findings.security.ProjectFindingsView",
        "$NOTIFICATIONS.subscriptions.ProjectSubscriptionsRead",
        "net.nemerosa.ontrack.extension.scm.catalog.SCMCatalogAccessFunction",
        // Unlike an agent, for an owner who holds them
        EventsAudit::class.java.name,
        "$NOTIFICATIONS.recording.NotificationRecordingAccess",
        "$WORKFLOWS.WorkflowAudit",
    )

    /**
     * Functions which have been decided to be never granted to a read-only token.
     *
     * Not needed for the check itself (an unlisted function is denied anyway), but it makes the
     * decision explicit.
     */
    val deniedFunctions: Set<String> = setOf(
        // Gating both reads and writes
        AccountManagement::class.java.name,
        GlobalSettings::class.java.name,
        ApplicationManagement::class.java.name,
        // Administration
        ProjectCreation::class.java.name,
        AccountGroupManagement::class.java.name,
        LabelManagement::class.java.name,
        ValidationStampBulkUpdate::class.java.name,
        DashboardEdition::class.java.name,
        DashboardSharing::class.java.name,
        DashboardGlobal::class.java.name,
        "net.nemerosa.ontrack.extension.scorecard.security.EstateManagement",
        "$NOTIFICATIONS.subscriptions.GlobalSubscriptionsManage",
        "$NOTIFICATIONS.webhooks.WebhookManagement",
        "$ENVIRONMENTS.EnvironmentSave",
        "$ENVIRONMENTS.EnvironmentDelete",
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
        // Recording evidence
        BuildCreate::class.java.name,
        BuildConfig::class.java.name,
        ValidationRunCreate::class.java.name,
        ValidationRunStatusChange::class.java.name,
        ValidationRunStatusCommentEditOwn::class.java.name,
        PromotionRunCreate::class.java.name,
        "$ENVIRONMENTS.SlotPipelineCreate",
        "$ENVIRONMENTS.SlotPipelineStart",
        "$ENVIRONMENTS.SlotPipelineFinish",
        "$ENVIRONMENTS.SlotPipelineData",
        "$ENVIRONMENTS.SlotPipelineWorkflowRun",
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
     * Does the policy allow a read-only token to hold this function?
     *
     * The function is matched exactly: `BuildCreate` extends `ProjectView`, and must stay denied.
     */
    fun isGranted(fn: Class<*>): Boolean = fn.name in allowedFunctions

}
