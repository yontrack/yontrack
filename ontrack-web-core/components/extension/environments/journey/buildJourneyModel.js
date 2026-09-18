import {isAuthorized} from "@components/common/authorizations"

/**
 * May this user start a deployment of this build anywhere?
 *
 * The strip carries one Deploy button for the whole project rather than one per environment - the
 * dialog is where the environment is chosen - so the question the button is gated on is "anywhere",
 * not "here". The right is read from the slots, which is where it is granted: `Slot.authorizations`
 * answers `pipeline / create` per slot, and a project may well grant it on staging and refuse it on
 * production.
 *
 * @param {?Array} journey The entries of `Build.journey`
 * @return {boolean}
 */
export const canDeployFromJourney = (journey) =>
    (journey ?? []).some(entry => !!entry?.slot && !!isAuthorized(entry.slot, 'pipeline', 'create'))

/**
 * The decoration's stubs, read as journey entries.
 *
 * `BuildEnvironmentsDecorations` answers *"where is this build now"* - per qualifier, the highest
 * slot holding it (`findHighestDeployedSlotPipelinesByBuildAndQualifier`, and ADR 0007 on why the
 * decoration is that and not a deployment history). Every stub it sends is therefore a slot where
 * the build is deployed, which is what lets the renderer state the chip's state rather than ask the
 * server for it a second time.
 *
 * @param {?Array} data The decoration's payload
 * @return {Array} Entries shaped like `BuildJourneyData`, in environment order
 */
export const decorationJourneyEntries = (data) =>
    (data ?? []).map(stub => ({
        state: 'DEPLOYED',
        nonEligibleRules: [],
        pipeline: stub.pipelineId ? {id: stub.pipelineId} : null,
        slot: {
            id: stub.slotId,
            qualifier: stub.qualifier,
            environment: {
                id: stub.environmentId,
                name: stub.environmentName,
                order: stub.environmentOrder,
                image: stub.environmentImage,
            },
        },
    })).sort((a, b) => (a.slot.environment.order ?? 0) - (b.slot.environment.order ?? 0))

/**
 * A build's current deployments, read as journey entries.
 *
 * `Build.currentDeployments` answers *"where is this build deployed right now"* - one pipeline per
 * qualifier of the slots still holding it - which is the same question the decoration asks and
 * therefore gets the same answer: every entry is `DEPLOYED`. The build search "Deployments" column
 * and `ProjectPromotionWidget` read it (#1796), so both state deployment in the five words the rest
 * of the redesign uses rather than in an environment icon whose meaning had to be learnt.
 *
 * Sorted by environment order, lowest first, exactly as the journey strip is: a reader who has
 * learnt to read a strip of chips left to right reads this one the same way. The server answers
 * highest environment first, which is the order the column's predecessor needed to take its `[0]`.
 *
 * @param {?Array} deployments `Build.currentDeployments`
 * @return {Array} Entries shaped like `BuildJourneyData`, in environment order
 */
export const currentDeploymentJourneyEntries = (deployments) =>
    (deployments ?? [])
        // A pipeline with no slot has nothing a chip could name; the server does not send one, and
        // drawing a nameless chip if it ever did would be worse than drawing none.
        .filter(pipeline => !!pipeline?.slot)
        .map(pipeline => ({
            state: 'DEPLOYED',
            nonEligibleRules: [],
            pipeline: {id: pipeline.id, number: pipeline.number, status: pipeline.status},
            slot: pipeline.slot,
        }))
        .sort((a, b) => (a.slot.environment?.order ?? 0) - (b.slot.environment?.order ?? 0))
