import "@testing-library/jest-dom"
import {fireEvent, render, screen, waitFor} from "@testing-library/react"

// antd's Drawer reads the responsive breakpoints; jsdom ships no `matchMedia`.
// Same stand-in as `StatePill.test.js` and the other antd component tests.
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
 * Promoting a build from a phone.
 *
 * The sheet is the whole of #1724 on the client side: the level, the collapsed
 * time, the description and - the part a phone cannot do without - the promotion
 * level's own declared fields, which are what makes a level usable or unusable
 * from a phone.
 */

let levelsResult = {data: null, loading: false, error: null, finished: true}
const callGraphQL = jest.fn()

jest.mock("../../../components/services/GraphQL", () => ({
    // `dataFn` and `initialData` applied as the real hook applies them: what the
    // component reads off a query result is part of the contract it is written
    // against, and a mock that skipped them would test a different hook.
    useQuery: (query, {dataFn = data => data, initialData = null} = {}) => ({
        ...levelsResult,
        data: levelsResult.data ? dataFn(levelsResult.data) : initialData,
    }),
    callGraphQL: (...args) => callGraphQL(...args),
}))

import MobilePromoteSheet from "@components/mobile/builds/MobilePromoteSheet"

const build = {id: 100, branch: {id: 10}}

const field = ({
                   name,
                   displayName,
                   type = 'TEXT',
                   required = false,
                   options = [],
                   position = 0,
               }) => ({name, displayName, description: null, type, required, options, position})

const level = (id, name, fields = []) => ({id, name, image: false, fields})

const withLevels = (...levels) => {
    levelsResult = {
        data: {branches: [{promotionLevels: levels}]},
        loading: false,
        error: null,
        finished: true,
    }
}

/** Opens the level dropdown and picks one by name. */
const pickLevel = async (name) => {
    fireEvent.mouseDown(screen.getByTestId('mobile-promote-level').querySelector('.ant-select-selector'))
    const option = await screen.findByTitle(name)
    fireEvent.click(option)
    await waitFor(() => expect(screen.getByTestId('mobile-promote-level')).toHaveTextContent(name))
}

const submit = () => fireEvent.click(screen.getByTestId('mobile-promote-submit'))

/** What the mutation was called with. */
const sentVariables = () => callGraphQL.mock.calls[0][0].variables

const openSheet = (props = {}) => render(
    <MobilePromoteSheet build={build} open={true} onClose={() => {}} onPromoted={() => {}} {...props}/>
)

beforeEach(() => {
    callGraphQL.mockReset()
    callGraphQL.mockResolvedValue({createPromotionRunById: {errors: null}})
    withLevels(level(500, 'BRONZE'), level(501, 'SILVER'))
})

