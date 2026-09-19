import "@testing-library/jest-dom"
import {fireEvent, render, screen, waitFor} from "@testing-library/react"

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

let queryResult = {data: null, loading: false, error: null, finished: true}
const callGraphQL = jest.fn()

jest.mock("../../../../components/services/GraphQL", () => ({
    // `dataFn` and `initialData` applied as the real hook applies them: what the component reads off
    // a query result is part of the contract it is written against.
    useQuery: (query, {dataFn = data => data, initialData = null} = {}) => ({
        ...queryResult,
        data: queryResult.data ? dataFn(queryResult.data) : initialData,
    }),
    callGraphQL: (...args) => callGraphQL(...args),
}))

// `SlotAdmissionRuleSummary` goes through `Dynamic`, whose webpack context does not exist under Jest.
jest.mock("../../../../components/extension/environments/SlotAdmissionRuleSummary", () => ({
    __esModule: true,
    default: ({ruleConfig}) => <span>{`${ruleConfig?.promotion ?? ''} promotion is required`}</span>,
}))

import DeployDialog from "@components/extension/environments/shared/DeployDialog"

const granted = (name, action) => ({name, action, authorized: true})
const refused = (name, action) => ({name, action, authorized: false})

const slot = (id, order, {qualifier = '', current = null, authorizations = [granted('pipeline', 'create')]} = {}) => ({
    id,
    qualifier,
    environment: {id: `env-${id}`, name: id, order},
    project: {id: 1, name: 'petclinic'},
    authorizations,
    currentPipeline: current,
})

const pipeline = (number, status, buildName) => ({
    id: `p-${number}`,
    number,
    status,
    finished: status === 'DONE' || status === 'CANCELLED',
    build: {id: buildName, name: buildName},
})

const goldRule = {id: 'r1', name: 'gold', ruleId: 'promotion', ruleConfig: {promotion: 'GOLD'}}

const withSlots = (...entries) => {
    queryResult = {data: {eligibleSlotsForBuild: entries}, loading: false, error: null, finished: true}
}

const openFromBuild = (dialogOverrides = {}) => {
    const dialog = {
        context: {build: {id: 100, name: '107'}},
        open: true,
        close: jest.fn(),
        onSuccess: jest.fn(),
        ...dialogOverrides,
    }
    render(<DeployDialog dialog={dialog}/>)
    return dialog
}

beforeEach(() => {
    queryResult = {data: null, loading: false, error: null, finished: true}
    callGraphQL.mockReset()
    callGraphQL.mockResolvedValue({startSlotPipeline: {pipeline: {id: 'pipeline-1'}, errors: null}})
})

describe('the deploy dialog, opened from a build', () => {

    it('offers an eligible slot', () => {
        withSlots({eligible: true, nonEligibleRules: [], slot: slot('staging', 10)})
        openFromBuild()
        expect(screen.getByTestId('deploy-dialog-slot-staging')).toHaveTextContent('staging')
        expect(screen.getByTestId('deploy-dialog-slot-start-staging')).toBeInTheDocument()
    })

    it('lists an ineligible slot rather than hiding it', () => {
        // A user is never left wondering where an environment went.
        withSlots({eligible: false, nonEligibleRules: [goldRule], slot: slot('production', 20)})
        openFromBuild()
        expect(screen.getByTestId('deploy-dialog-slot-ineligible-production')).toHaveTextContent('Not eligible')
        expect(screen.queryByTestId('deploy-dialog-slot-start-production')).not.toBeInTheDocument()
    })

    it('says why it refuses, rule by rule and in words', () => {
        withSlots({eligible: false, nonEligibleRules: [goldRule], slot: slot('production', 20)})
        openFromBuild()
        expect(screen.getByTestId('deploy-dialog-slot-reasons-production'))
            .toHaveTextContent('GOLD promotion is required')
    })

    it('still says something when the server names no rule', () => {
        withSlots({eligible: false, nonEligibleRules: [], slot: slot('production', 20)})
        openFromBuild()
        expect(screen.getByTestId('deploy-dialog-slot-reasons-production'))
            .toHaveTextContent('This build cannot be deployed here.')
    })

    it('warns, by name, about the deployment it would cancel', () => {
        // This is what starting a deployment has always done, silently.
        withSlots({
            eligible: true,
            nonEligibleRules: [],
            slot: slot('production', 20, {current: pipeline(12, 'RUNNING', '105')}),
        })
        openFromBuild()
        expect(screen.getByTestId('deploy-dialog-slot-cancels-production'))
            .toHaveTextContent('Deployment #12 (build 105, RUNNING) will be cancelled.')
    })

    it('warns about nothing when the slot is idle', () => {
        withSlots({eligible: true, nonEligibleRules: [], slot: slot('staging', 10)})
        openFromBuild()
        expect(screen.queryByTestId('deploy-dialog-slot-cancels-staging')).not.toBeInTheDocument()
    })

    it('hides the action from a user without the right', () => {
        withSlots({
            eligible: true,
            nonEligibleRules: [],
            slot: slot('staging', 10, {authorizations: [refused('pipeline', 'create')]}),
        })
        openFromBuild()
        expect(screen.getByTestId('deploy-dialog-slot-staging')).toBeInTheDocument()
        expect(screen.queryByTestId('deploy-dialog-slot-start-staging')).not.toBeInTheDocument()
    })

    it('starts the deployment on the slot that was chosen', async () => {
        withSlots(
            {eligible: true, nonEligibleRules: [], slot: slot('staging', 10)},
            {eligible: true, nonEligibleRules: [], slot: slot('production', 20)},
        )
        const dialog = openFromBuild()
        fireEvent.click(screen.getByTestId('deploy-dialog-slot-start-production'))
        await waitFor(() => expect(callGraphQL).toHaveBeenCalled())
        expect(callGraphQL.mock.calls[0][0].variables).toEqual({slotId: 'production', buildId: 100})
        await waitFor(() => expect(dialog.onSuccess).toHaveBeenCalledWith('pipeline-1'))
    })

    it('shows a refusal from the server rather than closing on it', async () => {
        callGraphQL.mockResolvedValue({
            startSlotPipeline: {pipeline: null, errors: [{message: "Build is not eligible."}]},
        })
        withSlots({eligible: true, nonEligibleRules: [], slot: slot('staging', 10)})
        const dialog = openFromBuild()
        fireEvent.click(screen.getByTestId('deploy-dialog-slot-start-staging'))
        expect(await screen.findByTestId('deploy-dialog-error')).toHaveTextContent('Build is not eligible.')
        expect(dialog.onSuccess).not.toHaveBeenCalled()
        expect(dialog.close).not.toHaveBeenCalled()
    })

    it('says so when the project has no slot', () => {
        withSlots()
        openFromBuild()
        expect(screen.getByTestId('deploy-dialog-empty'))
            .toHaveTextContent("This build's project has no deployment slot.")
    })

    it('does not call a project slotless before the first answer arrives', () => {
        // `useQuery` starts with `loading` false and only flips it inside its effect, so a dialog
        // trusting `loading` alone would say "no slot" over an answer on its way.
        queryResult = {data: null, loading: false, error: null, finished: false}
        openFromBuild()
        expect(screen.queryByTestId('deploy-dialog-empty')).not.toBeInTheDocument()
    })
})

