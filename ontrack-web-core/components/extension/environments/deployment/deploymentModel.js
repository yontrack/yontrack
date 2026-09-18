import {phaseItems} from "@components/extension/environments/shared/whatsBlockingModel"
import {slotPipelineStatusLabels} from "@components/extension/environments/SlotPipelineStatusLabel"

/**
 * What the deployment page is looking at, worked out apart from how it is drawn.
 *
 * The page answers three questions in three places, and each one is a function of the deployment
 * alone: *where is it* (the steps bar), *what is holding it up* (the current phase, then the phases
 * already over) and *how did it get here* (the audit timeline). Keeping those three functions here
 * means each is stated once and can be tested without a DOM.
 */

/** The order a deployment goes through its phases, which is also the order they are drawn in. */
export const PHASES = ['CANDIDATE', 'RUNNING', 'DONE']

/**
 * The horizontal `Steps` bar: Candidate → Running → Deployed, or → Cancelled.
 *
 * A cancelled deployment does not get a fourth step. It **replaces** the step it never reached, and
 * that step is marked as an error: the bar says where the deployment stopped, and a cancelled
 * deployment stopped instead of arriving. The alternative - four steps, one of them always dead -
 * would draw "Deployed" on a deployment which never will be.
 *
 * @param {Object} deployment The deployment, with `status` and `changes`.
 * @return {{current: number, status: string, items: Array}} `current` and `status` are Ant Design's
 *   own `Steps` props; `items` carries a `key` per step so a test can name one.
 */
export const deploymentSteps = (deployment) => {
    const status = deployment?.status

    const candidate = {key: 'CANDIDATE', title: slotPipelineStatusLabels.CANDIDATE}
    const running = {key: 'RUNNING', title: slotPipelineStatusLabels.RUNNING}
    const done = {key: 'DONE', title: slotPipelineStatusLabels.DONE}

    if (status === 'CANCELLED') {
        // Where it was when it was cancelled decides which step the cancellation replaces: a
        // candidate never started, a running deployment never finished.
        const reachedRunning = !!statusChange(deployment, 'RUNNING')
        const cancelled = {key: 'CANCELLED', title: slotPipelineStatusLabels.CANCELLED}
        const items = reachedRunning ? [candidate, running, cancelled] : [candidate, cancelled]
        return {items, current: items.length - 1, status: 'error'}
    }

    const items = [candidate, running, done]
    const current = status === 'CANDIDATE' ? 0 : status === 'RUNNING' ? 1 : 2
    return {items, current, status: status === 'DONE' ? 'finish' : 'process'}
}

/**
 * The status change which took a deployment to a given status, if it ever got there.
 */
export const statusChange = (deployment, status) =>
    (deployment?.changes ?? []).find(change => change.type === 'STATUS' && change.status === status)

/**
 * The phases to draw **below** the current one: the ones this deployment has already been through.
 *
 * A candidate has none - it has not been anywhere yet. A running deployment has its candidate phase
 * behind it. A finished one has all of them, which is why a `DONE` or `CANCELLED` deployment reads
 * as a complete record rather than as a screen with its middle missing.
 *
 * A phase with no checks at all is left out rather than drawn empty: "Running phase (0 checks)" is
 * a row that tells nobody anything.
 *
 * @param {Object} deployment The deployment.
 * @return {Array} `{phase, title, items}`, in the order the deployment went through them.
 */
export const earlierPhases = (deployment) => {
    if (!deployment) return []

    const reached = PHASES.filter(phase => {
        if (phase === 'CANDIDATE') return true
        /*
         * A deployed deployment shows its running phase even when it never *was* running: a forced
         * one goes straight from candidate to deployed, and its RUNNING workflows - which did not
         * run - are part of the record somebody forcing it left behind. A cancelled one is the
         * other way round: it stopped, and the phase it never reached is not history, it is a phase
         * that did not happen.
         */
        if (phase === 'RUNNING') {
            return deployment.status === 'RUNNING' ||
                deployment.status === 'DONE' ||
                !!statusChange(deployment, 'RUNNING')
        }
        return deployment.status === 'DONE'
    })

    // The phase the deployment is *in* is shown at the top, under "What's blocking", and is not
    // repeated here.
    const current = deployment.status === 'CANDIDATE' ? 'CANDIDATE'
        : deployment.status === 'RUNNING' ? 'RUNNING'
            : null

    return reached
        .filter(phase => phase !== current)
        .map(phase => {
            const items = phaseItems(deployment, phase)
            return {
                phase,
                title: `${slotPipelineStatusLabels[phase]} phase`,
                items,
            }
        })
        .filter(entry => entry.items.length > 0)
}

/**
 * The audit timeline, newest first: every change the deployment recorded, as one readable line.
 *
 * This is the auditor's half of the page, and the reason the redesign gives the side column to it
 * rather than to more of the same checks: who moved this deployment, when, and - for an override -
 * what they said about it. The server already stores all of it in `SlotPipeline.changes`; nothing
 * on the old page showed the overrides at all.
 *
 * @param {Object} deployment The deployment, with `changes`.
 * @return {Array} `{key, title, message, user, timestamp, type, status}`, newest first.
 */
export const timelineEntries = (deployment) => {
    const changes = deployment?.changes ?? []
    return [...changes]
        .sort((a, b) => String(b.timestamp).localeCompare(String(a.timestamp)))
        .map((change, index) => ({
            key: change.id ?? `${change.type}-${change.timestamp}-${index}`,
            type: change.type,
            status: change.status,
            user: change.user,
            timestamp: change.timestamp,
            title: timelineTitle(change),
            // The override message is the whole point of an override entry; for a status change it
            // is the forcing message, which matters for exactly the same reason.
            message: change.overrideMessage || null,
        }))
}

/**
 * One line's headline.
 *
 * A status change is named by the status it reached - "Running", "Deployed" - because that is the
 * word the steps bar above uses and the two must read the same. Everything else is named by the
 * message the server recorded with it.
 */
const timelineTitle = (change) => {
    if (change.type === 'STATUS') {
        return slotPipelineStatusLabels[change.status] ?? change.status ?? 'Status change'
    }
    return change.message || defaultTitles[change.type] || 'Change'
}

const defaultTitles = {
    RULE_DATA: 'Rule data entered',
    RULE_OVERRIDDEN: 'Rule overridden',
    WORKFLOW_OVERRIDDEN: 'Workflow overridden',
}

/**
 * Whether the deployment is over - which decides that the page offers no action at all and shows
 * every phase read-only.
 */
export const isSettled = (deployment) =>
    deployment?.status === 'DONE' || deployment?.status === 'CANCELLED'
