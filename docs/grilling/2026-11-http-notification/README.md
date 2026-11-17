# HTTP call notification channel — grilling session

Grilling session of 2026-11-17 on a new notification channel for Yontrack 6.0 that calls an HTTP API,
optionally waiting for conditions on its JSON response. Issues: [issues.md](issues.md), initiative
`initiative: http-notification`.

## The request

- Choose a verb and a URL: `GET`, `POST`, `PUT` only, no `DELETE`.
- JSON payload only (`POST` and `PUT`).
- Optionally wait for the completion of the call (2xx).
- Optionally wait for conditions on the (JSON) response.
- Open questions: how credentials are managed, and how conditions are defined.

## Research

### In the codebase (6.0, `main`)

- **`webhook` channel** (`ontrack-extension-notifications/.../webhooks/`): POSTs the event envelope
  `{uuid, type, data}` to an admin-registered webhook (name, URL, timeout, encrypted
  `basic` / `bearer` / `header` authentication). It never looks at the status code: a 500 or a connection
  error is still an OK record.
- **Configurations** (Jira, Jenkins, GitLab...): `AbstractConfigurationService`, secrets encrypted in
  `CONFIGURATIONS`, a CasC context per type, the generic configuration GraphQL mutations, one UI page per
  type. Channels reference a configuration by name. No generic HTTP configuration exists.
- **Waiting channels**: Jenkins, GitHub workflow, GitLab and Bitbucket pipelines have a `callMode`
  (`ASYNC` / `SYNC`) and a timeout; `SYNC` blocks inside `publish()` on one of the notification queue
  consumers (5 by default), reporting progress through `outputProgressCallback`. `ASYNC` results are
  pulled: `getNotificationResult()` is recomputed on each read; no background poller exists.
- **Expressions**: only Jayway JsonPath 3 (auto-versioning) and SpEL. No CEL, JMESPath, JSONata, jq.
- **Templating**: no JSON-escape filter; `JsonNode.transform` templates the string leaves of a JSON
  value (Jira custom fields).
- **No SSRF protection** or host allowlist exists for outgoing calls.
- **Workflows** chain a channel's output: `${workflow.<node>.result.<path>}` (text values only).

### Elsewhere

| Style | Who | Shape |
|-------|-----|-------|
| Status codes + body substring | Jenkins HTTP Request, Flagger | `validResponseCodes`, `validResponseContent` |
| Status field mapping | Spinnaker Webhook stage | `statusJsonPath` + `successStatuses` / `canceledStatuses` / `terminalStatuses` |
| Expression over the response | Argo Rollouts / Workflows, Harness, Azure DevOps, Kubernetes / Tekton (CEL) | `successCondition` / `failureCondition`, `eq(root['status'],'x')` |
| No-code assertion list | Grafana Synthetic Monitoring, Datadog | `{target, operator, value}` |

- **Polling** is tri-state almost everywhere: success, failure, or neither (still pending). It is bounded by
  an interval and a timeout, and transport errors are counted apart from failed conditions. Spinnaker
  polls a status URL taken from the `Location` header or a JSON field, rather than repeating the
  initial request.
- **Credentials** are named server-side objects referenced by id (Jenkins credentials, Azure DevOps
  generic service connection, Harness secrets, Spinnaker preconfigured webhooks). Usual types: none,
  basic, bearer, custom header, OAuth2 client credentials, mTLS. Secrets never go in URLs and are masked.
- **Security**: SSRF (allowlists, private ranges, DNS rebinding), redirects carrying credentials
  elsewhere, response size limits, timeouts, TLS toggles.

Sources: Argo Rollouts web metrics, Argo Workflows HTTP template, Flagger webhooks, Spinnaker
`orca-webhook`, Harness HTTP step, Azure DevOps invoke REST API checks, Jenkins HTTP Request plugin,
Grafana Synthetic Monitoring, Datadog API tests, the Kubernetes CEL reference, the OWASP SSRF cheat sheet.

