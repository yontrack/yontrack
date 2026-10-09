# GraphQL query limits

The GraphQL API at `/graphql` limits how much work one query can make the server do. Without a
limit, a single request with many aliases, repeated directives, or a deeply nested selection could
keep the server busy for a long time, and any user allowed to query the API could send one.

A query going over a limit is **rejected with one error**, before any of it runs:

```json
{
  "errors": [
    {
      "message": "The query has a depth of 31, more than the 25 allowed (ontrack.config.graphql.limits.max-depth).",
      "extensions": { "classification": "ExecutionAborted" }
    }
  ]
}
```

!!! note "New in 6.0"

    Earlier versions accepted queries of any size.

## The limits

| Property (`ontrack.config.graphql.limits.*`) | Default | What it counts |
|----------------------------------------------|---------|----------------|
| `max-aliases`                                | 30 | Aliases in the query document (`a: projects { … }`) |
| `max-directives-per-location`                | 3 | Directives on one field, fragment, fragment spread, operation or variable. A directive that is not declared repeatable, such as `@include`, may also never appear twice on one location. |
| `max-depth`                                  | 25 | Nested field levels: `{ projects { name } }` has a depth of 2 |
| `max-complexity`                             | 1000 | Fields in the query, once its fragments are expanded. A list counts once, whatever its size. |

The defaults sit well above the largest queries sent by the Yontrack UI (the
[mobile UI](../mobile/index.md) included), the `yontrack` CLI, the MCP server and the standard
introspection query:

| Largest query measured in 6.0 | Aliases | Directives per location | Depth | Complexity |
|-------------------------------|---------|-------------------------|-------|------------|
| Yontrack UI, desktop and mobile | 4     | 1                       | 16    | 177        |
| `yontrack` CLI                | 0       | 1                       | 5     | 16         |
| MCP server                    | 0       | 0                       | 7     | 29         |
| Standard introspection query  | 0       | 0                       | 13    | 62         |

If one of your own clients sends larger queries, raise the limit it goes over in the
configuration of the Yontrack **API** container:

```yaml
ontrack:
  config:
    graphql:
      limits:
        max-depth: 30
```

or, as an environment variable:

```yaml
ONTRACK_CONFIG_GRAPHQL_LIMITS_MAX_DEPTH: "30"
```

## Watching the limits before enforcing them

Set `ontrack.config.graphql.limits.mode` to `WARN` (`ONTRACK_CONFIG_GRAPHQL_LIMITS_MODE: WARN`) to
run the queries going over a limit all the same. Each of them then logs a warning naming the limit,
the value it measured and the operation:

```
[graphql] Limit not enforced for operation ProjectBuilds: The query has a depth of 31, more than the 25 allowed (ontrack.config.graphql.limits.max-depth).
```

In both modes, every query going over a limit is counted in the
`ontrack_graphql_limits_exceeded_total` metric, tagged with the `limit` it went over and the
`mode` (`enforce` or `warn`). A count that stays at zero in `WARN` mode means the limits can be
enforced safely. See the [metrics](../operations/metrics.md).

The properties are described with the other
[general configuration properties](../generated/configurations/net.nemerosa.ontrack.model.support.OntrackConfigProperties.md) as well.
