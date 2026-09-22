import "@testing-library/jest-dom"
import {act, fireEvent, render, screen, waitFor, within} from "@testing-library/react"

let queryResult = {data: null, loading: false, error: null, finished: true}
/** The options the screen handed `useQuery` for the *list* query on its last render. */
let queryOptions
/** What the label filter's own query answers with. */
let labelsResult = []

jest.mock("../../../components/services/GraphQL", () => ({
    /*
     * Two queries run on this screen: the list itself, and the labels the label
     * filter offers to pick from. They are told apart by their text, so that the
     * tests below drive the list without the filter's query standing in for it.
     *
     * `dataFn` is applied rather than bypassed: it is part of what the screen
     * asks for, and a test feeding it the already-unwrapped answer would not
     * notice the screen reading the wrong field of it.
     */
    useQuery: (query, options) => {
        if (query.includes('paginatedProjects')) {
            queryOptions = options
            const {data, ...rest} = queryResult
            return {...rest, data: data ? options.dataFn(data) : data}
        }
        return {data: labelsResult, loading: false, error: null, finished: true}
    },
    callGraphQL: jest.fn(),
}))

import MobileProjectListScreen from "@/app/mobile/projects/ProjectListScreen"
import {MOBILE_PROJECT_PAGE_SIZE} from "@/app/mobile/projects/ProjectListScreen"

const setResult = (result) => {
    queryResult = {data: null, loading: false, error: null, finished: true, ...result}
}

/**
 * The answer to the list query, in the shape the server sends it - the page and
 * the *filtered* total beside it.
 */
const page = (list, totalSize = list.length) => setResult({
    data: {paginatedProjects: {pageInfo: {totalSize}, pageItems: list}},
})

const projects = (...list) => page(list)

const project = (id, name, favourite = false, disabled = false) => ({id, name, favourite, disabled})

const label = (id, category, name) => ({
    id, category, name, description: null, color: '#FF0000', foregroundColor: '#000000',
})

/** Types into the name filter, then lets the debounce fire. */
const filterBy = async (text) => {
    fireEvent.change(screen.getByTestId('mobile-projects-filter'), {target: {value: text}})
    await act(async () => {
        jest.advanceTimersByTime(1000)
    })
}

/**
 * Picks a label in the label filter, the way a thumb does it.
 *
 * The options carry a chip rather than a string, so they are found by the chip's
 * own test id rather than by title. Real timers: the dropdown's own animation
 * would otherwise never run, and only the name filter needs the fake ones.
 */
const pickLabel = async (display) => {
    jest.useRealTimers()
    fireEvent.mouseDown(
        within(screen.getByTestId('mobile-projects-labels-filter')).getByRole('combobox')
    )
    const options = await screen.findAllByTestId(`label-${display}`)
    fireEvent.click(options[options.length - 1])
    await waitFor(() => expect(queryOptions.variables.labels).toContain(display))
    jest.useFakeTimers()
}

beforeEach(() => {
    queryOptions = undefined
    labelsResult = []
    jest.useFakeTimers()
})

afterEach(() => {
    jest.useRealTimers()
})

