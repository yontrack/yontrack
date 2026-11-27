import "@testing-library/jest-dom"
import {act, fireEvent, render, screen, waitFor, within} from "@testing-library/react"
import SelectProject from "@components/projects/SelectProject"

const mockCallGraphQL = jest.fn()

jest.mock("../../../components/services/GraphQL", () => ({
    callGraphQL: (...args) => mockCallGraphQL(...args),
}))

/**
 * The search of the project selector, whose responses may arrive in any order (#2117).
 */
describe('SelectProject', () => {

    // One pending response per searched token, resolved when the test decides
    let pending

    beforeEach(() => {
        pending = {}
        mockCallGraphQL.mockReset()
        mockCallGraphQL.mockImplementation(({variables: {token}}) => new Promise(resolve => {
            pending[token] = (names) => resolve({
                projects: names.map((name, index) => ({id: String(index + 1), name})),
            })
        }))
    })

    const type = (text) => fireEvent.change(
        within(screen.getByTestId('project')).getByRole('combobox'),
        {target: {value: text}},
    )

    const optionNames = () => screen.queryAllByRole('option').map(option => option.textContent)

    it('shows the options of the last token when an earlier response arrives after it', async () => {
        render(<SelectProject id="project" debounceTimeout={0}/>)

        type('prj')
        await waitFor(() => expect(pending['prj']).toBeDefined())
        type('prj_99382')
        await waitFor(() => expect(pending['prj_99382']).toBeDefined())

        await act(async () => pending['prj_99382'](['prj_99382']))
        await act(async () => pending['prj'](['prj_1', 'prj_2', 'prj_99382']))

        await waitFor(() => expect(optionNames()).toEqual(['prj_99382']))
    })

    it('searches only the last token of a burst of keystrokes', async () => {
        render(<SelectProject id="project" debounceTimeout={50}/>)

        type('prj')
        type('prj_')
        type('prj_9')
        await waitFor(() => expect(mockCallGraphQL).toHaveBeenCalled())
        await act(async () => new Promise(resolve => setTimeout(resolve, 100)))

        expect(mockCallGraphQL).toHaveBeenCalledTimes(1)
        expect(mockCallGraphQL.mock.calls[0][0].variables).toEqual({token: 'prj_9'})
    })

    it('does not search for a token of 2 characters or fewer', async () => {
        render(<SelectProject id="project" debounceTimeout={0}/>)

        type('pr')
        await act(async () => new Promise(resolve => setTimeout(resolve, 10)))

        expect(mockCallGraphQL).not.toHaveBeenCalled()
    })
})
