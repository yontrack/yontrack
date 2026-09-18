import {
    canDeployFromJourney,
    decorationJourneyEntries,
} from "@components/extension/environments/journey/buildJourneyModel"

const slot = (id, {authorized = undefined} = {}) => ({
    id,
    qualifier: '',
    environment: {id: `env-${id}`, name: id, order: 10, image: false},
    project: {id: 1, name: 'petclinic'},
    authorizations: authorized === undefined ? undefined :
        [{name: 'pipeline', action: 'create', authorized}],
})

describe('the journey strip deploy gate', () => {

    /*
     * The gate is checked here rather than in a browser on purpose: the acceptance stack signs in
     * as a single admin, so a spec can show the button is *there* and can never show it is absent
     * for somebody without the right - which is the half that matters.
     */

    it('offers to deploy when at least one slot grants the right', () => {
        expect(canDeployFromJourney([
            {slot: slot('staging', {authorized: false})},
            {slot: slot('production', {authorized: true})},
        ])).toBe(true)
    })

    it('does not offer to deploy when no slot grants the right', () => {
        expect(canDeployFromJourney([
            {slot: slot('staging', {authorized: false})},
            {slot: slot('production', {authorized: false})},
        ])).toBe(false)
    })

    it('does not offer to deploy when the slots carry no authorization at all', () => {
        expect(canDeployFromJourney([{slot: slot('staging')}])).toBe(false)
    })

    it('does not offer to deploy on a project with no slot', () => {
        expect(canDeployFromJourney([])).toBe(false)
        expect(canDeployFromJourney(null)).toBe(false)
    })

    it('survives an entry with no slot', () => {
        expect(canDeployFromJourney([{}, {slot: slot('production', {authorized: true})}])).toBe(true)
    })
})

describe('the decoration stubs, read as journey entries', () => {

    const stub = (name, order) => ({
        environmentId: `env-${name}`,
        environmentName: name,
        environmentOrder: order,
        environmentImage: false,
        slotId: `slot-${name}`,
        qualifier: '',
        pipelineId: `pipeline-${name}`,
    })

    it('states the build is deployed, which is what the decoration means', () => {
        // `findHighestDeployedSlotPipelinesByBuildAndQualifier` answers "where is this build now",
        // so every stub is a slot holding the build (ADR 0007).
        const [entry] = decorationJourneyEntries([stub('production', 20)])
        expect(entry.state).toBe('DEPLOYED')
        expect(entry.slot.id).toBe('slot-production')
        expect(entry.slot.environment).toEqual({
            id: 'env-production',
            name: 'production',
            order: 20,
            image: false,
        })
        expect(entry.pipeline).toEqual({id: 'pipeline-production'})
    })

    it('orders the chips by environment, whatever order the server listed them in', () => {
        const entries = decorationJourneyEntries([stub('production', 20), stub('staging', 10)])
        expect(entries.map(entry => entry.slot.environment.name)).toEqual(['staging', 'production'])
    })

    it('draws nothing for a build deployed nowhere', () => {
        expect(decorationJourneyEntries([])).toEqual([])
        expect(decorationJourneyEntries(undefined)).toEqual([])
    })
})
