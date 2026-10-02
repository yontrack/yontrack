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
 * Grouped mode of the branch builds grid (#1940): the chip of a single-run group opens the
 * quick-transition popover, and a change refreshes the grid.
 */

const callGraphQL = jest.fn()

jest.mock("../../../components/services/GraphQL", () => ({
    useQuery: (query, {dataFn = data => data, initialData = null, condition = true} = {}) => ({
        data: condition ? dataFn({
            validationRuns: [{
                id: 7,
                lastStatus: {statusID: {id: 'WARNING', name: 'Warning'}},
                authorizations: [{name: 'validation_run', action: 'status_change', authorized: true}],
            }]
        }) : initialData,
        loading: false,
        error: null,
        finished: condition,
    }),
    callGraphQL: (...args) => callGraphQL(...args),
}))

// The dialogs load their own data through a client this test has no use for
jest.mock("../../../components/validationRuns/ValidationRunHistoryDialog", () => ({
    __esModule: true,
    useValidationRunHistoryDialog: () => ({start: jest.fn()}),
    default: () => null,
}))

import ValidationGroup from "@components/validationRuns/ValidationGroup"
import {RefDataContext} from "@components/providers/RefDataProvider"

const WARNING = {id: 'WARNING', name: 'Warning'}
const FIXED = {id: 'FIXED', name: 'Fixed'}

const validationRunStatuses = {
    getAccessibleStatuses: (id) => id === 'WARNING' ? [FIXED] : [],
    roots: [],
    list: [WARNING, FIXED],
}

const validation = (runId, stampName) => ({
    validationStamp: {id: runId, name: stampName, image: false},
    validationRuns: [{
        id: runId,
        lastStatus: {statusID: WARNING, creation: {time: '2026-10-01T08:00:00Z', user: 'admin'}},
    }],
})

const renderGroup = (validations, onChange) => render(
    <RefDataContext.Provider value={{validationRunStatuses}}>
        <ValidationGroup
            group={{
                statusID: WARNING,
                count: validations.length,
                description: `${validations.length} validations with status Warning`,
                validations,
            }}
            onChange={onChange}
        />
    </RefDataContext.Provider>
)

beforeEach(() => {
    callGraphQL.mockReset()
    callGraphQL.mockResolvedValue({changeValidationRunStatus: {errors: []}})
})

describe('a single-run validation group', () => {

    it('opens the quick-transition popover of its run, and refreshes the grid after a change', async () => {
        const onChange = jest.fn()
        renderGroup([validation(7, 'security-scan')], onChange)
        fireEvent.click(screen.getByTestId('validation-group-WARNING'))
        const popover = await screen.findByTestId('validation-run-quick-transition-7')
        fireEvent.click(within(popover).getByRole('button', {name: 'Fixed'}))
        await waitFor(() => expect(onChange).toHaveBeenCalled())
        expect(callGraphQL.mock.calls[0][0].variables).toEqual({runId: 7, statusId: 'FIXED', description: null})
    })
})

describe('a group of several runs', () => {

    it('opens the list of its runs, not a quick-transition popover', async () => {
        renderGroup([validation(7, 'security-scan'), validation(8, 'unit-tests')], jest.fn())
        fireEvent.click(screen.getByText('2 Warning'))
        expect(await screen.findByText('2 Warning validations in build')).toBeInTheDocument()
        expect(screen.queryByTestId(/^validation-run-quick-transition-/)).not.toBeInTheDocument()
    })
})
