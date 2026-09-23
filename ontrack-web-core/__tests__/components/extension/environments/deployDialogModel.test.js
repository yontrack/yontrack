import {
    buildChoices,
    cancellationWarning,
    cancelledDeployment,
    notDeployableWarning,
    pipelineOnlyNote,
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

describe('an eligible slot the build cannot go to yet (#1851)', () => {

    const promotionRule = {id: 'r1', name: 'bronze', ruleId: 'promotion', ruleConfig: {promotion: 'BRONZE'}}
    const manualRule = {id: 'r2', name: 'approval', ruleId: 'manual', ruleConfig: {}}

    it('carries the deployability the server gave', () => {
        const choices = slotChoices([{
            eligible: true,
            deployable: false,
            nonDeployableRules: [{rule: promotionRule, reason: 'Build not promoted'}],
            pipelineOnlyRules: [],
            slot: slot('production', 20),
        }])
        expect(choices[0].eligible).toBe(true)
        expect(choices[0].deployable).toBe(false)
        expect(notDeployableWarning(choices[0])).toBe('Not deployable yet: Build not promoted')
    })

    it('joins several reasons, and names the rule when the server gives no reason', () => {
        const choice = slotChoices([{
            eligible: true,
            deployable: false,
            nonDeployableRules: [
                {rule: promotionRule, reason: 'Build not promoted'},
                {rule: {id: 'r3', name: 'lastRelease', ruleId: 'branchPattern'}, reason: null},
            ],
            slot: slot('production', 20),
        }])[0]
        expect(notDeployableWarning(choice)).toBe('Not deployable yet: Build not promoted; lastRelease')
    })

    it('warns about nothing when the build is deployable', () => {
        const choice = slotChoices([{eligible: true, deployable: true, slot: slot('staging', 10)}])[0]
        expect(notDeployableWarning(choice)).toBeNull()
    })

    it('warns about nothing on an ineligible slot, which explains itself otherwise', () => {
        const choice = slotChoices([{eligible: false, deployable: false, slot: slot('production', 20)}])[0]
        expect(notDeployableWarning(choice)).toBeNull()
    })

    it('only notes a rule decided on the deployment, rather than warning about it', () => {
        const choice = slotChoices([{
            eligible: true,
            deployable: true,
            nonDeployableRules: [],
            pipelineOnlyRules: [manualRule],
            slot: slot('production', 20),
        }])[0]
        expect(notDeployableWarning(choice)).toBeNull()
        expect(pipelineOnlyNote(choice)).toBe('Needs approval once started')
    })

    it('notes nothing when every rule is decided on the build', () => {
        const choice = slotChoices([{eligible: true, deployable: true, slot: slot('staging', 10)}])[0]
        expect(pipelineOnlyNote(choice)).toBeNull()
    })
})

describe('choosing a build, from a slot', () => {

    const theSlot = slot('production', 20)

    const build = (name) => ({id: name, name})

    it('offers every build the slot came back with', () => {
        // The query asks for deployable builds only, so the server has already applied the slot's
        // rules - see the note on `buildChoices` for why this direction does not explain refusals.
        const choices = buildChoices([build('107'), build('104')], theSlot)
        expect(choices.map(choice => choice.eligible)).toEqual([true, true])
        expect(choices.map(choice => choice.deployable)).toEqual([true, true])
        expect(choices.map(choice => choice.key)).toEqual(['107', '104'])
    })

    it('carries the cancellation the choice would cause', () => {
        const busy = slot('production', 20, {current: pipeline(12, 'RUNNING', '105')})
        const choices = buildChoices([build('107')], busy)
        expect(choices[0].cancels.number).toBe(12)
    })

    it('carries no cancellation when the slot is idle', () => {
        const choices = buildChoices([build('107')], theSlot)
        expect(choices[0].cancels).toBeNull()
    })
})
