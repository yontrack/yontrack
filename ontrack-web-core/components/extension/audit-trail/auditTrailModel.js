/**
 * View model of the audit trail page of a build: how a verification reads, and what each entry
 * says. Pure functions, so that the page only draws.
 */

/**
 * First of the positions given, ignoring the absent ones.
 */
const firstSeq = (...seqs) => {
    const present = seqs.filter(seq => seq !== null && seq !== undefined)
    return present.length > 0 ? Math.min(...present) : null
}

/**
 * The badge of a verification: one state, the worst found.
 *
 * - `broken` — the chain is broken, or an endorsement is invalid: the trail was tampered with. The
 *   seq is the first entry failing either check.
 * - `unendorsed` — the chain is intact, but its tail is covered by no endorsement.
 * - `partial` — the trail opened after its build was created (`trail.opened`).
 * - `intact` — everything checks.
 *
 * @param verification `AuditTrailVerification`
 * @return {{state: string, text: string, color: string}} State, text and antd colour of the badge
 */
export function trailBadge(verification) {
    if (!verification.chainIntact || !verification.endorsementsValid) {
        const seq = firstSeq(
            verification.chainIntact ? null : verification.firstBrokenSeq,
            verification.endorsementsValid ? null : verification.firstInvalidEndorsementSeq,
        )
        return {
            state: 'broken',
            text: seq ? `Broken at seq ${seq}` : 'Broken',
            color: 'error',
        }
    } else if (verification.unendorsedFromSeq) {
        return {
            state: 'unendorsed',
            text: `Unendorsed from seq ${verification.unendorsedFromSeq}`,
            color: 'warning',
        }
    } else if (verification.partial) {
        return {
            state: 'partial',
            text: 'Partial',
            color: 'processing',
        }
    } else {
        return {
            state: 'intact',
            text: 'Intact',
            color: 'success',
        }
    }
}

/**
 * The badge of the verification of the evidence, once it was asked for: `null` while it was not —
 * `missingEvidence` and `alteredEvidence` are then `null`.
 *
 * @param verification `AuditTrailVerification`
 * @return {{state: string, text: string, color: string}|null} State, text and antd colour of the
 * badge
 */
export function evidenceBadge(verification) {
    const missing = verification.missingEvidence
    const altered = verification.alteredEvidence
    if (!missing && !altered) {
        return null
    }
    const failures = []
    if (missing?.length > 0) {
        failures.push(`missing at seq ${missing.join(', ')}`)
    }
    if (altered?.length > 0) {
        failures.push(`altered at seq ${altered.join(', ')}`)
    }
    if (failures.length > 0) {
        return {
            state: 'failed',
            text: `Evidence ${failures.join('; ')}`,
            color: 'error',
        }
    } else {
        return {
            state: 'intact',
            text: 'Evidence intact',
            color: 'success',
        }
    }
}

/**
 * How each channel of an actor reads.
 */
const ACTOR_CHANNELS = {
    ui: 'UI',
    token: 'token',
    jwt: 'JWT',
    webhook: 'webhook',
    system: 'system',
}

/**
 * Who made the change of an entry, and how they got in: the account and its channel — the name of
 * the API token, never its value — or the system with its reason, on behalf of the actor which set
 * it off.
 *
 * @param actor Actor of an entry, as its JSON
 * @return {string} Text of the actor, empty when there is none
 */
export function actorText(actor) {
    if (!actor) {
        return ''
    }
    if (actor.account === 'system' && actor.via === 'system') {
        const reason = actor.system ? ` (${actor.system})` : ''
        const onBehalfOf = actor.onBehalfOf ? ` on behalf of ${actorText(actor.onBehalfOf)}` : ''
        return `System${reason}${onBehalfOf}`
    }
    const channel = ACTOR_CHANNELS[actor.via] ?? actor.via
    const token = actor.tokenName ? ` ${actor.tokenName}` : ''
    return `${actor.account} (${channel}${token})`
}

/**
 * What a deletion reaching the build beyond the deleted entity says, by the `reason` of its entry.
 */
const CASCADE_REASONS = {
    'cascade/validation-stamp-deleted': 'with its validation stamp',
    'cascade/promotion-level-deleted': 'with its promotion level',
    'cascade/target-build-deleted': 'with its target build',
}

const UNKNOWN = '?'

/**
 * Last segment of the FQCN of a property type.
 */
const propertyTypeName = (fqcn) => fqcn ? fqcn.substring(fqcn.lastIndexOf('.') + 1) : UNKNOWN

/**
 * A validation run, as an entry refers to it: its validation stamp and its order.
 */
const validationRunText = ({validationStamp, validationRun}) =>
    `${validationStamp?.name ?? UNKNOWN} #${validationRun?.order ?? UNKNOWN}`

/**
 * The build a link targets.
 */
