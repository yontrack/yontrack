import "@testing-library/jest-dom"
import {fireEvent, render, screen, waitFor} from "@testing-library/react"
import ValidationRunHistoryDialog from "@components/validationRuns/ValidationRunHistoryDialog"

jest.mock("../../../components/builds/BuildLink", () => function BuildLink({build}) {
    return <span>{build?.name}</span>
})
jest.mock("../../../components/validationStamps/ValidationStampLink", () => function ValidationStampLink({validationStamp}) {
    return <span>{validationStamp?.name}</span>
})
jest.mock("../../../components/validationRuns/ValidationRunLink", () => function ValidationRunLink({text}) {
    return <span>{text}</span>
})
jest.mock("../../../components/validationRuns/ValidationRunStatus", () => function ValidationRunStatus() {
    return null
})
jest.mock("../../../components/framework/validation-data-type/ValidationDataType", () => function ValidationDataType() {
    return null
})
jest.mock("../../../components/validationRuns/ValidationRun", () => function ValidationRun({run, onStatusChanged, onRunChanged}) {
    return <div>
        <span>{`Status of ${run.runOrder}: ${run.lastStatus.statusID.id}`}</span>
        <button onClick={() => onRunChanged({id: run.id, lastStatus: {statusID: {id: "FIXED"}}})}>
            {`Change ${run.runOrder}`}
        </button>
        <button onClick={onStatusChanged}>{`Reload ${run.runOrder}`}</button>
    </div>
})

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
 * `ValidationRunHistoryDialog` chains two `useQuery` calls (#1929): the run gives the build and the
 * validation stamp, whose runs are then listed. A run changed in the dialog is applied locally.
 */

const run = (id, runOrder) => ({
    id,
    runOrder,
    lastStatus: {statusID: {id: "FAILED"}},
})

const operations = () => global.fetch.mock.calls.map(([, init]) => {
    const {query, variables} = JSON.parse(init.body)
    return {operation: query.match(/query (\w+)/)[1], variables}
})

describe('ValidationRunHistoryDialog', () => {

    beforeEach(() => {
        global.fetch = jest.fn().mockImplementation(async (_, init) => {
            const {query} = JSON.parse(init.body)
            const data = query.includes("GetValidationHistory") ? {
                build: {
                    id: "20",
                    name: "build-20",
                    validations: [{
                        validationStamp: {
                            id: "30",
                            name: "unit-tests",
                            dataType: null,
                            validationRunsPaginated: {
                                pageInfo: {nextPage: null},
                                pageItems: [run("1", 2), run("2", 1)],
                            },
                        },
                    }],
                },
            } : {
                validationRuns: [{
                    build: {id: "20", releaseProperty: null},
                    validationStamp: {name: "unit-tests"},
                }],
            }
            return {ok: true, status: 200, json: async () => data}
        })
    })

    afterEach(() => {
        delete global.fetch
    })

    const dialog = {open: true, run: {id: "1"}, close: jest.fn()}

    it('lists the runs of the build and validation stamp of the selected run', async () => {
        render(<ValidationRunHistoryDialog dialog={dialog}/>)

        await waitFor(() => expect(screen.getByText("Status of 2: FAILED")).toBeInTheDocument())
        expect(screen.getByText("Status of 1: FAILED")).toBeInTheDocument()
        expect(screen.getByText("build-20")).toBeInTheDocument()
        expect(operations()).toEqual([
            {operation: "GetValidationRun", variables: {runId: 1}},
            {
                operation: "GetValidationHistory",
                variables: {buildId: 20, validationStampName: "unit-tests", offset: 0, size: 10},
            },
        ])
    })

    it('applies a changed run locally, until the history is reloaded', async () => {
        const onChange = jest.fn()
        render(<ValidationRunHistoryDialog dialog={dialog} onChange={onChange}/>)

        await waitFor(() => expect(screen.getByText("Status of 2: FAILED")).toBeInTheDocument())

        fireEvent.click(screen.getByText("Change 2"))
        await waitFor(() => expect(screen.getByText("Status of 2: FIXED")).toBeInTheDocument())
        expect(screen.getByText("Status of 1: FAILED")).toBeInTheDocument()

        fireEvent.click(screen.getByText("Reload 1"))
        await waitFor(() => expect(screen.getByText("Status of 2: FAILED")).toBeInTheDocument())
        expect(operations().map(it => it.operation)).toEqual([
            "GetValidationRun", "GetValidationHistory", "GetValidationRun", "GetValidationHistory",
        ])
    })

    it('does not query anything while the dialog is closed', async () => {
        render(<ValidationRunHistoryDialog dialog={{...dialog, open: false}}/>)

        await new Promise(resolve => setTimeout(resolve, 50))
        expect(global.fetch).not.toHaveBeenCalled()
    })

})
