import "@testing-library/jest-dom"
import {fireEvent, render, screen, waitFor, within} from "@testing-library/react"
import ProjectPermissionsView from "@components/projects/permissions/ProjectPermissionsView"
import ProjectPermissionsCommand from "@components/projects/permissions/ProjectPermissionsCommand"

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

const mockUseQuery = jest.fn()
const mockCallGraphQL = jest.fn()

jest.mock("../../../../components/services/GraphQL", () => ({
    useQuery: (...args) => mockUseQuery(...args),
    callGraphQL: (...args) => mockCallGraphQL(...args),
}))

jest.mock("next/head", () => function Head() {
    return null
})

jest.mock("../../../../components/layouts/MainPage", () => function MainPage({title, children}) {
    return (
        <div>
            <h1>{title}</h1>
            {children}
        </div>
    )
})

const participant = {id: 'PARTICIPANT', name: 'Participant', description: 'Can add comments'}
const readOnly = {id: 'READ_ONLY', name: 'Read Only', description: 'Can see the project'}

const project = (authorized = true) => ({
    id: '7',
    name: 'yontrack',
    authorizations: [
        {name: 'project', action: 'permissions', authorized},
    ],
    projectRoles: [
        {...participant, groups: [{id: '3', name: 'Release engineers', description: null}], accounts: []},
        {...readOnly, groups: [{id: '3', name: 'Release engineers', description: null}], accounts: [{id: '10', email: 'alice@example.com', fullName: 'Alice'}]},
    ],
})

// One answer per query, the same object on every render: the target picker sets its options in an
// effect depending on the targets, which a new array on each render would run again and again
const noTargets = {data: [], loading: false, finished: true}

const renderView = (data, error = undefined) => {
    const permissions = {data, loading: false, finished: true, error}
    mockUseQuery.mockImplementation((query) =>
        query.includes('ProjectPermissions') ? permissions : noTargets
    )
    return render(<ProjectPermissionsView id={7}/>)
}

beforeEach(() => {
    mockUseQuery.mockReset()
    mockCallGraphQL.mockReset()
})

describe('The permissions page of a project', () => {

    it('shows one row per group or account, with its roles as tags', () => {
        renderView(project())

        const group = screen.getByTestId('project-roles-GROUP-3')
        expect(within(group).getByText('Participant')).toBeInTheDocument()
        expect(within(group).getByText('Read Only')).toBeInTheDocument()

        const account = screen.getByTestId('project-roles-ACCOUNT-10')
        expect(within(account).getByText('Read Only')).toBeInTheDocument()
        expect(within(account).queryByText('Participant')).not.toBeInTheDocument()

        expect(screen.getByText('alice@example.com')).toBeInTheDocument()
        expect(screen.getByRole('button', {name: 'Add permission'})).toBeInTheDocument()
    })

    it('revokes one role from a group, keeping the others', async () => {
        mockCallGraphQL.mockResolvedValue({revokeProjectRoleFromAccountGroup: {errors: []}})
        renderView(project())

        fireEvent.click(screen.getByRole('button', {name: 'Remove the Participant role from Release engineers'}))
        fireEvent.click(await screen.findByRole('button', {name: 'OK'}))

        await waitFor(() => expect(mockCallGraphQL).toHaveBeenCalledTimes(1))
        const call = mockCallGraphQL.mock.calls[0][0]
        expect(call.query).toContain('revokeProjectRoleFromAccountGroup')
        expect(call.variables).toEqual({projectId: 7, id: 3, projectRole: 'PARTICIPANT'})
    })

    it('says why a revocation was refused', async () => {
        mockCallGraphQL.mockResolvedValue({revokeProjectRoleFromAccount: {errors: [{message: 'Refused'}]}})
        renderView(project())

        fireEvent.click(screen.getByRole('button', {name: 'Remove the Read Only role from alice@example.com'}))
        fireEvent.click(await screen.findByRole('button', {name: 'OK'}))

        expect(await screen.findByText('Refused')).toBeInTheDocument()
    })

    it('manages nothing for a user who may not manage the permissions of the project', () => {
        renderView(project(false))

        expect(screen.getByText('You are not allowed to manage the permissions of this project.')).toBeInTheDocument()
        expect(screen.queryByRole('button', {name: 'Add permission'})).not.toBeInTheDocument()
        expect(screen.queryByTestId('project-roles-GROUP-3')).not.toBeInTheDocument()
    })

    it('looks for the accounts and groups to grant a role to on this very project', () => {
        renderView(project())

        const [, options] = mockUseQuery.mock.calls.find(([query]) => query.includes('permissionTargets'))
        expect(options.variables.projectId).toEqual(7)
    })

    it('says why the permissions cannot be read', () => {
        renderView(null, 'Access denied')

        expect(screen.getByText('The permissions of this project cannot be read: Access denied')).toBeInTheDocument()
        expect(screen.queryByText('This project does not exist, or you are not allowed to see it.')).not.toBeInTheDocument()
    })

    it('says when the project cannot be found', () => {
        renderView(null)

        expect(screen.getByText('This project does not exist, or you are not allowed to see it.')).toBeInTheDocument()
    })
})

describe('The Permissions command of the project page', () => {

    it('links to the permissions page of the project', () => {
        render(<ProjectPermissionsCommand project={{id: 7}}/>)

        expect(screen.getByTestId('project-permissions-command')).toHaveTextContent('Permissions')
        expect(screen.getByRole('link')).toHaveAttribute('href', '/project/7/permissions')
    })
})
