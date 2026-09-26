import "@testing-library/jest-dom"
import {act, fireEvent, render, screen, within} from "@testing-library/react"

/** What the search query answers with, in the shape the server sends it. */
let searchAnswer = null
/** The options the palette handed `useQuery` on its last render. */
let searchOptions

jest.mock("../../../../components/services/GraphQL", () => ({
    useQuery: (query, options) => {
        searchOptions = options
        const active = options.condition !== false
        return {
            data: active && searchAnswer ? options.dataFn(searchAnswer) : null,
            loading: false,
            error: null,
            finished: active,
        }
    },
    callGraphQL: jest.fn(),
}))

const mockPush = jest.fn()
jest.mock("next/router", () => ({
    useRouter: () => ({push: mockPush}),
}))

// The per-type components are loaded lazily by path: what matters here is which one is asked for
jest.mock("../../../../components/common/Dynamic", () => ({
    Dynamic: ({path, props}) => <span data-testid={`dynamic-${path}`}>{props?.title}</span>,
}))

import {CommandPaletteProvider} from "@components/search/palette/CommandPaletteContext"
import {UserContext} from "@components/providers/UserProvider"
import {recordRecentlyVisited} from "@components/search/palette/recentlyVisited"
import SearchPaletteButton from "@components/search/palette/SearchPaletteButton"

const user = {
    name: 'admin',
    authorizations: {},
    userMenuGroups: [
        {
            id: 'system', name: 'System', items: [
                {extension: 'core/admin', id: 'settings', name: 'Settings'},
                {extension: 'core/admin', id: 'jobs', name: 'System jobs'},
            ]
        },
        {
            id: 'information', name: 'Information', items: [
                {extension: 'extension/auto-versioning', id: 'audit/global', name: 'Auto-versioning audit'},
            ]
        },
    ],
}

const renderPalette = () => render(
    <UserContext.Provider value={user}>
        <CommandPaletteProvider>
            <input data-testid="some-input"/>
            <div data-testid="some-text">Some text</div>
        </CommandPaletteProvider>
    </UserContext.Provider>
)

const dialog = () => screen.queryByRole('dialog')
const combobox = () => screen.getByRole('combobox')
const options = () => screen.getAllByRole('option')
const activeOption = () => {
    const id = combobox().getAttribute('aria-activedescendant')
    return id ? document.getElementById(id) : null
}

const press = (key, init = {}) => fireEvent.keyDown(combobox(), {key, ...init})

const type = async (text) => {
    fireEvent.change(combobox(), {target: {value: text}})
    await act(async () => {
        jest.advanceTimersByTime(1000)
    })
}

const result = (typeId, typeName, title, data) => ({
    type: {id: typeId, name: typeName, description: ''},
    title,
    description: '',
    accuracy: 1.0,
    data,
})

beforeEach(() => {
    localStorage.clear()
    searchAnswer = null
    searchOptions = undefined
    mockPush.mockReset()
    jest.useFakeTimers()
})

afterEach(() => {
    jest.useRealTimers()
})

