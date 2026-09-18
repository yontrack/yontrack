/**
 * What "What's blocking" is looking at, worked out apart from how it is drawn.
 *
 * The list answers job 3 of the redesign - *why is this deployment not moving?* - and the answer is
 * a function of which phase the deployment is in. Keeping that function here rather than inside the
 * component means the phase rule is stated once and can be tested without a DOM.
 */

/**
 * The checks of the phase a deployment is *currently* in.
 *
 * - A **candidate** is held up by the slot's admission rules and by its `CANDIDATE` workflows: those
 *   are what stand between it and running.
 * - A **running** deployment is held up by its `RUNNING` workflows only. Its admission rules were
 *   settled when it started, and re-listing them here would put passed history in front of the one
 *   thing that is not passing.
 * - A settled deployment (done or cancelled) is held up by nothing, and the list says so rather
 *   than showing an empty box.
 *
 * Each item is normalised to one shape so the component draws one kind of row:
 *
 * ```
 * {key, kind: 'rule' | 'workflow', ok, overridden, override, canBeOverridden,
 *  rule?, slotWorkflow?, needsInput}
 * ```
 *
 * @param {Object} deployment The deployment, with `status`, `admissionRules`, `requiredInputs` and
 *   the slot's `candidateWorkflows` / `runningWorkflows`.
 * @return {Array} The items of the current phase, failing ones first.
 */
export const currentPhaseItems = (deployment) => {
    if (!deployment) return []

    const awaitingInput = new Set(
        (deployment.requiredInputs ?? []).map(input => input.config?.id).filter(Boolean)
    )

    const items = []

    if (deployment.status === 'CANDIDATE') {
        (deployment.admissionRules ?? []).forEach(rule => {
            const config = rule.admissionRuleConfig
            items.push({
                key: `rule-${config?.id}`,
                kind: 'rule',
                ok: !!rule.check?.ok,
                reason: rule.check?.reason,
                overridden: !!rule.overridden,
                override: rule.override,
                canBeOverridden: !!rule.canBeOverridden,
                needsInput: awaitingInput.has(config?.id),
                rule,
            })
        });
        (deployment.slot?.candidateWorkflows ?? []).forEach(slotWorkflow => {
            items.push(workflowItem(slotWorkflow))
        })
    } else if (deployment.status === 'RUNNING') {
        (deployment.slot?.runningWorkflows ?? []).forEach(slotWorkflow => {
            items.push(workflowItem(slotWorkflow))
        })
    }

    // Failing first, then overridden, then passed - the order somebody reading the list needs, not
    // the order the server happened to return them in.
    return [...items].sort((a, b) => rank(a) - rank(b))
}

const workflowItem = (slotWorkflow) => {
    const instance = slotWorkflow.slotWorkflowInstanceForPipeline
    return {
        key: `workflow-${slotWorkflow.id}`,
        kind: 'workflow',
        // A workflow which has not started yet has no verdict, and "no verdict" is not a pass: the
        // deployment is still waiting on it.
        ok: !!instance?.check?.ok,
        reason: instance?.check?.reason,
        overridden: !!instance?.overridden,
        override: instance?.override,
        canBeOverridden: !!instance?.canBeOverridden,
        needsInput: false,
        slotWorkflow,
    }
}

const rank = (item) => {
    if (!item.ok) return 0
    if (item.overridden) return 1
    return 2
}

/**
 * "N of M checks passed" - the same sentence the mobile deployment screen uses, from the same
 * numbers, so the two UIs do not count differently.
 */
export const checksSummary = (items) => {
    const total = items.length
    const passed = items.filter(item => item.ok).length
    return {passed, total, text: `${passed} of ${total} checks passed`}
}

/**
 * Whether the current phase is clear - which is what lets the drawer say "nothing is blocking"
 * rather than showing a list of green ticks nobody needs to read.
 */
export const isClear = (items) => items.every(item => item.ok)
