import {callGraphQL, useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";

/**
 * The project, its authorizations, and its project roles - each with the groups and the
 * accounts holding it on the project.
 */
export const useProjectPermissions = ({id, refreshState}) => {
    const {data: project, loading, finished, error} = useQuery(
        gql`
            query ProjectPermissions($id: Int!) {
                project(id: $id) {
                    id
                    name
                    authorizations {
                        name
                        action
                        authorized
                    }
                    projectRoles {
                        id
                        name
                        description
                        groups {
                            id
                            name
                            description
                        }
                        accounts {
                            id
                            email
                            fullName
                        }
                    }
                }
            }
        `,
        {
            variables: {id: Number(id)},
            deps: [id, refreshState],
            initialData: null,
            dataFn: data => data.project,
        }
    )
    return {project, loading: loading || !finished, error}
}

/**
 * The project roles, without who holds them: the roles which can be granted.
 */
export const projectRolesOf = (projectRoles) =>
    (projectRoles ?? []).map(({id, name, description}) => ({id, name, description}))

/**
 * Turns the project roles - each with the groups and the accounts holding it - into one row per
 * group or account, holding all its roles on the project, in the order of the roles.
 *
 * The rows have the shape of the permission targets: groups first, then accounts, each sorted
 * by name.
 */
export const groupProjectPermissionsByTarget = (projectRoles) => {
    const index = {}
    const add = (target, role) => {
        const key = `${target.type}-${target.id}`
        let row = index[key]
        if (!row) {
            row = {key, target, roles: []}
            index[key] = row
        }
        row.roles.push({id: role.id, name: role.name, description: role.description})
    }
    ;(projectRoles ?? []).forEach(role => {
        (role.groups ?? []).forEach(group => add({
            type: 'GROUP',
            id: group.id,
            name: group.name,
            description: group.description,
        }, role))
        ;(role.accounts ?? []).forEach(account => add({
            type: 'ACCOUNT',
            id: account.id,
            name: account.email,
            description: account.fullName,
        }, role))
    })
    const typeOrder = {GROUP: 0, ACCOUNT: 1}
    return Object.values(index).sort((a, b) =>
        (typeOrder[a.target.type] - typeOrder[b.target.type]) ||
        (a.target.name ?? '').localeCompare(b.target.name ?? '')
    )
}

/**
 * The value of a permission target in `SelectPermissionTarget`, `TYPE-id`, as a target.
 */
export const parsePermissionTarget = (value) => {
    const [type, id] = value.split("-")
    return {type, id}
}

const gqlGrantToAccount = gql`
    mutation GrantProjectRoleToAccount($projectId: Int!, $id: Int!, $projectRole: String!) {
        grantProjectRoleToAccount(input: {projectId: $projectId, accountId: $id, projectRole: $projectRole}) {
            errors {
                message
            }
        }
    }
`

const gqlGrantToAccountGroup = gql`
    mutation GrantProjectRoleToAccountGroup($projectId: Int!, $id: Int!, $projectRole: String!) {
        grantProjectRoleToAccountGroup(input: {projectId: $projectId, accountGroupId: $id, projectRole: $projectRole}) {
            errors {
                message
            }
        }
    }
`

const gqlRevokeFromAccount = gql`
    mutation RevokeProjectRoleFromAccount($projectId: Int!, $id: Int!, $projectRole: String!) {
        revokeProjectRoleFromAccount(input: {projectId: $projectId, accountId: $id, projectRole: $projectRole}) {
            errors {
                message
            }
        }
    }
`

const gqlRevokeFromAccountGroup = gql`
    mutation RevokeProjectRoleFromAccountGroup($projectId: Int!, $id: Int!, $projectRole: String!) {
        revokeProjectRoleFromAccountGroup(input: {projectId: $projectId, accountGroupId: $id, projectRole: $projectRole}) {
            errors {
                message
            }
        }
    }
`

const mutateProjectRole = async ({accountQuery, accountNode, groupQuery, groupNode, projectId, target, projectRole}) => {
    const [query, node] = target.type === 'ACCOUNT' ? [accountQuery, accountNode] : [groupQuery, groupNode]
    const data = await callGraphQL({
        query,
        variables: {
            projectId: Number(projectId),
            id: Number(target.id),
            projectRole,
        },
    })
    const errors = data?.[node]?.errors
    if (errors && errors.length > 0) {
        throw new Error(errors[0].message)
    }
}

/**
 * Grants the roles to an account or a group on the project, one after the other, keeping the
 * roles it holds already. Throws on the first refusal, naming the refused role: the roles before
 * it are granted, the ones after it are not.
 */
export const grantProjectRoles = async ({projectId, target, projectRoles}) => {
    for (const projectRole of projectRoles) {
        try {
            await mutateProjectRole({
                accountQuery: gqlGrantToAccount,
                accountNode: 'grantProjectRoleToAccount',
                groupQuery: gqlGrantToAccountGroup,
                groupNode: 'grantProjectRoleToAccountGroup',
                projectId,
                target,
                projectRole,
            })
        } catch (ex) {
            throw new Error(`Cannot grant the ${projectRole} role: ${ex.message}`)
        }
    }
}

/**
 * Revokes one role from an account or a group on the project, keeping its other roles.
 */
export const revokeProjectRole = async ({projectId, target, projectRole}) => {
    await mutateProjectRole({
        accountQuery: gqlRevokeFromAccount,
        accountNode: 'revokeProjectRoleFromAccount',
        groupQuery: gqlRevokeFromAccountGroup,
        groupNode: 'revokeProjectRoleFromAccountGroup',
        projectId,
        target,
        projectRole,
    })
}
