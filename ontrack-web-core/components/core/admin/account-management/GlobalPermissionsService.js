import {useMutation, useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";

export const useGlobalPermissions = ({refreshState}) => {
    const {data: globalPermissions, loading} = useQuery(
        gql`
            query GlobalPermissions {
                globalPermissions {
                    target {
                        type
                        id
                        name
                        description
                    }
                    role {
                        id
                        name
                        description
                    }
                }
            }
        `,
        {
            initialData: [],
            deps: [refreshState],
            dataFn: data => data.globalPermissions,
        }
    )
    return {globalPermissions, loading}
}

export const usePermissionTargets = ({token}) => {
    const {data: permissionTargets, loading} = useQuery(
        gql`
            query PermissionTargets($token: String = null) {
                permissionTargets(token: $token) {
                    type
                    id
                    name
                    description
                }
            }
        `,
        {
            variables: {token},
            deps: [token],
            initialData: [],
            dataFn: data => data.permissionTargets,
        }
    )
    return {permissionTargets, loading}
}

export const useMutationAddGlobalPermissionToAccount = ({onSuccess}) => {
    const {mutate, loading} = useMutation(
        gql`
            mutation AddGlobalPermissionToAccount(
                $accountId: Int!,
                $role: String!,
            ) {
                grantGlobalRoleToAccount(input: {
                    accountId: $accountId,
                    globalRole: $role,
                }) {
                    errors {
                        message
                    }
                }
            }
        `,
        {
            userNodeName: 'grantGlobalRoleToAccount',
            onSuccess,
        }
    )

    const addGlobalPermissionToAccount = async ({accountId, role}) => {
        await mutate({
            accountId: Number(accountId),
            role,
        })
    }

    return {addGlobalPermissionToAccount, loading}
}

export const useMutationAddGlobalPermissionToAccountGroup = ({onSuccess}) => {
    const {mutate, loading} = useMutation(
        gql`
            mutation AddGlobalPermissionToAccountGroup(
                $accountGroupId: Int!,
                $role: String!,
            ) {
                grantGlobalRoleToAccountGroup(input: {
                    accountGroupId: $accountGroupId,
                    globalRole: $role,
                }) {
                    errors {
                        message
                    }
                }
            }
        `,
        {
            userNodeName: 'grantGlobalRoleToAccountGroup',
            onSuccess,
        }
    )

    const addGlobalPermissionToAccountGroup = async ({accountGroupId, role}) => {
        await mutate({
            accountGroupId: Number(accountGroupId),
            role,
        })
    }

    return {addGlobalPermissionToAccountGroup, loading}
}

export const useMutationRevokeGlobalRoleFromAccount = ({onSuccess}) => {
    const {mutate, loading} = useMutation(
        gql`
            mutation RevokeGlobalRoleFromAccount(
                $accountId: Int!,
                $role: String!,
            ) {
                revokeGlobalRoleFromAccount(input: {
                    accountId: $accountId,
                    globalRole: $role,
                }) {
                    errors {
                        message
                    }
                }
            }
        `,
        {
            userNodeName: 'revokeGlobalRoleFromAccount',
            onSuccess,
        }
    )

    const revokeGlobalRoleFromAccount = async ({accountId, role}) => {
        await mutate({
            accountId: Number(accountId),
            role,
        })
    }

    return {revokeGlobalRoleFromAccount, loading}
}

export const useMutationRevokeGlobalRoleFromAccountGroup = ({onSuccess}) => {
    const {mutate, loading} = useMutation(
        gql`
            mutation RevokeGlobalRoleFromAccountGroup(
                $accountGroupId: Int!,
                $role: String!,
            ) {
                revokeGlobalRoleFromAccountGroup(input: {
                    accountGroupId: $accountGroupId,
                    globalRole: $role,
                }) {
                    errors {
                        message
                    }
                }
            }
        `,
        {
            userNodeName: 'revokeGlobalRoleFromAccountGroup',
            onSuccess,
        }
    )

    const revokeGlobalRoleFromAccountGroup = async ({accountGroupId, role}) => {
        await mutate({
            accountGroupId: Number(accountGroupId),
            role,
        })
    }

    return {revokeGlobalRoleFromAccountGroup, loading}
}

/**
 * Groups the global permissions - one per target and role - into one entry per target, holding
 * all its roles, in the order of the permissions.
 */
export const groupGlobalPermissionsByTarget = (globalPermissions) => {
    const index = {}
    const rows = []
    globalPermissions.forEach(({target, role}) => {
        const key = `${target.type}-${target.id}`
        let row = index[key]
        if (!row) {
            row = {key, target, roles: []}
            index[key] = row
            rows.push(row)
        }
        row.roles.push(role)
    })
    return rows
}
