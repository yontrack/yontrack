import {
    buildChoices,
    cancellationWarning,
    cancelledDeployment,
    slotChoices,
} from "@components/extension/environments/shared/deployDialogModel"

/**
 * What the deploy dialog offers, and what it warns about.
 *
 * The warning is the one this file exists for: starting a deployment has always cancelled the slot's
 * active one - `SlotServiceImpl.startPipeline`, "Cancelled by more recent pipeline." - and no screen
 * has ever said so.
 */

const pipeline = (number, status, buildName) => ({
    id: `p-${number}`,
    number,
    status,
    finished: status === 'DONE' || status === 'CANCELLED',
    build: {id: buildName, name: buildName},
})

const slot = (id, order, {qualifier = '', current = null} = {}) => ({
    id,
    qualifier,
    environment: {id: `env-${id}`, name: id, order},
    project: {id: 1, name: 'petclinic'},
    currentPipeline: current,
})

describe('the cancellation warning', () => {

    it('names the deployment a new one would cancel', () => {
        const production = slot('production', 20, {current: pipeline(12, 'RUNNING', '105')})
        expect(cancellationWarning(production))
            .toBe('Deployment #12 (build 105, RUNNING) will be cancelled.')
    })

    it('warns about nothing when the slot is idle', () => {
        expect(cancelledDeployment(slot('production', 20))).toBeNull()
        expect(cancellationWarning(slot('production', 20))).toBeNull()
    })

    it("warns about nothing when the slot's last deployment is finished", () => {
        const production = slot('production', 20, {current: pipeline(12, 'DONE', '105')})
        expect(cancellationWarning(production)).toBeNull()
    })
})

describe('choosing a slot, from a build', () => {

    it('orders them the way a delivery pipeline runs', () => {
        const choices = slotChoices([
            {eligible: true, slot: slot('production', 20)},
            {eligible: true, slot: slot('staging', 10)},
        ])
        expect(choices.map(choice => choice.key)).toEqual(['staging', 'production'])
    })

    it('breaks a tie on the qualifier, so two slots of one environment are stable', () => {
        const choices = slotChoices([
            {eligible: true, slot: slot('production-canary', 20, {qualifier: 'canary'})},
            {eligible: true, slot: slot('production', 20, {qualifier: ''})},
        ])
        expect(choices.map(choice => choice.key)).toEqual(['production', 'production-canary'])
    })

    it('lists an ineligible slot rather than hiding it, with the rules that refuse', () => {
        const rules = [{id: 'r1', name: 'gold', ruleId: 'promotion', ruleConfig: {promotion: 'GOLD'}}]
        const choices = slotChoices([
            {eligible: false, nonEligibleRules: rules, slot: slot('production', 20)},
        ])
        expect(choices[0].eligible).toBe(false)
        expect(choices[0].nonEligibleRules).toEqual(rules)
    })
})

describe('choosing a build, from a slot', () => {

    const theSlot = slot('production', 20)

    const build = (name, state, nonEligibleRules = []) => ({
        id: name,
        name,
        journey: [
            // Another slot's entry, to make sure the right one is picked out.
            {slot: {id: 'staging'}, state: 'DEPLOYED', nonEligibleRules: []},
            {slot: {id: 'production'}, state, nonEligibleRules},
        ],
    })

    it("reads each build's eligibility from its own journey entry for this slot", () => {
        const choices = buildChoices([build('107', 'ELIGIBLE')], theSlot)
        expect(choices[0].eligible).toBe(true)
        expect(choices[0].state).toBe('ELIGIBLE')
    })

    it('still offers a build which has been here before', () => {
        // Redeploying a superseded build is a thing people do, and it is not the rules refusing.
        const choices = buildChoices([build('104', 'SUPERSEDED')], theSlot)
        expect(choices[0].eligible).toBe(true)
    })

    it('refuses a build the rules refuse, with their reasons', () => {
        const rules = [{id: 'r1', name: 'gold', ruleId: 'promotion', ruleConfig: {promotion: 'GOLD'}}]
        const choices = buildChoices([build('108', 'NOT_ELIGIBLE', rules)], theSlot)
        expect(choices[0].eligible).toBe(false)
        expect(choices[0].nonEligibleRules).toEqual(rules)
    })

    it('carries the cancellation the choice would cause', () => {
        const busy = slot('production', 20, {current: pipeline(12, 'RUNNING', '105')})
        const choices = buildChoices([build('107', 'ELIGIBLE')], busy)
        expect(choices[0].cancels.number).toBe(12)
    })
})