describe('opening and closing the palette', () => {

    it('is closed until asked for', () => {
        renderPalette()
        expect(dialog()).not.toBeInTheDocument()
    })

    it('opens on Ctrl+K', () => {
        renderPalette()
        fireEvent.keyDown(document.body, {key: 'k', ctrlKey: true})
        expect(dialog()).toBeInTheDocument()
        expect(combobox()).toHaveFocus()
    })

    it('opens on Cmd+K', () => {
        renderPalette()
        fireEvent.keyDown(document.body, {key: 'k', metaKey: true})
        expect(dialog()).toBeInTheDocument()
    })

    it('opens on Cmd+K from inside a focused input', () => {
        renderPalette()
        const input = screen.getByTestId('some-input')
        input.focus()
        fireEvent.keyDown(input, {key: 'k', metaKey: true})
        expect(dialog()).toBeInTheDocument()
    })

    it('opens on / when no input is focused', () => {
        renderPalette()
        fireEvent.keyDown(screen.getByTestId('some-text'), {key: '/'})
        expect(dialog()).toBeInTheDocument()
    })

    it('does not open on / typed into an input', () => {
        renderPalette()
        const input = screen.getByTestId('some-input')
        input.focus()
        fireEvent.keyDown(input, {key: '/'})
        expect(dialog()).not.toBeInTheDocument()
    })

    it('does not open on a plain K', () => {
        renderPalette()
        fireEvent.keyDown(document.body, {key: 'k'})
        expect(dialog()).not.toBeInTheDocument()
    })

    it('is a dialog with a name, holding a combobox driving a listbox', () => {
        recordRecentlyVisited({type: 'project', id: 1, name: 'ontrack', context: null, href: '/project/1'})
        renderPalette()
        fireEvent.keyDown(document.body, {key: 'k', ctrlKey: true})
        expect(screen.getByRole('dialog', {name: 'Search'})).toBeInTheDocument()
        const listbox = screen.getByRole('listbox')
        expect(combobox()).toHaveAttribute('aria-controls', listbox.id)
        expect(combobox()).toHaveAttribute('aria-expanded', 'true')
    })

    it('closes on Esc and gives the focus back', () => {
        renderPalette()
        const input = screen.getByTestId('some-input')
        input.focus()
        fireEvent.keyDown(input, {key: 'k', ctrlKey: true})
        press('Escape')
        expect(dialog()).not.toBeInTheDocument()
        expect(input).toHaveFocus()
    })

})

describe('before typing', () => {

    it('lists the recently visited entities, the last one first', () => {
        recordRecentlyVisited({type: 'project', id: 1, name: 'ontrack', context: null, href: '/project/1'})
        recordRecentlyVisited({type: 'branch', id: 10, name: 'main', context: 'ontrack', href: '/branch/10'})
        renderPalette()
        fireEvent.keyDown(document.body, {key: 'k', ctrlKey: true})
        const group = screen.getByRole('group', {name: 'Recently visited'})
        const listed = within(group).getAllByRole('option')
        expect(listed).toHaveLength(2)
        expect(listed[0]).toHaveTextContent('main')
        expect(listed[0]).toHaveTextContent('ontrack')
        expect(listed[1]).toHaveTextContent('ontrack')
    })

    it('does not search', () => {
        renderPalette()
        fireEvent.keyDown(document.body, {key: 'k', ctrlKey: true})
        expect(searchOptions.condition).toBe(false)
    })

})

describe('moving and opening', () => {

    const withThreeVisits = () => {
        recordRecentlyVisited({type: 'project', id: 1, name: 'one', context: null, href: '/project/1'})
        recordRecentlyVisited({type: 'project', id: 2, name: 'two', context: null, href: '/project/2'})
        recordRecentlyVisited({type: 'project', id: 3, name: 'three', context: null, href: '/project/3'})
        renderPalette()
        fireEvent.keyDown(document.body, {key: 'k', ctrlKey: true})
    }

    it('starts on the first option', () => {
        withThreeVisits()
        expect(activeOption()).toHaveTextContent('three')
        expect(activeOption()).toHaveAttribute('aria-selected', 'true')
    })

    it('moves down and up', () => {
        withThreeVisits()
        press('ArrowDown')
        expect(activeOption()).toHaveTextContent('two')
        press('ArrowDown')
        expect(activeOption()).toHaveTextContent('one')
        press('ArrowUp')
        expect(activeOption()).toHaveTextContent('two')
    })

    it('wraps around at both ends', () => {
        withThreeVisits()
        press('ArrowUp')
        expect(activeOption()).toHaveTextContent('one')
        press('ArrowDown')
        expect(activeOption()).toHaveTextContent('three')
    })

    it('opens the active option on Enter, and closes', () => {
        withThreeVisits()
        press('ArrowDown')
        press('Enter')
        expect(mockPush).toHaveBeenCalledWith('/project/2')
        expect(dialog()).not.toBeInTheDocument()
    })

    it('opens the active option in a new tab on Ctrl+Enter or Cmd+Enter', () => {
        const open = jest.spyOn(window, 'open').mockImplementation(() => null)
        try {
            withThreeVisits()
            press('Enter', {ctrlKey: true})
            expect(open).toHaveBeenCalledWith('/project/3', '_blank', 'noopener')
            press('ArrowDown')
            press('Enter', {metaKey: true})
            expect(open).toHaveBeenCalledWith('/project/2', '_blank', 'noopener')
            expect(mockPush).not.toHaveBeenCalled()
        } finally {
            open.mockRestore()
        }
    })

    it('opens an option on a click', () => {
        withThreeVisits()
        fireEvent.click(screen.getByRole('option', {name: /two/}))
        expect(mockPush).toHaveBeenCalledWith('/project/2')
    })

})

