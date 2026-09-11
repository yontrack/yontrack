import "@testing-library/jest-dom"
import {fireEvent, render, screen, waitFor} from "@testing-library/react"

// antd's Drawer reads the responsive breakpoints; jsdom ships no `matchMedia`.
// Same stand-in as the other antd component tests.
Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: jest.fn().mockImplementation(query => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: jest.fn(),
        removeListener: jest.fn(),
        addEventListener: jest.fn(),
        removeEventListener: jest.fn(),
        dispatchEvent: jest.fn(),
    })),
})

/**
 * Starting a deployment from a phone.
 *
 * The sheet's whole job is the list: which environments this build can go to,
 * which it cannot, and - the part the desktop `Select` cannot do - *why* not.
 */

let slotsResult = {data: null, loading: false, error: null, finished: true}
const callGraphQL = jest.fn()

jest.mock("../../../components/services/GraphQL", () => ({
    // `dataFn` and `initialData` applied as the real hook applies them: what the
    // component reads off a query result is part of the contract it is written
    // against.
    useQuery: (query, {dataFn = data => data, initialData = null} = {}) => ({
        ...slotsResult,
        data: slotsResult.data ? dataFn(slotsResult.data) : initialData,
    }),
    callGraphQL: (...args) => callGraphQL(...args),
}))

/*
 * The rule summary is a `Dynamic`: a lazily imported component chosen by rule
 * id. Stubbed here so these tests assert what the sheet *hands* the shared
 * mapping - the rule's id and its configuration - rather than re-testing the
 * desktop components behind it. That the real mapping phrases a promotion rule
 * as "GOLD promotion is required" is asserted in `mobile.spec.js`, against a
 * real slot.
 */
jest.mock("../../../components/extension/environments/SlotAdmissionRuleSummary", () => ({
    __esModule: true,
    default: ({ruleId, ruleConfig}) => <span>{`${ruleId}:${JSON.stringify(ruleConfig)}`}</span>,
}))

import MobileDeploySheet from "@components/mobile/builds/MobileDeploySheet"

const build = {id: 100}

const slot = (id, environmentName, {qualifier = '', order = 100} = {}) => ({
    id,
    qualifier,
    environment: {id: `env-${environmentName}`, name: environmentName, order},
})

const eligible = (aSlot) => ({eligible: true, nonEligibleRules: [], slot: aSlot})

const ineligible = (aSlot, rules = []) => ({eligible: false, nonEligibleRules: rules, slot: aSlot})

const rule = (id, ruleId, ruleConfig) => ({id, name: id, ruleId, ruleConfig})

const withSlots = (...slots) => {
    slotsResult = {
        data: {eligibleSlotsForBuild: slots},
        loading: false,
        error: null,
        finished: true,
    }
}

const openSheet = (props = {}) =>
    render(<MobileDeploySheet build={build} open={true} onClose={() => {}} {...props}/>)

beforeEach(() => {
    slotsResult = {data: null, loading: false, error: null, finished: true}
    callGraphQL.mockReset()
    callGraphQL.mockResolvedValue({startSlotPipeline: {pipeline: {id: 'pipeline-1'}, errors: null}})
})