describe('the mobile promote sheet', () => {

    it('offers the levels of the build\'s own branch', async () => {
        openSheet()
        fireEvent.mouseDown(screen.getByTestId('mobile-promote-level').querySelector('.ant-select-selector'))
        expect(await screen.findByTitle('BRONZE')).toBeInTheDocument()
        expect(await screen.findByTitle('SILVER')).toBeInTheDocument()
    })

    it('refuses to promote to nothing', async () => {
        openSheet()
        submit()
        expect(await screen.findByText('Promotion level is required.')).toBeInTheDocument()
        expect(callGraphQL).not.toHaveBeenCalled()
    })

    it('promotes the build to the chosen level', async () => {
        openSheet()
        await pickLevel('SILVER')
        submit()
        await waitFor(() => expect(callGraphQL).toHaveBeenCalled())
        expect(sentVariables()).toMatchObject({buildId: 100, promotion: 'SILVER'})
    })

    describe('the date and time', () => {

        it('is not a picker a thumb has to get past', () => {
            // Someone promoting from their phone is promoting now. The picker is
            // there for the rare correction, not in the way of the common case.
            openSheet()
            expect(screen.queryByTestId('mobile-promote-time')).not.toBeInTheDocument()
            expect(screen.getByTestId('mobile-promote-time-toggle')).toBeInTheDocument()
        })

        it('leaves the time to the server when it stays collapsed', async () => {
            // Not a timestamp captured when the sheet opened: the promotion
            // happens when it is submitted, and a sheet can sit open.
            openSheet()
            await pickLevel('BRONZE')
            submit()
            await waitFor(() => expect(callGraphQL).toHaveBeenCalled())
            expect(sentVariables().dateTime).toBeUndefined()
        })

        it('opens a picker for the promotion that happened earlier', async () => {
            openSheet()
            fireEvent.click(screen.getByTestId('mobile-promote-time-toggle'))
            expect(await screen.findByTestId('mobile-promote-time')).toBeInTheDocument()
        })
    })

    it('sends the description when there is one', async () => {
        openSheet()
        await pickLevel('BRONZE')
        fireEvent.change(screen.getByTestId('mobile-promote-description'), {target: {value: "Signed off by QA"}})
        submit()
        await waitFor(() => expect(callGraphQL).toHaveBeenCalled())
        expect(sentVariables().description).toEqual("Signed off by QA")
    })

    describe('the promotion level\'s own fields', () => {

        it('shows the fields of the level that was picked, and only those', async () => {
            withLevels(
                level(500, 'BRONZE', [field({name: 'ticket', displayName: 'Ticket'})]),
                level(501, 'SILVER', [field({name: 'signoff', displayName: 'Sign-off'})]),
            )
            openSheet()
            await pickLevel('BRONZE')
            expect(screen.getByText('Ticket')).toBeInTheDocument()
            expect(screen.queryByText('Sign-off')).not.toBeInTheDocument()
        })

        it('shows them in their declared order', async () => {
            withLevels(level(500, 'BRONZE', [
                field({name: 'second', displayName: 'Second', position: 1}),
                field({name: 'first', displayName: 'First', position: 0}),
            ]))
            openSheet()
            await pickLevel('BRONZE')
            const labels = Array.from(document.querySelectorAll('.ant-form-item-label label'))
                .map(label => label.textContent)
            expect(labels.indexOf('First')).toBeLessThan(labels.indexOf('Second'))
        })

        it('refuses to promote without a required field', async () => {
            // The acceptance criterion this whole section exists for: a required
            // field a phone cannot fill makes the level unusable from a phone.
            withLevels(level(500, 'BRONZE', [field({name: 'ticket', displayName: 'Ticket', required: true})]))
            openSheet()
            await pickLevel('BRONZE')
            submit()
            expect(await screen.findByText('Ticket is required.')).toBeInTheDocument()
            expect(callGraphQL).not.toHaveBeenCalled()
        })

        it('sends the values that were filled in', async () => {
            withLevels(level(500, 'BRONZE', [field({name: 'ticket', displayName: 'Ticket', required: true})]))
            openSheet()
            await pickLevel('BRONZE')
            fireEvent.change(screen.getByRole('textbox', {name: 'Ticket'}), {target: {value: 'PROJ-42'}})
            submit()
            await waitFor(() => expect(callGraphQL).toHaveBeenCalled())
            expect(sentVariables().fieldValues).toEqual([{name: 'ticket', value: 'PROJ-42'}])
        })

        it('sends no field values for a level that declares none', async () => {
            openSheet()
            await pickLevel('BRONZE')
            submit()
            await waitFor(() => expect(callGraphQL).toHaveBeenCalled())
            expect(sentVariables().fieldValues).toBeUndefined()
        })

        it('forgets what was typed for the level that was abandoned', async () => {
            // Two levels, two `ticket` fields: carrying the first one's value
            // into the second would promote with an answer the user never gave
            // to the question they were actually asked.
            withLevels(
                level(500, 'BRONZE', [field({name: 'ticket', displayName: 'Ticket'})]),
                level(501, 'SILVER', [field({name: 'ticket', displayName: 'Ticket'})]),
            )
            openSheet()
            await pickLevel('BRONZE')
            fireEvent.change(screen.getByRole('textbox', {name: 'Ticket'}), {target: {value: 'PROJ-42'}})
            await pickLevel('SILVER')
            submit()
            await waitFor(() => expect(callGraphQL).toHaveBeenCalled())
            expect(sentVariables().fieldValues).toBeUndefined()
        })
    })

    describe('when the promotion fails', () => {

        it('says why, and keeps what was filled in', async () => {
            callGraphQL.mockResolvedValue({
                createPromotionRunById: {errors: [{message: "Field ticket is required."}]},
            })
            const onPromoted = jest.fn()
            openSheet({onPromoted})
            await pickLevel('BRONZE')
            submit()
            expect(await screen.findByTestId('mobile-promote-error')).toHaveTextContent('Field ticket is required.')
            expect(onPromoted).not.toHaveBeenCalled()
        })

        it('says why when the call itself failed', async () => {
            callGraphQL.mockRejectedValue(new Error("Network is unreachable."))
            openSheet()
            await pickLevel('BRONZE')
            submit()
            expect(await screen.findByTestId('mobile-promote-error')).toHaveTextContent('Network is unreachable.')
        })
    })

    it('tells the screen to refresh once the promotion is in', async () => {
        // The build screen has to show the new promotion without a reload, and
        // only the server knows the run it created.
        const onPromoted = jest.fn()
        const onClose = jest.fn()
        openSheet({onPromoted, onClose})
        await pickLevel('BRONZE')
        submit()
        await waitFor(() => expect(onPromoted).toHaveBeenCalled())
        expect(onClose).toHaveBeenCalled()
    })

    it('asks for nothing while it is closed', () => {
        // A sheet nobody opened must not cost a phone a query - and the build
        // screen mounts one for every build a user looks at.
        render(<MobilePromoteSheet build={build} open={false} onClose={() => {}} onPromoted={() => {}}/>)
        expect(screen.queryByTestId('mobile-promote-level')).not.toBeInTheDocument()
    })
})