describe('the deploy dialog, opened from a slot', () => {

    const openFromSlot = (theSlot) => {
        const dialog = {
            context: {slot: theSlot},
            open: true,
            close: jest.fn(),
            onSuccess: jest.fn(),
        }
        render(<DeployDialog dialog={dialog}/>)
        return dialog
    }

    const build = (name, displayName) => ({
        id: name,
        name,
        displayName: displayName ?? name,
        promotionRuns: [],
    })

    const withBuilds = (theSlot, ...builds) => {
        queryResult = {
            data: {
                slotById: {
                    id: theSlot.id,
                    authorizations: theSlot.authorizations,
                    currentPipeline: theSlot.currentPipeline,
                    eligibleBuilds: {pageItems: builds},
                },
            },
            loading: false,
            error: null,
            finished: true,
        }
    }

    it('names the slot it is deploying into', () => {
        const production = slot('production', 20, {qualifier: 'canary'})
        withBuilds(production)
        openFromSlot(production)
        expect(screen.getByText('Deploy to production · petclinic [canary]')).toBeInTheDocument()
    })

    it('offers a deployable build', () => {
        const production = slot('production', 20)
        withBuilds(production, build('107'))
        openFromSlot(production)
        expect(screen.getByTestId('deploy-dialog-build-start-107')).toBeInTheDocument()
    })

    it('starts the deployment of the build that was chosen', async () => {
        const production = slot('production', 20)
        withBuilds(production, build('107'))
        openFromSlot(production)
        fireEvent.click(screen.getByTestId('deploy-dialog-build-start-107'))
        await waitFor(() => expect(callGraphQL).toHaveBeenCalled())
        expect(callGraphQL.mock.calls[0][0].variables).toEqual({slotId: 'production', buildId: 107})
    })

    it('warns about the deployment it would cancel', () => {
        const busy = slot('production', 20, {current: pipeline(12, 'RUNNING', '105')})
        withBuilds(busy, build('107'))
        openFromSlot(busy)
        expect(screen.getByTestId('deploy-dialog-build-cancels-107'))
            .toHaveTextContent('Deployment #12 (build 105, RUNNING) will be cancelled.')
    })

    it('says so when the slot has nothing it can take', () => {
        const production = slot('production', 20)
        withBuilds(production)
        openFromSlot(production)
        expect(screen.getByTestId('deploy-dialog-empty'))
            .toHaveTextContent('No build of this project can be deployed here.')
    })
})

describe('the deploy dialog, opened on a build AND a slot', () => {

    const openOnBoth = (theSlot) => {
        const dialog = {
            context: {build: {id: 100, name: '107'}, slot: theSlot},
            open: true,
            close: jest.fn(),
            onSuccess: jest.fn(),
        }
        render(<DeployDialog dialog={dialog}/>)
        return dialog
    }

    it('narrows to the slot the caller named rather than asking again', () => {
        // A Deploy button beside one build in one slot has already answered the question; asking it
        // again is what made the old per-row buttons and the dialog feel like two different things.
        withSlots(
            {eligible: true, nonEligibleRules: [], slot: slot('staging', 10)},
            {eligible: true, nonEligibleRules: [], slot: slot('production', 20)},
        )
        openOnBoth(slot('production', 20))
        expect(screen.getByTestId('deploy-dialog-slot-production')).toBeInTheDocument()
        expect(screen.queryByTestId('deploy-dialog-slot-staging')).not.toBeInTheDocument()
    })

    it('still warns about the deployment it would cancel', () => {
        const busy = slot('production', 20, {current: pipeline(12, 'RUNNING', '105')})
        withSlots({eligible: true, nonEligibleRules: [], slot: busy})
        openOnBoth(busy)
        expect(screen.getByTestId('deploy-dialog-slot-cancels-production'))
            .toHaveTextContent('Deployment #12 (build 105, RUNNING) will be cancelled.')
    })

    it('still says why that one slot refuses the build', () => {
        withSlots({eligible: false, nonEligibleRules: [goldRule], slot: slot('production', 20)})
        openOnBoth(slot('production', 20))
        expect(screen.getByTestId('deploy-dialog-slot-reasons-production'))
            .toHaveTextContent('GOLD promotion is required')
    })
})
