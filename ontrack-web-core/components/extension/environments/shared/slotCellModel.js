import dayjs from "dayjs"

/**
 * The reading half of the slot cell, kept apart from the drawing half.
 *
 * A cell has room for a handful of words and the rules deciding which words those are - which build
 * is "the" build, which promotion is "the" promotion, whether a deployment is still in flight - are
 * shared with the drawer, the journey chip and the matrix. Keeping them here means one place says
 * what a slot is showing, and it can be tested without rendering anything.
 */

/**
 * A slot pipeline is *in flight* while it is neither done nor cancelled.
 *
 * `Slot.currentPipeline` is simply the slot's most recent deployment whatever became of it, so a
 * caller asking "is something happening here?" has to ask this and not merely whether the field is
 * set.
 */
export const isInFlight = (pipeline) => !!pipeline && !pipeline.finished

/**
 * The deployment a slot's cell overlays, or `null` when the slot is idle.
 */
export const inFlightDeployment = (slot) =>
    isInFlight(slot?.currentPipeline) ? slot.currentPipeline : null

/**
 * The build a slot is holding: the last one actually deployed.
 *
 * Deliberately *not* the in-flight build. "89 · 1.3.9" is what is running in that environment right
 * now; "→ 107 candidate" is what is trying to replace it, and the cell shows the second as an
 * overlay on the first rather than in its place.
 */
export const deployedBuild = (slot) => slot?.lastDeployedPipeline?.build ?? null

/**
 * The top promotion of a build, or `null`.
 *
 * `promotionRuns(lastPerLevel: true)` comes back in the branch's promotion order, which the server
 * reads as `ORDER BY ORDERNB` over the branch's promotion levels - lowest rung first. The top
 * promotion is therefore the last entry, not the first.
 */
export const topPromotionRun = (build) => {
    const runs = build?.promotionRuns
    if (!runs || runs.length === 0) return null
    return runs[runs.length - 1]
}

/**
 * How a build is named in a cell: "104 · 1.4.3", or just "104" when it carries no release.
 */
export const buildLabel = (build) => {
    if (!build) return ''
    const release = build.releaseProperty?.value
    return release ? `${build.name} · ${release}` : build.name
}

/**
 * A compact age - "3d", "5h", "12m", "now" - for the one line a cell has.
 *
 * The drawer says "deployed 3 days ago by admin" through `TimestampText`; a cell cannot afford the
 * words, and a column of "3 days ago" next to "18 days ago" is harder to compare at a glance than
 * "3d" next to "18d".
 */
export const compactAge = (value, now = undefined) => {
    if (!value) return ''
    const then = dayjs(value)
    if (!then.isValid()) return ''
    const reference = now ? dayjs(now) : dayjs()
    const minutes = reference.diff(then, 'minute')
    if (minutes < 1) return 'now'
    if (minutes < 60) return `${minutes}m`
    const hours = reference.diff(then, 'hour')
    if (hours < 24) return `${hours}h`
    const days = reference.diff(then, 'day')
    if (days < 365) return `${days}d`
    return `${reference.diff(then, 'year')}y`
}

/**
 * How a slot is named on screen: "production · petclinic [canary]".
 *
 * The vocabulary decision of the redesign - the word *slot* belongs to Setup, and a reader is shown
 * the environment and the project instead. `slotName` in `SlotName.js` is the older
 * "environment/project" spelling and stays where it is used.
 */
export const slotDisplayName = (slot) => {
    if (!slot) return ''
    const base = `${slot.environment?.name} · ${slot.project?.name}`
    return slot.qualifier ? `${base} [${slot.qualifier}]` : base
}

/**
 * The same, without the project - for a drawer already sitting under a project's name.
 */
export const slotDisplayNameWithoutProject = (slot) => {
    if (!slot) return ''
    return slot.qualifier ? `${slot.environment?.name} [${slot.qualifier}]` : slot.environment?.name
}

/**
 * The overlay line of a cell: "→ 107 candidate", or `null` when nothing is in flight.
 */
export const inFlightLabel = (slot) => {
    const pipeline = inFlightDeployment(slot)
    if (!pipeline) return null
    return `→ ${pipeline.build?.name} ${pipeline.status?.toLowerCase()}`
}
