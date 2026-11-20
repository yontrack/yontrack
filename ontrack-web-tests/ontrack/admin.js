import {graphQLCall, graphQLCallMutation} from "@ontrack/graphql";
import {gql} from "graphql-request";

class AdminMgt {

    constructor(ontrack) {
        this.ontrack = ontrack
    }

    async createGroup({name, description} = {description: ""}) {
        const data = await graphQLCallMutation(
            this.ontrack.connection,
            'createAccountGroup',
            gql`
                mutation CreateGroup($name: String!, $description: String) {
                    createAccountGroup(input: {
                        name: $name,
                        description: $description,
                    }) {
                        accountGroup {
                            id
                            name
                            description
                        }
                        errors {
                            message
                        }
                    }
                }
            `,
            {name, description}
        )
        return data.createAccountGroup.accountGroup
    }

    async getGroupByName(groupName) {
        const data = await graphQLCall(
            this.ontrack.connection,
            gql`
                query GetGroupByName($name: String!) {
                    accountGroupByName(name: $name) {
                        id
                        name
                        description
                    }
                }
            `,
            {name: groupName}
        )
        const group = data.accountGroupByName
        if (group) {
            return group
        } else {
            throw new Error(`Cannot find group with name ${groupName}`)
        }
    }

    /**
     * Grants a project role to a group, keeping the other roles it holds on the project.
     *
     * Scoping the permission to one project rather than granting a global role keeps the
     * change to the shared instance out of the way of the other tests signing in with the
     * same user.
     */
    async grantProjectRoleToGroup(group, projectId, projectRole) {
        await graphQLCallMutation(
            this.ontrack.connection,
            'grantProjectRoleToAccountGroup',
            gql`
                mutation GrantProjectRoleToAccountGroup($accountGroupId: Int!, $projectId: Int!, $projectRole: String!) {
                    grantProjectRoleToAccountGroup(input: {
                        accountGroupId: $accountGroupId,
                        projectId: $projectId,
                        projectRole: $projectRole,
                    }) {
                        errors {
                            message
                        }
                    }
                }
            `,
            {accountGroupId: Number(group.id), projectId: Number(projectId), projectRole}
        )
    }

    /**
     * Grants a global role to a group, keeping the other global roles it holds.
     */
    async grantGlobalRoleToGroup(group, globalRole) {
        await graphQLCallMutation(
            this.ontrack.connection,
            'grantGlobalRoleToAccountGroup',
            gql`
                mutation GrantGlobalRoleToAccountGroup($accountGroupId: Int!, $globalRole: String!) {
                    grantGlobalRoleToAccountGroup(input: {
                        accountGroupId: $accountGroupId,
                        globalRole: $globalRole,
                    }) {
                        errors {
                            message
                        }
                    }
                }
            `,
            {accountGroupId: Number(group.id), globalRole}
        )
    }

    async mapGroup(idpGroup, groupName) {

        const group = await this.getGroupByName(groupName)

        await graphQLCallMutation(
            this.ontrack.connection,
            'mapGroup',
            gql`
                mutation MapGroup($idpGroup: String!, $groupId: Int!) {
                    mapGroup(input: {
                        idpGroup: $idpGroup,
                        groupId: $groupId,
                    }) {
                        errors {
                            message
                        }
                    }
                }
            `,
            {idpGroup, groupId: Number(group.id)}
        )
    }

    revokeToken = async (tokenName) => {
        await graphQLCallMutation(
            this.ontrack.connection,
            'revokeToken',
            gql`
                mutation RevokeToken($name: String!) {
                    revokeToken(input: {name: $name}) {
                        errors {
                            message
                        }
                    }
                }
            `,
            {name: tokenName}
        )
    }
}

export const admin = (ontrack) => new AdminMgt(ontrack)
