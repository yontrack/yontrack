"use client"

import {useEffect, useState} from "react";
import {signOut} from "next-auth/react";

export const useQuery = (query, {
    variables = {},
    deps = [],
    initialData = null,
    condition = true,
    dataFn = (data) => data,
} = {}) => {
    const [data, setData] = useState(initialData)
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState()
    const [finished, setFinished] = useState(false)

    useEffect(() => {
        const controller = new AbortController()
        if (condition) {
            setLoading(true)
            callGraphQL({query, variables, signal: controller.signal})
                .then(data => {
                    setData(dataFn(data))
                    setError(null)
                })
                .catch((ex) => {
                    if (ex.name !== 'AbortError') {
                        setError(ex.message)
                        setData(null)
                    }
                })
                .finally(() => {
                    setFinished(true)
                    setLoading(false)
                })
        }
        return () => {
            controller.abort()
        }
    }, [condition, ...deps])

    return {
        data,
        loading,
        error,
        finished,
    }
}

export const useQueries = (queries = [], {
    deps = [],
    condition = true,
} = {}) => {
    const [data, setData] = useState([])
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState()
    const [finished, setFinished] = useState(false)

    useEffect(() => {
        let active = true
        if (condition && queries && queries.length > 0) {
            setLoading(true)
            const promises = queries.map(({query, variables}) => callGraphQL({query, variables}))
            Promise.all(promises)
                .then(results => {
                    if (active) {
                        setData(results)
                        setError(null)
                    }
                })
                .catch((ex) => {
                    if (active) {
                        setError(ex.message)
                        setData([])
                    }
                })
                .finally(() => {
                    if (active) {
                        setFinished(true)
                        setLoading(false)
                    }
                })
        }
        return () => {
            active = false
        }
    }, [condition, ...deps])

    return {
        data,
        loading,
        error,
        finished,
    }
}

export const useMutation = (query, {userNodeName, onSuccess}) => {
    const [data, setData] = useState({})
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState()

    const mutate = async (variables) => {
        setLoading(true)
        try {
            const data = await callGraphQL({query, variables})
            const userNode = data[userNodeName]
            if (userNode) {
                setData(userNode)
                const errors = userNode.errors
                if (errors && errors.length > 0) {
                    setError(errors[0].message)
                } else if (onSuccess) {
                    onSuccess(userNode)
                }
            }
        } finally {
            setLoading(false)
        }
    }

    return {
        mutate,
        data,
        loading,
        error,
    }
}

export async function callGraphQL({
                                      query,
                                      variables,
                                      signal,
                                  }) {
    const res = await fetch('/api/protected/graphql', {
        method: 'POST',
        headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            query,
            variables,
        }),
        signal,
    })
    if (res.ok) {
        return await res.json()
    } else if (res.status === 401) {
        await signOut()
    } else {
        console.error(res)
        let body
        try {
            body = await res.json()
        } catch (ignored) {
            // Keeping the generic message below
        }
        throw new Error(graphQLErrorMessage(body));
    }
}

export const genericGraphQLErrorMessage = "Issue with GraphQL call."

/**
 * The GraphQL errors are returned by the API route as a serialized `ClientError`. This extracts
 * the message of the first error, so that the reason of the failure can be displayed to the user.
 */
export function graphQLErrorMessage(body) {
    return body?.error?.response?.errors?.[0]?.message ?? genericGraphQLErrorMessage
}