describe('the mobile deploy sheet', () => {

    it('offers an eligible slot', () => {
        withSlots(eligible(slot('slot-1', 'staging')))
        openSheet()
        expect(screen.getByTestId('mobile-deploy-slot-slot-1')).toHaveTextContent('staging')
        expect(screen.getByTestId('mobile-deploy-start-slot-1')).toBeInTheDocument()
    })

    it('names the qualifier beside the environment', () => {
        // A project can have two slots in one environment, told apart by nothing
        // else - so an environment name on its own would draw the same card
        // twice and say nothing about either (#1731).
        withSlots(
            eligible(slot('slot-1', 'production')),
            eligible(slot('slot-2', 'production', {qualifier: 'canary'})),
        )
        openSheet()
        expect(screen.getByTestId('mobile-deploy-slot-slot-2')).toHaveTextContent('production [canary]')
        expect(screen.getByTestId('mobile-deploy-slot-slot-1')).not.toHaveTextContent('[')
    })

    it('shows an ineligible slot rather than hiding it', () => {
        // Hiding it leaves a user wondering where an environment went, which is
        // exactly the failure this list exists to avoid.
        withSlots(ineligible(slot('slot-2', 'production'), [rule('r1', 'promotion', {promotion: 'GOLD'})]))
        openSheet()
        expect(screen.getByTestId('mobile-deploy-slot-slot-2')).toBeInTheDocument()
        expect(screen.getByTestId('mobile-deploy-ineligible-slot-2')).toBeInTheDocument()
    })

    it('says why an ineligible slot refuses, rule by rule', () => {
        withSlots(ineligible(slot('slot-2', 'production'), [
            rule('r1', 'promotion', {promotion: 'GOLD'}),
            rule('r2', 'branchPattern', {includes: ['main']}),
        ]))
        openSheet()
        expect(screen.getByTestId('mobile-deploy-reason-r1')).toHaveTextContent('promotion:{"promotion":"GOLD"}')
        expect(screen.getByTestId('mobile-deploy-reason-r2')).toHaveTextContent('branchPattern')
    })

    it('offers no way to deploy to a slot which refuses the build', () => {
        withSlots(ineligible(slot('slot-2', 'production'), [rule('r1', 'promotion', {promotion: 'GOLD'})]))
        openSheet()
        expect(screen.queryByTestId('mobile-deploy-start-slot-2')).not.toBeInTheDocument()
    })

    it('still says something when the server names no rule', () => {
        // `eligible: false` with an empty rule list is possible - a slot of
        // another project - and a card with a "Not eligible" tag and nothing
        // under it would read as a bug.
        withSlots(ineligible(slot('slot-2', 'production'), []))
        openSheet()
        expect(screen.getByTestId('mobile-deploy-slot-slot-2')).toHaveTextContent(/cannot be deployed here/i)
    })

    it('orders the slots the way a pipeline runs, then by qualifier', () => {
        withSlots(
            eligible(slot('prod', 'production', {order: 200})),
            eligible(slot('staging-b', 'staging', {order: 100, qualifier: 'b'})),
            eligible(slot('staging-a', 'staging', {order: 100, qualifier: 'a'})),
        )
        openSheet()
        const ids = Array.from(screen.getByTestId('mobile-deploy-slots').children)
            .map(item => item.getAttribute('data-testid'))
        expect(ids).toEqual([
            'mobile-deploy-slot-staging-a',
            'mobile-deploy-slot-staging-b',
            'mobile-deploy-slot-prod',
        ])
    })

    it('starts the deployment on the slot that was tapped', async () => {
        withSlots(eligible(slot('slot-1', 'staging')), eligible(slot('slot-2', 'production')))
        openSheet()
        fireEvent.click(screen.getByTestId('mobile-deploy-start-slot-2'))
        await waitFor(() => expect(callGraphQL).toHaveBeenCalled())
        expect(callGraphQL.mock.calls[0][0].variables).toEqual({slotId: 'slot-2', buildId: 100})
    })

    it('hands the new deployment back so the screen can go to it', async () => {
        // A deployment starts as a CANDIDATE: nothing has happened to the
        // environment yet, and everything still to happen is on its own screen.
        const onStarted = jest.fn()
        const onClose = jest.fn()
        withSlots(eligible(slot('slot-1', 'staging')))
        openSheet({onStarted, onClose})
        fireEvent.click(screen.getByTestId('mobile-deploy-start-slot-1'))
        await waitFor(() => expect(onStarted).toHaveBeenCalledWith('pipeline-1'))
        expect(onClose).toHaveBeenCalled()
    })

    it('shows a refusal from the server rather than closing on it', async () => {
        const onStarted = jest.fn()
        callGraphQL.mockResolvedValue({
            startSlotPipeline: {pipeline: null, errors: [{message: "Build is not eligible."}]},
        })
        withSlots(eligible(slot('slot-1', 'staging')))
        openSheet({onStarted})
        fireEvent.click(screen.getByTestId('mobile-deploy-start-slot-1'))
        expect(await screen.findByTestId('mobile-deploy-error')).toHaveTextContent('Build is not eligible.')
        expect(onStarted).not.toHaveBeenCalled()
    })

    it('says so when the project has no slot at all', () => {
        withSlots()
        openSheet()
        expect(screen.getByTestId('mobile-deploy-none')).toBeInTheDocument()
    })

    it('says so when the environments could not be read', () => {
        // The environments extension is licensed; on an instance without it the
        // field is absent from the schema and the query fails validation.
        slotsResult = {data: null, loading: false, error: "Validation error", finished: true}
        openSheet()
        expect(screen.getByTestId('mobile-deploy-error')).toHaveTextContent(/could not load the environments/i)
    })

    it('does not call a project slotless before the first answer arrives', () => {
        // `useQuery` starts with `loading` false and only flips it inside its
        // effect, so a sheet trusting `loading` alone would say "no slot" over
        // an answer on its way.
        slotsResult = {data: null, loading: false, error: null, finished: false}
        openSheet()
        expect(screen.queryByTestId('mobile-deploy-none')).not.toBeInTheDocument()
    })
})
