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
