# Calling the GraphQL API

Components need to call the Ontrack GraphQL API.

Always use `useQuery`, `useMutation` or `callGraphQL` from `@components/services/GraphQL`. The
former `useGraphQLClient` hook and the `@components/services/useQuery` hook were removed in 6.0,
and ESLint rejects any import of them.

## Reading data

`useQuery` replaces the `useState` + `useEffect` + request triad:

```javascript
import {useQuery} from "@components/services/GraphQL";

const {data, loading, error, finished} = useQuery(
    gql`
        query MyQuery($id: Int!) {
            myEntity(id: $id) {
                name
            }
        }
    `,
    {
        variables: {id},
        deps: [id],                     // refetches whenever one of these changes
        condition: !!id,                // omit if always true
        initialData: null,
        dataFn: data => data.myEntity,  // omit if no transformation is needed
    }
)
```

A few things to know about it:

* `loading` starts at `false` and only flips inside its effect. When a component must never render
  as "loaded" before the first fetch resolves, use `loading || !finished`.
* A request superseded by a change of the `deps` is aborted: only the answer of the latest request
  is kept. Values needed with the answer (the offset of a page, for example) are best captured in
  `dataFn`, which runs against the request that produced the data.
* On error, `data` is set to `null` and `error` holds the message. Fall back on a module-level
  constant (`data ?? EMPTY`) rather than on an inline `{}` or `[]`, which is a new object at every
  render.
* Values derived from the data (commands, menu items, table rows) are computed while rendering,
  not stored in a `useState` filled by a `useEffect`.

Several independent queries run at once with `useQueries(queries, {deps, condition})`, whose `data`
is the list of their results.

## Mutations

Mutations are usually called upon an action initiated by a user, typically in an `async` function.
`callGraphQL` is the plain call:

```javascript
import {callGraphQL} from "@components/services/GraphQL";
import {processGraphQLErrors} from "@components/services/graphql-utils";

const onAction = async () => {
    const data = await callGraphQL({
        query: gql`
            mutation {
                doSomething {
                    errors {
                        message
                    }
                }
            }
        `,
        variables: {},
    })
    if (processGraphQLErrors(data, 'doSomething', messageApi)) {
        // Success
    }
}
```

If errors must be explicitly collected (for a form for example):

```javascript
const data = await callGraphQL({query, variables})
const errors = getGraphQLErrors(data, 'doSomething')
if (errors.length > 0) {
    // There are some errors
}
```

When the loading and error state of the mutation is useful to the component, `useMutation` holds it:

```javascript
import {useMutation} from "@components/services/GraphQL";

const {mutate, loading, error} = useMutation(MUTATION, {
    userNodeName: 'doSomething',
    onSuccess: (userNode) => { /* ... */ },
})

// later
await mutate(variables)
```

A `UserError` returned in the payload lands in `error`; a transport failure is thrown out of `mutate`.