## Decisions

### Channel and credentials

1. **A new channel**, type `http`, display name "HTTP call". `webhook` stays as it is: it pushes Yontrack
   events to a listener, while `http` calls an API as a step of delivery.
2. **Credentials live in a new configuration type, "HTTP"** (`HttpConfiguration`, CasC key `http`):
   name, **base URL**, authentication. Encrypted storage, the generic configuration GraphQL, a page in the
   configurations menu. The authentication types are those of the webhooks (`none`, `basic`, `bearer`,
   `header`), extracted into a shared place rather than copied. OAuth2 client credentials and mTLS are
   out of v1.
3. **The URL is bound to the configuration**: the channel gives a templated **path** (and optional
   query) appended to the base URL. Anything resolving outside it (another host, a `..` escape, an
   absolute URL) is refused, checked after templating. Otherwise a project owner could send an admin's
   token to any host.
4. **A configuration is mandatory** for every call (auth `none` for anonymous APIs). The set of
   configurations *is* the host allowlist, managed by admins; there is no separate allowlist.
5. **Any subscription, in any project, can use any HTTP configuration** by name, as for the other
   configuration types. Restricting configurations per project is a separate, cross-cutting feature.
6. **Connection test**: a `GET` on the base URL with the configured auth. OK unless it is a transport error,
   a 401/403 or a 5xx; a 404 on an API root is not an error. As for every configuration, it runs on save
   only when `configurationTest` is set.

### The call

7. **Verbs**: exactly `GET`, `POST`, `PUT`. A body only on `POST` / `PUT`.
8. **Payload**: a JSON value whose **string leaves are templated** (`JsonNode.transform`, as Jira custom
   fields). No body when none is given; the event envelope is not sent by default. Numbers and booleans are
   written literally, not produced by templates.
9. **Headers**: extra non-secret headers, templated. `Authorization` and any header set by the
   configuration are refused.
10. **The call is always awaited** and a non-2xx or a transport error is always an ERROR. There is no
    fire-and-forget mode: that is the `webhook` channel. The only optional wait is the one on conditions.
11. **Hardening**: redirects are **disabled**, so a 3xx is a failure since it could carry the credentials
    elsewhere. Connect and per-request timeouts apply. The response body is read up to a cap, then
    truncated. **No** private-IP blocking, because internal APIs are the main use and the admin-chosen
    base URL already bounds the target. **No** "ignore TLS errors" toggle: the JVM truststore is used.
12. **HTTP client**: a `RestTemplate` from `restTemplateBuilder()` (ADR 0016), with redirects off through
    `SimpleClientHttpRequestFactory` (`instanceFollowRedirects = false`, the GitLab precedent, which
    honours the JVM proxy properties). Tested with `MockRestServiceServer` / `MockRestTemplateProvider`.

### Conditions and polling

13. **Conditions are polled**: a request is repeated until the condition holds or the timeout expires. A
    one-shot check is polling with a single attempt.
14. **What gets polled**: the initial request is sent **once**. Polling is a `GET` on a **status URL**:
    - the same URL when the verb is `GET`;
    - otherwise an optional **`statusUrl` CEL expression** evaluated on the initial response
      (`response.headers['location']`, `'/deployments/' + response.body.id`), resolved under the base
      URL like decision 3.

    A `POST` / `PUT` with conditions but no `statusUrl` evaluates them on the initial response only.
15. **Condition language: CEL** (`dev.cel:cel`), the Kubernetes and Tekton standard. It is sandboxed, not
    Turing-complete, type-checked, and fits in one text field in the UI and in CasC. Expressions are
    compiled and type-checked when the subscription is saved; an invalid one is a validation error.
