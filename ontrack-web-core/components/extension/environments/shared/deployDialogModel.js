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
 * {key, label, eligible, nonEligibleRules, slot, build, cancels}
 * ```
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
            slot: entry.slot,
            build: null,
            cancels: cancelledDeployment(entry.slot),
        }))

/**
 * The choices offered when the dialog was opened **from a slot**: the builds that could come here.
 *
 * The eligibility of each one comes from its own `journey` - the entry for *this* slot - which is
 * the same answer, computed the same way, that the journey chip shows elsewhere. A build which has
 * already been here (superseded) or is here now is still a choice: redeploying is a thing people do.
 * Only a build the rules refuse is not.
 */
export const buildChoices = (builds = [], slot) =>
    builds.map(build => {
        const entry = (build.journey ?? []).find(it => it.slot?.id === slot?.id)
        const state = entry?.state
        return {
            key: build.id,
            eligible: state !== 'NOT_ELIGIBLE',
            state,
            nonEligibleRules: entry?.nonEligibleRules ?? [],
            slot,
            build,
            cancels: cancelledDeployment(slot),
        }
    })