const linkTargetText = ({target, qualifier}) =>
    `${target?.project ?? UNKNOWN} ${target?.name ?? UNKNOWN}${qualifier ? ` [${qualifier}]` : ''}`

/**
 * What a run info is attached to: the build, or one of its validation runs.
 */
const runnableText = ({runnable}) =>
    runnable?.type === 'validation_run' ? validationRunText(runnable) : 'the build'

/**
 * A deployment, as an entry refers to it: its number, its environment, and the qualifier of its slot.
 */
const deploymentText = ({deployment}) => {
    const qualifier = deployment?.slot?.qualifier
    return `Deployment #${deployment?.number ?? UNKNOWN} to ${deployment?.environment ?? UNKNOWN}${qualifier ? ` [${qualifier}]` : ''}`
}

/**
 * Fields of a build whose edition an entry records, in the order they are named.
 */
const BUILD_FIELDS = ['name', 'description', 'creation', 'creator']

const buildUpdatedText = ({old: before = {}, new: after = {}}) => {
    if (before.name !== after.name) {
        return `Build renamed from ${before.name} to ${after.name}`
    }
    const changed = BUILD_FIELDS.filter(field => JSON.stringify(before[field]) !== JSON.stringify(after[field]))
    return changed.length > 0 ? `Build updated: ${changed.join(', ')}` : 'Build updated'
}

/**
 * Summary of each type of entry, from its payload — see `TrailEntryTypes`.
 */
const SUMMARIES = {
    'trail.opened': () => 'Trail opened on a build created before it',
    'build.created': ({build}) => `Build ${build?.name ?? UNKNOWN} created`,
    'build.updated': buildUpdatedText,
    'property.set': ({propertyType}) => `Property ${propertyTypeName(propertyType)} set`,
    'property.deleted': ({propertyType}) => `Property ${propertyTypeName(propertyType)} deleted`,
    'link.added': (payload) => `Linked to ${linkTargetText(payload)}`,
    'link.removed': (payload) => `Link to ${linkTargetText(payload)} removed`,
    'validation.run': (payload) => `Validated ${validationRunText(payload)}: ${payload.status ?? UNKNOWN}`,
    'validation.status': (payload) => `Status of ${validationRunText(payload)} changed to ${payload.status ?? UNKNOWN}`,
    'validation.comment': (payload) => `Comment edited on ${validationRunText(payload)}`,
    'validation.data': (payload) => `Data of ${validationRunText(payload)} ${payload.data ? 'replaced' : 'removed'}`,
    'validation.deleted': (payload) => `Validation ${validationRunText(payload)} deleted`,
    'evidence.attached': (payload) => `Evidence ${payload.evidence?.fileName ?? UNKNOWN} attached to ${validationRunText(payload)}`,
    'evidence.deleted': (payload) => `Evidence ${payload.evidence?.fileName ?? UNKNOWN} deleted from ${validationRunText(payload)}`,
    'promotion.added': ({promotionLevel}) => `Promoted to ${promotionLevel?.name ?? UNKNOWN}`,
    'promotion.removed': ({promotionLevel}) => `Promotion to ${promotionLevel?.name ?? UNKNOWN} removed`,
    'runinfo.set': (payload) => `Run info set on ${runnableText(payload)}`,
    'runinfo.deleted': (payload) => `Run info deleted from ${runnableText(payload)}`,
    'deployment.created': (payload) => `${deploymentText(payload)} created`,
    'deployment.running': (payload) => `${deploymentText(payload)} running`,
    'deployment.done': (payload) => `${deploymentText(payload)} done`,
    'deployment.failed': (payload) => `${deploymentText(payload)} failed`,
    'deployment.cancelled': (payload) => `${deploymentText(payload)} cancelled`,
    'deployment.rule-data': (payload) => `${deploymentText(payload)}: data set for rule ${payload.rule?.name ?? UNKNOWN}`,
    'deployment.rule-overridden': (payload) => `${deploymentText(payload)}: rule ${payload.rule?.name ?? UNKNOWN} overridden`,
    'deployment.workflow-overridden': (payload) => `${deploymentText(payload)}: workflow ${payload.slotWorkflow?.workflow ?? UNKNOWN} overridden`,
    'deployment.deleted': (payload) => `${deploymentText(payload)} deleted`,
}

/**
 * One line saying what an entry records, from its type and its payload — with the cause of a
 * deletion which reached the build beyond the deleted entity. An entry of a type this UI does not
 * know reads as its type.
 *
 * @param entry `AuditTrailEntry`, with its `type` and its `payload`
 * @return {string} Summary of the entry
 */
export function entrySummary(entry) {
    const summary = SUMMARIES[entry.type]
    if (!summary) {
        return entry.type
    }
    const payload = entry.payload ?? {}
    const text = summary(payload)
    if (payload.reason) {
        return `${text}, ${CASCADE_REASONS[payload.reason] ?? payload.reason}`
    } else {
        return text
    }
}