describe('typing', () => {

    const openPalette = () => {
        renderPalette()
        fireEvent.keyDown(document.body, {key: 'k', ctrlKey: true})
    }

    it('lists the user menu items matching the text', async () => {
        openPalette()
        await type('sett')
        const group = screen.getByRole('group', {name: 'Menu'})
        const listed = within(group).getAllByRole('option')
        expect(listed).toHaveLength(1)
        expect(listed[0]).toHaveTextContent('Settings')
        press('Enter')
        expect(mockPush).toHaveBeenCalledWith('/core/admin/settings')
    })

    it('matches the user menu items on their group and every word, whatever the case', async () => {
        openPalette()
        await type('SYSTEM jobs')
        const listed = within(screen.getByRole('group', {name: 'Menu'})).getAllByRole('option')
        expect(listed.map(it => it.textContent)).toEqual([expect.stringContaining('System jobs')])
    })

    it('does not search below 2 characters', async () => {
        openPalette()
        await type('o')
        expect(searchOptions.condition).toBe(false)
        expect(screen.queryByRole('option', {name: /See all results/})).not.toBeInTheDocument()
    })

    it('searches the best 3 results of each type, once the typing has settled', async () => {
        openPalette()
        fireEvent.change(combobox(), {target: {value: 'ont'}})
        expect(searchOptions.condition).toBe(false)
        await act(async () => {
            jest.advanceTimersByTime(1000)
        })
        expect(searchOptions.condition).toBe(true)
        expect(searchOptions.variables).toEqual({query: 'ont', perType: 3})
    })

    it('groups the results by type, with the count of each type', async () => {
        searchAnswer = {
            search: {
                total: 14,
                message: null,
                facets: [
                    {type: {id: 'project', name: 'Project'}, count: 12},
                    {type: {id: 'branch', name: 'Branch'}, count: 2},
                ],
                items: [
                    result('project', 'Project', 'ontrack', {project: {id: 1, name: 'ontrack'}}),
                    result('branch', 'Branch', 'ontrack/main', {branch: {id: 10, name: 'main', project: {id: 1, name: 'ontrack'}}}),
                    result('project', 'Project', 'ontrack-demo', {project: {id: 2, name: 'ontrack-demo'}}),
                ],
            }
        }
        openPalette()
        await type('ontrack')

        const projects = screen.getByRole('group', {name: 'Project (12)'})
        expect(within(projects).getAllByRole('option')).toHaveLength(2)
        expect(within(projects).getAllByTestId('dynamic-framework/search/project/Result')).toHaveLength(2)
        const branches = screen.getByRole('group', {name: 'Branch (2)'})
        expect(within(branches).getAllByRole('option')).toHaveLength(1)

        // The best result first, a project not being said to be in itself
        expect(activeOption()).toHaveAccessibleName('ontrack, Project')
        press('Enter')
        expect(mockPush).toHaveBeenCalledWith('/project/1')
    })

    it('shows a capped count as the cap followed by a plus', async () => {
        searchAnswer = {
            search: {
                total: 1002,
                capped: true,
                message: null,
                facets: [
                    {type: {id: 'project', name: 'Project'}, count: 1000, capped: true},
                    {type: {id: 'branch', name: 'Branch'}, count: 2, capped: false},
                ],
                items: [
                    result('project', 'Project', 'ontrack', {project: {id: 1, name: 'ontrack'}}),
                    result('branch', 'Branch', 'ontrack/main', {branch: {id: 10, name: 'main', project: {id: 1, name: 'ontrack'}}}),
                ],
            }
        }
        openPalette()
        await type('ontrack')

        expect(screen.getByRole('group', {name: 'Project (1000+)'})).toBeInTheDocument()
        expect(screen.getByText('(1000+)')).toBeInTheDocument()
        expect(screen.getByRole('group', {name: 'Branch (2)'})).toBeInTheDocument()
    })

    it('opens each kind of result at its page', async () => {
        searchAnswer = {
            search: {
                total: 3,
                message: null,
                facets: [],
                items: [
                    result('scm-commit', 'SCM Commit', 'abcdef', {item: {id: 'abcdef1234', shortId: 'abcdef', projectName: 'ontrack'}}),
                    result('scm-issue', 'SCM Issue', 'ISS-1', {item: {key: 'ISS-1', displayKey: '#1', projectName: 'ontrack'}}),
                    result('build', 'Build', '1.0.0', {build: {id: 100, name: '1.0.0'}}),
                ],
            }
        }
        openPalette()
        await type('abc')
        press('Enter')
        expect(mockPush).toHaveBeenLastCalledWith('/extension/scm/ontrack/commit-info/abcdef1234')
    })

    it('names a result by its title, its type and its project', async () => {
        searchAnswer = {
            search: {
                total: 2,
                message: null,
                facets: [],
                items: [
                    result('scm-commit', 'SCM Commit', 'abcdef', {item: {id: 'abcdef1234', shortId: 'abcdef', projectName: 'ontrack'}}),
                    result('branch', 'Branch', 'ontrack/main', {branch: {id: 10, name: 'main', project: {id: 1, name: 'ontrack'}}}),
                ],
            }
        }
        openPalette()
        await type('abc')
        expect(screen.getByRole('option', {name: 'abcdef, SCM Commit, in ontrack'})).toBeInTheDocument()
        expect(screen.getByRole('option', {name: 'ontrack/main, Branch, in ontrack'})).toBeInTheDocument()
    })

    it('shows the message of the search', async () => {
        searchAnswer = {search: {total: 0, message: 'Search index is being built', facets: [], items: []}}
        openPalette()
        await type('ontrack')
        expect(screen.getByText('Search index is being built')).toBeInTheDocument()
    })

    it('ends on an entry opening all the results', async () => {
        openPalette()
        await type('my project')
        const all = screen.getByRole('option', {name: /See all results/})
        fireEvent.click(all)
        expect(mockPush).toHaveBeenCalledWith('/search?q=my%20project')
    })

    it('starts again on the first option when the text changes', async () => {
        openPalette()
        await type('s')
        press('ArrowDown')
        expect(activeOption()).toHaveTextContent('System jobs')
        await type('se')
        expect(activeOption()).toHaveTextContent('Settings')
    })

})

