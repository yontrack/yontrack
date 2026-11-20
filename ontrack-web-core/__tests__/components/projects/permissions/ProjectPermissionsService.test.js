import {
    grantProjectRoles,
    groupProjectPermissionsByTarget,
    parsePermissionTarget,
    projectRolesOf,
    revokeProjectRole
} from "@components/projects/permissions/ProjectPermissionsService"
import {callGraphQL} from "@components/services/GraphQL"

jest.mock("../../../../components/services/GraphQL", () => ({
    useQuery: jest.fn(),
    callGraphQL: jest.fn(),
}))

const participant = {id: 'PARTICIPANT', name: 'Participant', description: 'Can add comments'}
const readOnly = {id: 'READ_ONLY', name: 'Read Only', description: 'Can see the project'}
const owner = {id: 'OWNER', name: 'Project owner', description: 'Can do everything'}

const releaseEngineers = {id: '3', name: 'Release engineers', description: 'Ship things'}
const developers = {id: '2', name: 'Developers', description: null}
const alice = {id: '10', email: 'alice@example.com', fullName: 'Alice'}

describe('One row per account or group', () => {

    it('gathers all the roles of a group on the project in one row', () => {
        const rows = groupProjectPermissionsByTarget([
            {...participant, groups: [releaseEngineers], accounts: []},
            {...readOnly, groups: [releaseEngineers], accounts: []},
            {...owner, groups: [], accounts: []},
        ])
        expect(rows).toEqual([
            {
                key: 'GROUP-3',
                target: {type: 'GROUP', id: '3', name: 'Release engineers', description: 'Ship things'},
                roles: [participant, readOnly],
            },
        ])
    })

    it('lists the groups first, then the accounts, each by name, an account named by its email', () => {
        const rows = groupProjectPermissionsByTarget([
            {...participant, groups: [releaseEngineers], accounts: [alice]},
            {...owner, groups: [developers], accounts: []},
        ])
        expect(rows.map(it => it.key)).toEqual(['GROUP-2', 'GROUP-3', 'ACCOUNT-10'])
        expect(rows[2].target).toEqual({type: 'ACCOUNT', id: '10', name: 'alice@example.com', description: 'Alice'})
        expect(rows[2].roles).toEqual([participant])
    })

    it('has no row when nobody holds a role, or before the project is loaded', () => {
        expect(groupProjectPermissionsByTarget([{...participant, groups: [], accounts: []}])).toEqual([])
        expect(groupProjectPermissionsByTarget(undefined)).toEqual([])
    })

    it('offers every project role, without who holds it', () => {
        expect(projectRolesOf([{...participant, groups: [releaseEngineers], accounts: [alice]}])).toEqual([participant])
        expect(projectRolesOf(null)).toEqual([])
    })

    it('reads the value of the target picker', () => {
        expect(parsePermissionTarget('GROUP-3')).toEqual({type: 'GROUP', id: '3'})
        expect(parsePermissionTarget('ACCOUNT-10')).toEqual({type: 'ACCOUNT', id: '10'})
    })
})

describe('Granting and revoking project roles', () => {

    beforeEach(() => {
        callGraphQL.mockReset()
    })

    it('grants each role to a group in turn', async () => {
        callGraphQL.mockResolvedValue({grantProjectRoleToAccountGroup: {errors: []}})
        await grantProjectRoles({
            projectId: '7',
            target: {type: 'GROUP', id: '3'},
            projectRoles: ['PARTICIPANT', 'READ_ONLY'],
        })
        expect(callGraphQL).toHaveBeenCalledTimes(2)
        expect(callGraphQL.mock.calls[0][0].query).toContain('grantProjectRoleToAccountGroup')
        expect(callGraphQL.mock.calls[0][0].variables).toEqual({projectId: 7, id: 3, projectRole: 'PARTICIPANT'})
        expect(callGraphQL.mock.calls[1][0].variables).toEqual({projectId: 7, id: 3, projectRole: 'READ_ONLY'})
    })

    it('grants a role to an account', async () => {
        callGraphQL.mockResolvedValue({grantProjectRoleToAccount: {errors: []}})
        await grantProjectRoles({projectId: 7, target: {type: 'ACCOUNT', id: '10'}, projectRoles: ['OWNER']})
        expect(callGraphQL.mock.calls[0][0].query).toContain('grantProjectRoleToAccount(')
        expect(callGraphQL.mock.calls[0][0].variables).toEqual({projectId: 7, id: 10, projectRole: 'OWNER'})
    })

    it('stops at the first refusal, naming the refused role', async () => {
        callGraphQL.mockResolvedValue({grantProjectRoleToAccountGroup: {errors: [{message: 'Not allowed'}]}})
        await expect(grantProjectRoles({
            projectId: 7,
            target: {type: 'GROUP', id: '3'},
            projectRoles: ['PARTICIPANT', 'READ_ONLY'],
        })).rejects.toThrow('Cannot grant the PARTICIPANT role: Not allowed')
        expect(callGraphQL).toHaveBeenCalledTimes(1)
    })

    it('revokes one role from a group or from an account', async () => {
        callGraphQL.mockResolvedValue({})
        await revokeProjectRole({projectId: 7, target: {type: 'GROUP', id: '3'}, projectRole: 'PARTICIPANT'})
        await revokeProjectRole({projectId: 7, target: {type: 'ACCOUNT', id: '10'}, projectRole: 'OWNER'})
        expect(callGraphQL.mock.calls[0][0].query).toContain('revokeProjectRoleFromAccountGroup')
        expect(callGraphQL.mock.calls[0][0].variables).toEqual({projectId: 7, id: 3, projectRole: 'PARTICIPANT'})
        expect(callGraphQL.mock.calls[1][0].query).toContain('revokeProjectRoleFromAccount(')
        expect(callGraphQL.mock.calls[1][0].variables).toEqual({projectId: 7, id: 10, projectRole: 'OWNER'})
    })
})
