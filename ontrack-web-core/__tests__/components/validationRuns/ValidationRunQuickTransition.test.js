import "@testing-library/jest-dom"
import {fireEvent, render, screen, waitFor, within} from "@testing-library/react"

// antd reads the responsive breakpoints; jsdom ships no `matchMedia`.
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
 * The quick-transition popover of a validation run (#1940): one click on the run's status, one
 * click on the next status.
 */

let runResult = null
const callGraphQL = jest.fn()

jest.mock("../../../components/services/GraphQL", () => ({
    useQuery: (query, {dataFn = data => data, initialData = null, condition = true} = {}) => ({
        data: condition && runResult ? dataFn(runResult) : initialData,
        loading: false,
        error: null,
        finished: condition,
    }),
    callGraphQL: (...args) => callGraphQL(...args),
}))

// The history dialog loads its own data through a client this test has no use for
const historyStart = jest.fn()
jest.mock("../../../components/validationRuns/ValidationRunHistoryDialog", () => ({
    __esModule: true,
    useValidationRunHistoryDialog: () => ({start: historyStart}),
    default: () => null,
}))

import ValidationRunQuickTransition, {
    quickTransitionStatuses
} from "@components/validationRuns/ValidationRunQuickTransition"
import {RefDataContext} from "@components/providers/RefDataProvider"

const STATUSES = {
    PASSED: {id: 'PASSED', name: 'Passed', followingStatuses: []},
    FIXED: {id: 'FIXED', name: 'Fixed', followingStatuses: []},
    DEFECTIVE: {id: 'DEFECTIVE', name: 'Defective', followingStatuses: []},
    EXPLAINED: {id: 'EXPLAINED', name: 'Explained', followingStatuses: ['FIXED']},
    INVESTIGATING: {id: 'INVESTIGATING', name: 'Investigating', followingStatuses: ['DEFECTIVE', 'EXPLAINED', 'FIXED']},
    INTERRUPTED: {id: 'INTERRUPTED', name: 'Interrupted', followingStatuses: ['INVESTIGATING', 'FIXED']},
    WARNING: {
        id: 'WARNING',
        name: 'Warning',
        followingStatuses: ['INTERRUPTED', 'INVESTIGATING', 'EXPLAINED', 'DEFECTIVE', 'FIXED']
    },
}

const validationRunStatuses = {
    getAccessibleStatuses: (id) => (STATUSES[id]?.followingStatuses ?? []).map(fid => STATUSES[fid]),
    roots: [],
    list: Object.values(STATUSES),
}

const withRun = ({status = 'WARNING', statusChange = true} = {}) => {
    runResult = {
        validationRuns: [{
            id: 42,
            lastStatus: {statusID: {id: status, name: STATUSES[status].name}},
            authorizations: [{name: 'validation_run', action: 'status_change', authorized: statusChange}],
        }]
    }
}

const onChange = jest.fn()

const renderTrigger = ({label} = {}) => render(
    <RefDataContext.Provider value={{validationRunStatuses}}>
        <ValidationRunQuickTransition run={{id: 42}} label={label} onChange={onChange}>
            <span>Current status</span>
        </ValidationRunQuickTransition>
    </RefDataContext.Provider>
)

const trigger = () => screen.getByRole('button', {name: 'Current status'})

const openPopover = async () => {
    renderTrigger()
    fireEvent.click(trigger())
    return await screen.findByTestId('validation-run-quick-transition-42')
}

const sentVariables = () => callGraphQL.mock.calls[0][0].variables

beforeEach(() => {
    callGraphQL.mockReset()
    callGraphQL.mockResolvedValue({changeValidationRunStatus: {errors: []}})
    onChange.mockReset()
    historyStart.mockReset()
    withRun()
})

describe('quickTransitionStatuses', () => {

    it('puts FIXED first when it is allowed', () => {
        expect(quickTransitionStatuses(validationRunStatuses, 'WARNING').map(it => it.id))
            .toEqual(['FIXED', 'INTERRUPTED', 'INVESTIGATING', 'EXPLAINED', 'DEFECTIVE'])
    })

    it('keeps the order of the other next statuses', () => {
        expect(quickTransitionStatuses(validationRunStatuses, 'INVESTIGATING').map(it => it.id))
            .toEqual(['FIXED', 'DEFECTIVE', 'EXPLAINED'])
    })

    it('is empty for a status with no next status', () => {
        expect(quickTransitionStatuses(validationRunStatuses, 'PASSED')).toEqual([])
    })
})

describe('the quick-transition popover', () => {

    it('is closed until the status is clicked', () => {
        renderTrigger()
        expect(screen.queryByTestId('validation-run-quick-transition-42')).not.toBeInTheDocument()
    })

    it('opens from the keyboard', async () => {
        renderTrigger()
        fireEvent.keyDown(trigger(), {key: 'Enter'})
        expect(await screen.findByTestId('validation-run-quick-transition-42')).toBeInTheDocument()
    })

    it('can be named for the run it belongs to', () => {
        renderTrigger({label: 'Security scan \u2014 Warning'})
        expect(screen.getByRole('button', {name: 'Security scan \u2014 Warning'})).toBeInTheDocument()
    })

    it('does not report a change when the call returns nothing', async () => {
        // What `callGraphQL` does on a 401, while it signs the user out
        callGraphQL.mockResolvedValue(undefined)
        const popover = await openPopover()
        fireEvent.click(within(popover).getByRole('button', {name: 'Fixed'}))
        await waitFor(() => expect(callGraphQL).toHaveBeenCalled())
        expect(onChange).not.toHaveBeenCalled()
    })

    it('offers one button per next status, FIXED first', async () => {
        const popover = await openPopover()
        const buttons = within(popover).getAllByRole('button').map(it => it.textContent)
        expect(buttons).toEqual([
            'Fixed', 'Interrupted', 'Investigating', 'Explained', 'Defective',
            'With comment…', 'History…',
        ])
    })

    it('applies a status in one click, with no description, and refreshes its host', async () => {
        const popover = await openPopover()
        fireEvent.click(within(popover).getByRole('button', {name: 'Fixed'}))
        await waitFor(() => expect(onChange).toHaveBeenCalled())
        expect(sentVariables()).toEqual({runId: 42, statusId: 'FIXED', description: null})
    })

    it('shows only History… without the status change permission', async () => {
        withRun({statusChange: false})
        const popover = await openPopover()
        expect(within(popover).getAllByRole('button').map(it => it.textContent)).toEqual(['History…'])
    })

    it('shows only History… for a status with no next status', async () => {
        withRun({status: 'FIXED'})
        const popover = await openPopover()
        expect(within(popover).getAllByRole('button').map(it => it.textContent)).toEqual(['History…'])
    })

    it('opens the history dialog', async () => {
        const popover = await openPopover()
        fireEvent.click(within(popover).getByRole('button', {name: 'History…'}))
        expect(historyStart).toHaveBeenCalledWith({id: 42})
    })

    it('shows the errors of the mutation', async () => {
        callGraphQL.mockResolvedValue({changeValidationRunStatus: {errors: [{message: 'Not this time.'}]}})
        const popover = await openPopover()
        fireEvent.click(within(popover).getByRole('button', {name: 'Fixed'}))
        expect(await within(popover).findByText('Not this time.')).toBeInTheDocument()
        expect(onChange).not.toHaveBeenCalled()
    })

    it('shows a failure of the call itself', async () => {
        // A refused transition or a missing permission is a GraphQL error, not a user error
        callGraphQL.mockRejectedValue(new Error("[WARNING] --> [PASSED] change is not allowed."))
        const popover = await openPopover()
        fireEvent.click(within(popover).getByRole('button', {name: 'Fixed'}))
        expect(await within(popover).findByText('[WARNING] --> [PASSED] change is not allowed.')).toBeInTheDocument()
        expect(onChange).not.toHaveBeenCalled()
    })

    describe('with a comment', () => {

        it('sends the picked status and the comment', async () => {
            const popover = await openPopover()
            fireEvent.click(within(popover).getByRole('button', {name: 'With comment…'}))
            fireEvent.click(within(popover).getByRole('radio', {name: 'Explained'}))
            fireEvent.change(within(popover).getByRole('textbox', {name: 'Comment'}), {target: {value: 'Known flaky test'}})
            fireEvent.click(within(popover).getByRole('button', {name: 'Confirm'}))
            await waitFor(() => expect(onChange).toHaveBeenCalled())
            expect(sentVariables()).toEqual({runId: 42, statusId: 'EXPLAINED', description: 'Known flaky test'})
        })

        it('can go back to the one-click statuses', async () => {
            const popover = await openPopover()
            fireEvent.click(within(popover).getByRole('button', {name: 'With comment…'}))
            fireEvent.click(within(popover).getByRole('button', {name: 'Back'}))
            expect(within(popover).getByRole('button', {name: 'Fixed'})).toBeInTheDocument()
            expect(within(popover).queryByRole('textbox', {name: 'Comment'})).not.toBeInTheDocument()
        })

        it('cannot be confirmed before a status is picked', async () => {
            const popover = await openPopover()
            fireEvent.click(within(popover).getByRole('button', {name: 'With comment…'}))
            expect(within(popover).getByRole('button', {name: 'Confirm'})).toBeDisabled()
        })
    })
})