16. **CEL context**, and nothing else (no event or templating context, no `request`):
    - `response.status` (int)
    - `response.headers`: a map keyed by lowercased name, holding the first value as a string
    - `response.body`: the parsed JSON, as `dyn`
    - `attempt` (int)

    When conditions are set, a non-JSON or empty body makes the poll fail.
17. **Tri-state outcome**: a `successCondition` (required to wait) and an optional `failureCondition`,
    both evaluated on every response, the initial one included.
    - Success → OK.
    - Failure → ERROR, naming the expression.
    - Neither → still pending; poll again.
    - Still pending when the timeout expires → ERROR "timed out after N s".
    - No conditions at all → OK on 2xx.
18. **Errors while polling**: a transport error, a 5xx or a 429 is tolerated up to 3 consecutive times
    before ERROR; any other 4xx fails at once. The initial request keeps decision 10.
19. **Knobs**: `intervalSeconds` (default 10, minimum 5) and `timeoutSeconds` (default 300), per
    subscription. The timeout is capped by an admin setting.
20. **The wait blocks inside `publish()`** in v1, as the `SYNC` modes of Jenkins, GitHub, GitLab and
    Bitbucket do, with progress pushed through `outputProgressCallback` after each poll. Two known costs,
    to be documented:
    - It holds one notification queue consumer for up to the timeout.
    - A backend restart mid-wait redelivers the message (`ackMode = END`), which re-sends the initial
      `POST` / `PUT`.

    In a workflow, the channel's timeout must fit within the `notification` node's timeout.
    Finishing in the background through a `QueueProcessor`, for this channel and the `SYNC` modes of the
    others, is a 6.1 follow-up to be specified in its own session (H9).

### Output and settings

21. **Output** (`HttpNotificationChannelOutput`):
    - method and final URL;
    - response status, headers and body (parsed when JSON, capped); `Authorization` and `Set-Cookie`
      values are dropped;
    - attempt count and the last condition evaluation.

    No secrets are recorded. It is chainable in workflows (`${workflow.call.result.body.id}`).
22. **Admin settings "HTTP notifications"** (settings page + CasC): `maxTimeoutSeconds` 3600,
    `maxResponseBytes` 65536, `requestTimeoutSeconds` 30 (per single request). There is **no** `enabled`
    flag: without a configuration, no call is possible.

### Testing, demo, mobile

23. **Mock deployment API**: a backend endpoint gated by
    `ontrack.config.extension.notifications.http.mock.enabled`, on in the `dev` profile.
    - `POST /extension/notifications/http/mock/deployments` → 201 with `{id, status: RUNNING}` and a
      `Location` header.
    - `GET …/deployments/{id}` → `RUNNING` until N seconds have passed, then `SUCCEEDED`, or `FAILED`
      when the POST body asked for it (`"outcome": "FAILED"`).
    - It is **stateless**: the creation time and outcome are encoded in the id, so it is safe with
      several replicas.

    It is the target of the KDSL acceptance tests and of the demo.
24. **Demo**: the seed creates an HTTP configuration `demo-deployments` on the instance's own mock
    endpoint, and an `http` subscription on a promotion of `petclinic`. The subscription POSTs a
    deployment, polls `response.headers['location']`, succeeds on `response.body.status == 'SUCCEEDED'`
    and fails on `response.body.status == 'FAILED'`. The mock property joins "What the target instance
    must have", is checked before anything is deleted, and needs a gitops change for the demo's Helm
    values. How the seed learns the backend's own base URL (which may differ from `YONTRACK_URL`) is for
    the implementer to settle and document.
25. **No mobile impact.** The mobile UI edits neither subscriptions nor configurations, and leaves
    notification and executor output out (`WorkflowInstanceScreen.js`; `/extension/notifications/` maps to
    the desktop-only screen).

### Delivery

26. Initiative `initiative: http-notification`, milestone **6.0**, base `main`, issues H1–H8 at
    `status:todo` + `ready-for-agent`. H9 is on **6.1** at `status:tospec`. See [issues.md](issues.md).