describe('the mobile project list', () => {

    it('lists every project, favourite or not', () => {
        // This is the screen the home screen's empty state sends a first-time
        // user to, so it cannot itself be filtered down to favourites.
        projects(project(1, 'petclinic', true), project(2, 'common-library'))
        render(<MobileProjectListScreen/>)
        expect(screen.getByTestId('mobile-project-1')).toHaveTextContent('petclinic')
        expect(screen.getByTestId('mobile-project-2')).toHaveTextContent('common-library')
    })

    it('offers the favourite toggle on each of them, showing the current state', () => {
        projects(project(1, 'petclinic', true), project(2, 'common-library'))
        render(<MobileProjectListScreen/>)
        expect(screen.getByTestId('mobile-favourite-project-1')).toHaveAttribute('aria-pressed', 'true')
        expect(screen.getByTestId('mobile-favourite-project-2')).toHaveAttribute('aria-pressed', 'false')
    })

    it('sends each row to that project on the phone, not to the desktop page', () => {
        // Leaving `/mobile` would bounce the user through the redirect and,
        // once the app is installed as a PWA scoped to that prefix, out of the
        // app itself.
        projects(project(1, 'petclinic'))
        render(<MobileProjectListScreen/>)
        expect(screen.getByTestId('mobile-project-1').querySelector('a'))
            .toHaveAttribute('href', '/mobile/project/1')
    })

    it('says which projects are disabled', () => {
        projects(project(3, 'retired-thing', false, true))
        render(<MobileProjectListScreen/>)
        expect(screen.getByTestId('mobile-project-3')).toHaveTextContent('Disabled')
    })

    it('shows no label on the rows themselves', () => {
        // The filter is what labels are for on a phone: a chip per row would
        // cost the project name the width it needs at 375px.
        labelsResult = [label(10, 'team', 'platform')]
        projects(project(1, 'petclinic'))
        render(<MobileProjectListScreen/>)
        expect(screen.getByTestId('mobile-projects').querySelector('[data-testid^="label-"]')).toBeNull()
    })

    describe('paging', () => {

        it('asks for one page, large enough to hold what an instance holds', () => {
            // `paginatedProjects` is paginated where `projects(pattern:)` was
            // not, and a phone list is scrolled rather than paged - so the whole
            // list comes in one answer.
            projects(project(1, 'petclinic'))
            render(<MobileProjectListScreen/>)
            expect(queryOptions.variables.size).toEqual(MOBILE_PROJECT_PAGE_SIZE)
        })

        it('says so when the page did not hold everything', () => {
            // Silently dropping projects off the end is the failure this guards:
            // the total is the filtered one, so it says exactly what was missed.
            page([project(1, 'petclinic')], 300)
            render(<MobileProjectListScreen/>)
            expect(screen.getByTestId('mobile-projects-truncated')).toHaveTextContent('1 of 300')
        })

        it('says nothing when the page held everything', () => {
            projects(project(1, 'petclinic'))
            render(<MobileProjectListScreen/>)
            expect(screen.queryByTestId('mobile-projects-truncated')).not.toBeInTheDocument()
        })
    })

    describe('filtering by name', () => {

        it('asks the server for the whole list until something is typed', () => {
            // An instance can hold hundreds of projects, which is more than a
            // phone can be scrolled through - but the filter has to start empty
            // or the screen would open on nothing.
            projects(project(1, 'petclinic'))
            render(<MobileProjectListScreen/>)
            expect(queryOptions.variables.name).toBeNull()
        })

        it('filters on the server, not in the browser', () => {
            // The list the browser holds is the answer to the last query; a
            // client-side filter could only narrow that, never reach a project
            // the server never sent.
            projects(project(1, 'petclinic'))
            render(<MobileProjectListScreen/>)
            return filterBy('pet').then(() => {
                expect(queryOptions.variables.name).toEqual('pet')
            })
        })

        it('waits for the typing to settle before asking', () => {
            projects(project(1, 'petclinic'))
            render(<MobileProjectListScreen/>)
            fireEvent.change(screen.getByTestId('mobile-projects-filter'), {target: {value: 'p'}})
            // One request per keystroke on a list this size is what the debounce
            // is there to prevent.
            expect(queryOptions.variables.name).toBeNull()
        })

        it('treats a whitespace-only filter as no filter', async () => {
            // The server answers a blank name with the whole list, so a screen
            // calling itself filtered would head every project on the instance
            // with "Matching projects".
            projects(project(1, 'petclinic'))
            render(<MobileProjectListScreen/>)
            await filterBy('   ')
            expect(queryOptions.variables.name).toBeNull()
            expect(screen.getByTestId('mobile-projects')).toHaveTextContent('All projects')
        })

        it('sends the name without the spaces around it', async () => {
            projects(project(1, 'petclinic'))
            render(<MobileProjectListScreen/>)
            await filterBy('  pet  ')
            expect(queryOptions.variables.name).toEqual('pet')
        })

        it('goes back to the whole list when the filter is cleared', async () => {
            projects(project(1, 'petclinic'))
            render(<MobileProjectListScreen/>)
            await filterBy('pet')
            await filterBy('')
            expect(queryOptions.variables.name).toBeNull()
        })

        it('says a filter matched nothing, rather than showing the empty instance message', async () => {
            projects(project(1, 'petclinic'))
            render(<MobileProjectListScreen/>)
            projects()
            await filterBy('nothing-matches-this')
            expect(screen.getByTestId('mobile-projects-empty')).toHaveTextContent(/no project matches/i)
        })
    })

    describe('filtering by label', () => {

        it('asks for no label filter until one is picked', () => {
            projects(project(1, 'petclinic'))
            render(<MobileProjectListScreen/>)
            expect(queryOptions.variables.labels).toEqual([])
        })

        it('sends the labels as the display strings the server filters on', async () => {
            // `category:name`, the same strings `paginatedProjects(labels:)` and
            // the desktop project list filter use.
            labelsResult = [label(10, 'team', 'platform'), label(11, null, 'legacy')]
            projects(project(1, 'petclinic'))
            render(<MobileProjectListScreen/>)
            await pickLabel('team:platform')
            expect(queryOptions.variables.labels).toEqual(['team:platform'])
        })

        it('counts as filtering, so the list does not call itself the whole one', async () => {
            labelsResult = [label(10, 'team', 'platform')]
            projects(project(1, 'petclinic'))
            render(<MobileProjectListScreen/>)
            await pickLabel('team:platform')
            expect(screen.getByTestId('mobile-projects')).toHaveTextContent('Matching projects')
        })

        it('says which labels matched nothing, rather than showing the empty instance message', async () => {
            labelsResult = [label(10, 'team', 'platform')]
            projects(project(1, 'petclinic'))
            render(<MobileProjectListScreen/>)
            projects()
            await pickLabel('team:platform')
            await waitFor(() =>
                expect(screen.getByTestId('mobile-projects-empty')).toHaveTextContent('team:platform')
            )
        })
    })

    it('keeps the list on screen while a filter or a toggle refetches it', () => {
        setResult({
            data: {paginatedProjects: {pageInfo: {totalSize: 1}, pageItems: [project(1, 'petclinic')]}},
            loading: true,
            finished: true,
        })
        render(<MobileProjectListScreen/>)
        expect(screen.getByTestId('mobile-project-1')).toBeInTheDocument()
    })

    it('says so when there is no project at all', () => {
        projects()
        render(<MobileProjectListScreen/>)
        expect(screen.getByTestId('mobile-projects-empty')).toHaveTextContent(/no project on this instance/i)
    })

    it('does not flash the empty state before the first answer arrives', () => {
        setResult({data: null, loading: true, finished: false})
        render(<MobileProjectListScreen/>)
        expect(screen.queryByTestId('mobile-projects-empty')).not.toBeInTheDocument()
    })

    it('says so when the projects could not be loaded', () => {
        setResult({data: null, error: "Boom"})
        render(<MobileProjectListScreen/>)
        expect(screen.getByText(/Boom/)).toBeInTheDocument()
        expect(screen.queryByTestId('mobile-projects-empty')).not.toBeInTheDocument()
    })
})
