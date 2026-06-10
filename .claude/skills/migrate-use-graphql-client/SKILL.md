---
name: migrate-use-graphql-client
description: Migrate one or more frontend components from the deprecated useGraphQLClient hook to useQuery/useMutation from @components/services/GraphQL. Use when asked to migrate or replace useGraphQLClient usage.
user-invocable: true
---

# /migrate-use-graphql-client — Migrate from useGraphQLClient to useQuery

Arguments passed: `$ARGUMENTS`

---

## Step 1 — Identify target files

If `$ARGUMENTS` is a file path, migrate only that file.

Otherwise, find all files still using the deprecated hook:

```bash
grep -rl "useGraphQLClient" ontrack-web-core/components --include="*.js" \
  | grep -v "ConnectionContextProvider.js"
```

Migrate each file found, one at a time.

---

## Step 2 — Read the file

Read the full content of the file before making any changes. Understand:
- How many separate queries or mutations it contains
- What state is derived purely from fetch results vs. what drives pagination/filtering
- Whether the `.then()` handler does a simple assignment or something more complex (append, side effects)

---

## Step 3 — Update imports

**Remove:**
```javascript
import {useGraphQLClient} from "@components/providers/ConnectionContextProvider"
```

**Remove** `import {gql} from "graphql-request"` **only if** `gql` is used exclusively as a template-tag on query strings being passed to `client.request()`. If `gql` is used anywhere else, keep it. Note: `useQuery` does not require the `gql` tag — plain template strings work fine.

**Add** (choose what is needed):
```javascript
import {useQuery} from "@components/services/GraphQL"
// or
import {useQuery, useMutation} from "@components/services/GraphQL"
// or
import {useQuery, callGraphQL} from "@components/services/GraphQL"
```

---

## Step 4 — Replace each data-fetching useEffect

**Old pattern to remove:**
```javascript
const client = useGraphQLClient()
const [data, setData] = useState(<initialValue>)
const [loading, setLoading] = useState(false)

useEffect(() => {
    if (client && <condition>) {
        setLoading(true)
        client.request(QUERY, {var1, var2}).then(data => {
            setData(<transform(data)>)
        }).finally(() => {
            setLoading(false)
        })
    }
}, [client, dep1, dep2])
```

**New pattern:**
```javascript
const {data, loading} = useQuery(QUERY, {
    variables: {var1, var2},
    deps: [dep1, dep2],       // same deps minus `client`
    condition: <condition>,   // omit if always true
    initialData: <initialValue>,
    dataFn: data => <transform(data)>,  // omit if no transformation needed
})
```

**Rules:**
- Drop `client` from the `deps` array — it is no longer needed.
- If the old guard was `if (client && someVar)`, map `someVar` to `condition: !!someVar`.
- If the `.then()` body simply assigns one value, use `dataFn` to inline it.
- If the `.then()` body appends paginated results or triggers side effects, keep a separate `useEffect` watching the returned `data`:
  ```javascript
  const {data: rawData, loading} = useQuery(QUERY, { variables, deps })

  useEffect(() => {
      if (rawData) {
          // append / side effects here
      }
  }, [rawData])
  ```
- Remove the `const [loading, setLoading] = useState(false)` and the corresponding data `useState` only if they were exclusively managed inside the replaced effect. Keep any state that is also set elsewhere (e.g. by user interaction).
- For multiple independent queries in the same component, introduce multiple `useQuery` calls with distinct variable names.

---

## Step 5 — Handle mutations

`client.request()` calls inside async event handlers (onClick, onSubmit, etc.) are mutations, not queries. Do **not** force them into `useQuery`.

Options (choose the simplest fit):

**Option A — callGraphQL directly** (preferred for simple one-shot mutations):
```javascript
import {callGraphQL} from "@components/services/GraphQL"

const onSubmit = async (values) => {
    const data = await callGraphQL({query: MUTATION, variables: values})
    // handle result
}
```

**Option B — useMutation hook** (preferred when loading/error state from the hook is useful):
```javascript
const {mutate, loading, error} = useMutation(MUTATION, {
    userNodeName: 'myMutationName',
    onSuccess: (result) => { /* ... */ },
})
// later: mutate(variables)
```

---

## Step 6 — Verify

1. Confirm `useGraphQLClient` no longer appears in the migrated file.
2. Confirm the import from `ConnectionContextProvider` is gone.
3. Confirm the `const client = useGraphQLClient()` line is gone.
4. If all target files were migrated, run:
   ```bash
   grep -r "useGraphQLClient" ontrack-web-core/components --include="*.js" \
     | grep -v "ConnectionContextProvider.js"
   ```
   Expect no output.

---

## Step 7 — Report

For each file changed, summarise:
- Which queries were converted to `useQuery`
- Which mutations were converted to `callGraphQL` or `useMutation`
- Any state declarations that were removed
- Any cases where a secondary `useEffect` was kept for side effects

**Do not run the build or start the dev server** — leave that to the user.
