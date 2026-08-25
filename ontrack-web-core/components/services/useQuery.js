import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import {useEffect, useState} from "react";
import {getGraphQLErrors} from "@components/services/graphql-utils";
import {useReloadState} from "@components/common/StateUtils";
import {genericGraphQLErrorMessage} from "@components/services/GraphQL";

/**
 * @deprecated Use the `GraphQL/useQuery` hook instead.
 */
export const useQuery = (query, {
    variables,
    skipInitialFetch = false,
    initialData,
    condition = true,
    deps = [],
    dataFn
} = {}) => {
    const client = useGraphQLClient()

    const [loading, setLoading] = useState(!skipInitialFetch)
    const [error, setError] = useState('')
    const [data, setData] = useState(initialData)

    const [reloadState, reload] = useReloadState()

    useEffect(() => {
        if (client && condition && (reloadState > 0 || !skipInitialFetch)) {
            const runQuery = async () => {
                setError('')
                setLoading(true)
                try {
                    const data = await client.request(query, variables)
                    const errors = getGraphQLErrors(data)
                    if (errors && errors.length > 0) {
                        // `getGraphQLErrors` returns the messages themselves, not error objects
                        setError(errors[0])
                    } else if (dataFn) {
                        setData(dataFn(data))
                    } else {
                        setData(data)
                    }
                } catch (ex) {
                    // Without this, a rejected request left `error` empty and the caller had
                    // nothing to render: the failure was indistinguishable from having no data.
                    setError(ex.message || genericGraphQLErrorMessage)
                } finally {
                    setLoading(false)
                }
            }
            // noinspection JSIgnoredPromiseFromCall
            runQuery()
        }
    }, [client, condition, reloadState, ...deps])

    return {
        loading,
        error,
        data,
        setData,
        refetch: reload,
    }
}