describe('the search button of the navigation bar', () => {

    const renderWithButton = () => render(
        <UserContext.Provider value={user}>
            <CommandPaletteProvider>
                <SearchPaletteButton/>
            </CommandPaletteProvider>
        </UserContext.Provider>
    )

    const withPlatform = (platform, code) => {
        const spy = jest.spyOn(navigator, 'platform', 'get').mockReturnValue(platform)
        try {
            code()
        } finally {
            spy.mockRestore()
        }
    }

    it('opens the palette', () => {
        renderWithButton()
        fireEvent.click(screen.getByRole('button', {name: /Search/}))
        expect(dialog()).toBeInTheDocument()
        expect(combobox()).toHaveFocus()
    })

    it('shows ⌘K on a Mac', () => {
        withPlatform('MacIntel', () => {
            renderWithButton()
            const button = screen.getByRole('button', {name: /Search/})
            expect(button).toHaveTextContent('⌘K')
            expect(button).toHaveAttribute('aria-keyshortcuts', 'Meta+K')
        })
    })

    it('shows Ctrl K elsewhere', () => {
        withPlatform('Win32', () => {
            renderWithButton()
            const button = screen.getByRole('button', {name: /Search/})
            expect(button).toHaveTextContent('Ctrl K')
            expect(button).toHaveAttribute('aria-keyshortcuts', 'Control+K')
        })
    })

})
