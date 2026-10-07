import {useMutation, useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";

export const useAccounts = ({refreshState, token}) => {
    const {data: accounts, loading} = useQuery(
        gql`
            query Accounts(
                $token: String = "",
            ) {
                accounts(token: $token) {
                    id
                    fullName
                    email
                    kind
                    owner {
                        email
                    }
                    agents {
                        id
                        fullName
                        email
                    }
                    groups {
                        id
                        name
                    }
                    tokens {
                        name
                    }
                }
            }
        `,
        {
            variables: {token},
            initialData: [],
            deps: [refreshState, token],
            dataFn: data => data.accounts,
        }
    )
    return {accounts, loading}
}

export const useAccountGroups = ({refreshState = 0, name}) => {
    const {data: groups, loading} = useQuery(
        gql`
            query AccountGroups(
                $name: String = "",
            ) {
                accountGroups(name: $name) {
                    id
                    name
                    description
                }
            }
        `,
        {
            variables: {name},
            initialData: [],
            deps: [refreshState, name],
            dataFn: data => data.accountGroups,
        }
    )
    return {groups, loading}
}

export const useRevokeAccountTokens = ({onSuccess}) => {
    const {mutate: revokeAccountTokens, loading} = useMutation(
        gql`
            mutation RevokeAccountTokens($accountId: Int!) {
                revokeAccountTokens(input: {accountId: $accountId}) {
                    errors {
                        message
                    }
                }
            }
        `,
        {
            userNodeName: 'revokeAccountTokens',
            onSuccess,
        }
    )
    return {revokeAccountTokens, loading}
}

export const useMutationDeleteAccount = ({onSuccess}) => {
    const {mutate: deleteAccount, loading} = useMutation(
        gql`
            mutation DeleteAccount($accountId: Int!) {
                deleteAccount(input: {accountId: $accountId}) {
                    errors {
                        message
                    }
                }
            }
        `,
        {
            userNodeName: 'deleteAccount',
            onSuccess,
        }
    )
    return {deleteAccount, loading}
}

export const useMutationDeleteAccountGroup = ({onSuccess}) => {
    const {mutate: deleteAccountGroup, loading} = useMutation(
        gql`
            mutation DeleteAccountGroup($accountGroupId: Int!) {
                deleteAccountGroup(input: {id: $accountGroupId}) {
                    errors {
                        message
                    }
                }
            }
        `,
        {
            userNodeName: 'deleteAccountGroup',
            onSuccess,
        }
    )
    return {deleteAccountGroup, loading}
}

export const useMutationRevokeAllTokens = ({refresh}) => {
    const {mutate, loading} = useMutation(
        gql`
            mutation RevokeAllTokens {
                revokeAllTokens {
                    errors {
                        message
                    }
                }
            }
        `,
        {
            userNodeName: 'revokeAllTokens',
            onSuccess: refresh,
        }
    )

    const revokeAllTokens = async () => {
        await mutate({})
    }

    return {revokeAllTokens, loading}
}
