import {graphQLCall, graphQLCallMutation} from "@ontrack/graphql";
import {gql} from "graphql-request";
import {restCallPut} from "@ontrack/rest";

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
        return data.accountGroup
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
     * Gives a group a role on one project. There is no mutation for the permissions, only
     * the REST API the legacy UI used.
     *
     * Scoping the permission to one project rather than granting a global role keeps the
     * change to the shared instance out of the way of the other tests signing in with the
     * same user.
     */
    async setGroupProjectRole(groupName, projectId, role) {
        const group = await this.getGroupByName(groupName)
        await restCallPut(
            this.ontrack.connection,
            `/rest/accounts/permissions/projects/${projectId}/GROUP/${group.id}`,
            {role}
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
