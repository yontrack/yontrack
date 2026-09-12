package net.nemerosa.ontrack.extension.workflows.acl

import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstance

/**
 * Authorization of the _reads_ of a [WorkflowInstance].
 *
 * A workflow instance has **no owning entity in the model**: `WKF_INSTANCES` carries no project
 * column and the only project handle is the event's entity map. The owner is therefore derived
 * from the event at read time rather than stored - an instance can even acquire entities mid-run,
 * when a node's output event is merged back into it, so a stored owner would go stale.
 *
 * The rule is:
 *
 * ```
 * WorkflowAudit granted
 *   || (a project is resolvable from the event && ProjectView granted on it)
 * ```
 *
 * [WorkflowAudit] is an **override, not a fallback**: an administrator who can list instances on
 * the audit page must be able to open every one of them, including the ones which name no project
 * at all.
 */
interface WorkflowInstanceAccessService {

    /**
     * Can the current user read this [instance]?
     *
     * The boolean form, for a by-id read which must answer `null` rather than throw - an exception
     * on a by-id query leaks the existence of the instance.
     */
    fun isWorkflowInstanceAccessible(instance: WorkflowInstance): Boolean

    /**
     * Checks that the current user can read this [instance], throwing an
     * [org.springframework.security.access.AccessDeniedException] if not.
     *
     * The throwing form, for a caller which already holds the instance and for which a denial is an
     * error rather than an empty answer.
     */
    fun checkWorkflowInstanceAccess(instance: WorkflowInstance)

}
