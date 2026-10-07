import {useMutation, useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";

export const gqlAgentFragment = gql`
    fragment AgentData on Account {
        id
        email
        fullName
        kind
        agentTool
        agentDescription
        owner {
            id
            email
            fullName
        }
        tokens {
            name
            creation
            lastUsed
            validUntil
            valid
        }
    }
`

/**
 * The agents visible to the current user: all of them for an administrator, their own otherwise.
 *
 * @param refreshState Refresh trigger
 * @param mine Restricts the list to the agents of the current user, administrator or not
 */
export const useAgents = ({refreshState, mine = false}) => {
    const {data, loading, finished} = useQuery(
        mine ?
            gql`
                query MyAgents {
                    user {
                        account {
                            agents {
                                ...AgentData
                            }
                        }
                    }
                }
                ${gqlAgentFragment}
            ` :
            gql`
                query Agents {
                    agents {
                        ...AgentData
                    }
                }
                ${gqlAgentFragment}
            `,
        {
            initialData: [],
            deps: [refreshState, mine],
            dataFn: data => (mine ? data.user?.account?.agents : data.agents) ?? [],
        }
    )
    return {agents: data ?? [], loading: loading || !finished}
}

/**
 * One agent, when visible to the current user.
 */
export const useAgent = ({id, refreshState}) => {
    const {data, loading, finished} = useQuery(
        gql`
            query Agent($id: Int!) {
                agents(id: $id) {
                    ...AgentData
                }
            }
            ${gqlAgentFragment}
        `,
        {
            variables: {id},
            condition: !!id,
            deps: [id, refreshState],
            dataFn: data => data.agents?.[0] ?? null,
        }
    )
    return {agent: data, loading: loading || !finished}
}

export const useDeleteAgent = ({onSuccess}) => {
    const {mutate, loading} = useMutation(
        gql`
            mutation DeleteAgent($id: Int!) {
                deleteAgent(input: {id: $id}) {
                    errors {
                        message
                    }
                }
            }
        `,
        {
            userNodeName: 'deleteAgent',
            onSuccess,
        }
    )
    const deleteAgent = async (agent) => {
        await mutate({id: Number(agent.id)})
    }
    return {deleteAgent, loading}
}

export const useGenerateAgentToken = ({onSuccess}) => {
    const {mutate, data, loading, error} = useMutation(
        gql`
            mutation GenerateAgentToken($id: Int!, $name: String!) {
                generateAgentToken(input: {id: $id, name: $name}) {
                    errors {
                        message
                    }
                    token {
                        name
                        value
                    }
                }
            }
        `,
        {
            userNodeName: 'generateAgentToken',
            onSuccess,
        }
    )
    const generateAgentToken = async (agent, name) => {
        await mutate({id: Number(agent.id), name})
    }
    return {generateAgentToken, data, loading, error}
}

export const useRevokeAgentToken = ({onSuccess}) => {
    const {mutate, loading} = useMutation(
        gql`
            mutation RevokeAgentToken($id: Int!, $name: String!) {
                revokeAgentToken(input: {id: $id, name: $name}) {
                    errors {
                        message
                    }
                }
            }
        `,
        {
            userNodeName: 'revokeAgentToken',
            onSuccess,
        }
    )
    const revokeAgentToken = async (agent, name) => {
        await mutate({id: Number(agent.id), name})
    }
    return {revokeAgentToken, loading}
}

export const useRevokeAllAgentTokens = ({onSuccess}) => {
    const {mutate, loading} = useMutation(
        gql`
            mutation RevokeAllAgentTokens($id: Int!) {
                revokeAllAgentTokens(input: {id: $id}) {
                    errors {
                        message
                    }
                }
            }
        `,
        {
            userNodeName: 'revokeAllAgentTokens',
            onSuccess,
        }
    )
    const revokeAllAgentTokens = async (agent) => {
        await mutate({id: Number(agent.id)})
    }
    return {revokeAllAgentTokens, loading}
}

/**
 * The persons an agent can be given to, for an administrator.
 */
export const useHumanAccounts = ({condition = true}) => {
    const {data, loading} = useQuery(
        gql`
            query HumanAccounts {
                accounts {
                    id
                    email
                    fullName
                    kind
                }
            }
        `,
        {
            condition,
            initialData: [],
            dataFn: data => (data.accounts ?? []).filter(account => account.kind === 'HUMAN'),
        }
    )
    return {accounts: data ?? [], loading}
}
