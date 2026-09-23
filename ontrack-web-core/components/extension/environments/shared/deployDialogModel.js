/**
 * The reading half of the deploy dialog.
 *
 * Two modes, one list. Whether the dialog was opened from a build (choose a slot) or from a slot
 * (choose a build), what it draws is a list of *choices*, each either deployable or refused with
 * the rules that refuse it. Normalising both shapes here is what lets the dialog have one list and
 * one row renderer instead of two of each.
 */

/**
 * One choice, whichever mode produced it.
 *
 * ```
 * {key, label, eligible, nonEligibleRules, deployable, nonDeployableRules, pipelineOnlyRules, slot, build, cancels}
 * ```
 *
 * `eligible` says a deployment can be started; `deployable` says it could also run now. An eligible
 * choice which is not deployable is still offered - its deployment waits as a candidate until the
 * rules in `nonDeployableRules` accept it - but it says so (#1851).
 *
 * `pipelineOnlyRules` are the rules decided on the deployment itself, like a manual approval: not
 * a reason to warn.
 *
 * `cancels` is the active deployment this choice would cancel, or null.
 */

/**
 * The deployment a new one on this slot would cancel, or `null`.
 *
 * `SlotServiceImpl.startPipeline` cancels the slot's active deployment with "Cancelled by more
 * recent pipeline." and no screen has ever said so. The dialog is where that stops being implicit.
 */
export const cancelledDeployment = (slot) => {
    const current = slot?.currentPipeline
    return current && !current.finished ? current : null
}

/**
 * *"Deployment #12 (build 105, RUNNING) will be cancelled."*
 */
export const cancellationWarning = (slot) => {
    const cancels = cancelledDeployment(slot)
    if (!cancels) return null
    return `Deployment #${cancels.number} (build ${cancels.build?.name}, ${cancels.status}) will be cancelled.`
}

/**
 * The choices offered when the dialog was opened **from a build**: the slots of the build's project.
 *
 * Ineligible slots are listed, not hidden - a user is never left wondering where an environment
 * went - each with the admission rules which actually refuse *this* build rather than the slot's
 * whole rule set.
 *
 * Ordered the way a delivery pipeline runs: environment order first, then qualifier so two slots of
 * one project in one environment come out stably.
 */
export const slotChoices = (eligibleSlots = []) =>
    [...eligibleSlots]
        .sort((a, b) =>
            (a.slot?.environment?.order ?? 0) - (b.slot?.environment?.order ?? 0) ||
            (a.slot?.qualifier ?? '').localeCompare(b.slot?.qualifier ?? '')
        )
        .map(entry => ({
            key: entry.slot.id,
            eligible: !!entry.eligible,
            nonEligibleRules: entry.nonEligibleRules ?? [],
            deployable: !!entry.eligible && (entry.deployable ?? true),
            nonDeployableRules: entry.nonDeployableRules ?? [],
            pipelineOnlyRules: entry.pipelineOnlyRules ?? [],
            slot: entry.slot,
            build: null,
            cancels: cancelledDeployment(entry.slot),
        }))

/**
 * The choices offered when the dialog was opened **from a slot**: the builds that could come here.
 *
 * Every one of them is deployable, and that asymmetry with [slotChoices] is deliberate rather than
 * an omission. "Non-eligible choices are listed with the rule that refuses them" is a question a
 * person asks about *a build* - "why can I not deploy 107 to production?" - and the server answers
 * it that way round, as `EligibleSlot.nonEligibleRules`. The other way round has no equivalent: a
 * slot's ineligible builds are every build of the project that is not on offer, which is a list of
 * everything rather than an explanation. So the slot asks for the builds its rules already accept,
 * and the explaining is done in the from-a-build direction, where the question is actually asked.
 */
export const buildChoices = (builds = [], slot) =>
    builds.map(build => ({
        key: build.id,
        eligible: true,
        nonEligibleRules: [],
        deployable: true,
        nonDeployableRules: [],
        pipelineOnlyRules: [],
        slot,
        build,
        cancels: cancelledDeployment(slot),
    }))

/**
 * *"Not deployable yet: Build not promoted"* - for an eligible choice whose deployment would wait as
 * a candidate, or `null`.
 *
 * A warning and not a refusal: the deployment can still be started, and runs once the rules accept
 * it. What it must never be again is silent, which is how a candidate for an unpromoted build came
 * to look like a slow deployment (#1851).
 */
export const notDeployableWarning = (choice) => {
    if (!choice?.eligible || choice.deployable) return null
    const reasons = (choice.nonDeployableRules ?? []).map(check => check.reason || check.rule?.name).filter(Boolean)
    return reasons.length > 0 ? `Not deployable yet: ${reasons.join('; ')}` : 'Not deployable yet'
}

/**
 * A neutral note about the rules decided on the deployment itself - *"Needs approval once
 * started"* - or `null`.
 *
 * Such a rule cannot be satisfied before the deployment exists, so it is no reason to warn.
 */
export const pipelineOnlyNote = (choice) => {
    if (!choice?.eligible) return null
    const notes = [...new Set((choice.pipelineOnlyRules ?? []).map(rule =>
        rule.ruleId === 'manual' ? 'Needs approval once started' : `${rule.name}: checked once started`
    ))]
    return notes.length > 0 ? notes.join('. ') : null
}
