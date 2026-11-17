# HTTP call

The `http` channel calls an HTTP API when an event occurs: one `GET`, `POST` or `PUT`, under an
[HTTP configuration](http-configuration.md). Since notifications can be used as
[workflow](../workflows/workflows.md) nodes, a call can also be one step of a workflow, typically to
start a deployment, and it can [wait](#waiting-for-a-condition) for that deployment to finish.

## Configuration

{% raw %}
```yaml
channel: http
channelConfig:
  config: deployments
  method: POST
  path: /deployments?project=${project}
  headers:
    - name: X-Request-Id
      value: "${build}"
  body:
    project: "${project}"
    version: "${build}"
    environment: production
    replicas: 3
    dryRun: false
```
{% endraw %}

* `config` is the name of the [HTTP configuration](http-configuration.md) to call. It gives the base
  URL and the authentication. It is required: the HTTP configurations are the only hosts Yontrack
  calls.
* `method` is `GET` (the default), `POST` or `PUT`.
* `path` is appended to the base URL of the configuration, with an optional query. With a base URL
  `https://deploy.example.com/api`, the path `/deployments?project=x` calls
  `https://deploy.example.com/api/deployments?project=x`. Without any path, the base URL itself is called.
* `headers` are extra headers. `Authorization`, and any header that the authentication of the
  configuration sets (the name of a `header` authentication, for example), are refused whatever their
  case: the credentials come from the configuration only.
* `body` is a JSON value, sent as `application/json`, for `POST` and `PUT` only: a subscription with a
  body on `GET` is refused. Without any body, none is sent; the event itself is never sent.
* `successCondition`, `failureCondition`, `statusUrl`, `intervalSeconds` and `timeoutSeconds` make the
  call [wait for a condition](#waiting-for-a-condition). They are all optional.

## Templating

The `path`, the values of the `headers` and the **string values** of the `body` are
[templates](../../appendix/templating.md) rendered against the event, as plain text. In the example
above, `${build}` becomes the name of the build. Numbers and booleans of the body are sent as they
are: a template always gives a string.

The path is checked **after** templating. It must stay under the base URL of the configuration, since
the call carries its credentials. The call is refused, with an _invalid configuration_ result, when the
path:

* is an absolute URL (`https://...`) or starts with `//`;
* has a `.` or `..` segment, even encoded (`%2e%2e`);
* has a backslash or a fragment (`#...`);
* is not a valid URL: encode the special characters, such as spaces.

## Result

The call is always awaited:

* a `2xx` response is a success;
* any other status is an error, **redirects included**: Yontrack never follows a redirect, since it
  could carry the credentials of the configuration to another host;
* a transport error (unknown host, connection refused, timeout, TLS error) is an error.

The output of the notification has the method, the URL which was called, the status, the headers and
the body of the response, and the number of requests sent. The body is parsed when the response is
JSON, kept as text otherwise. No secret is recorded: the authentication of the configuration is not in
the output, and the `Authorization` and `Set-Cookie` response headers, as well as the headers named
like the ones of the configuration, are left out. When the call [waits](#waiting-for-a-condition), the
output also has the URL which is polled (`statusUrl`) and the outcome of the conditions on the last
response (`condition`: `SUCCESS`, `FAILURE` or `PENDING`); the status, headers and body are the ones
of the last response.

In a workflow, the next nodes can use the response through the
[`workflow`](../../generated/templating/renderables/templating-renderable-workflow.md) templating
function, under the `result` of the node, for example `${workflow.deploy?path=result.body.id}` for a
node named `deploy`. Only text values can be used this way.

## Waiting for a condition

A `2xx` response often only says that the API accepted the request: a deployment was _started_. With a
**success condition**, the call goes on waiting until the API says that the deployment is done.

{% raw %}
```yaml
channel: http
channelConfig:
  config: deployments
  method: POST
  path: /deployments
  body:
    version: "${build}"
  successCondition: "response.body.status == 'SUCCEEDED'"
  failureCondition: "response.body.status == 'FAILED'"
  statusUrl: "response.headers['location']"
  intervalSeconds: 10
  timeoutSeconds: 600
```
{% endraw %}

### Conditions

The conditions are [CEL](https://cel.dev) expressions, the language of the Kubernetes and Tekton
conditions. Each one sees the response, and nothing else (neither the event nor the templating):

| Variable           | Type                  | Content                                                                  |
|--------------------|-----------------------|--------------------------------------------------------------------------|
| `response.status`  | `int`                 | Status code                                                              |
| `response.headers` | `map(string, string)` | Headers, by **lowercase** name, with their **first** value               |
| `response.body`    | `dyn`                 | Body, parsed as JSON                                                     |
| `attempt`          | `int`                 | Number of the request: 1 for the initial one, then 2, 3... for the polls |

For example:

* `response.body.status == 'SUCCEEDED'`
* `response.body.status in ['FAILED', 'CANCELLED']`
* `response.body.steps.all(s, s.state == 'done')`
* `has(response.body.finishedAt)`
* `'x-deployment-done' in response.headers`

The `Authorization` and `Set-Cookie` headers, and the headers set by the configuration, are not in
`response.headers`. Reading a field which is absent from the body is an error: test it with `has(...)`
first when it may be missing.

The **success condition** returns a boolean, and is required to wait. The optional **failure condition**
returns a boolean too. Both are evaluated on every response, the initial one included:

* the success condition holds: the notification is a success;
* the failure condition holds: the notification is an error, naming the condition. When both hold, the
  failure wins;
* neither holds: the call is **still pending**, and is polled again.

When there is a condition, the body of every response must be JSON (its content type is
`application/json` or `*+json`): an empty body, a body which is not JSON, or a body cut at the
[maximum response size](#limits) is an error. So is a condition which cannot be evaluated.

The expressions are compiled and checked when the subscription is saved: a syntax error, an unknown
variable or field (`response.bdy`), a type error (`response.status == '200'`) or a condition which does
not return a boolean is refused, naming the expression. Their evaluation is bounded: an expression
looping too long over a large body is an error.

Without any success condition, the call does not wait: any `2xx` is a success.

### What is polled

The initial request is sent **once**: a `POST` is never sent again. What is polled, with `GET`, is its
**status URL**:

* for a `GET`, the URL of the call itself;
* for a `POST` or a `PUT`, the `statusUrl`: a CEL expression evaluated on the initial response, returning
  a string. For example `response.headers['location']`, or `'/deployments/' + response.body.id`.

The status URL must stay under the base URL of the configuration, which is checked when it is
evaluated:

* an absolute URL, as `Location` headers often hold, is polled when it is under the base URL: same
  scheme, host and port, and under its path. Any other is an error;
* an absolute path already under the path of the base URL (`/api/deployments/12` for the base URL
  `https://deploy.example.com/api`) is polled as it is;
* anything else is a path appended to the base URL, like the [path](#templating) of the call.

As for the path, `.` and `..` segments, backslashes and fragments are refused.

A `POST` or a `PUT` with conditions but without any `statusUrl` is not polled: its conditions are
evaluated on its initial response only, and the notification is an error when they are still pending.

The polls send the extra `headers` of the call and the authentication of the configuration, but no
body.

### Interval, timeout and errors

* `intervalSeconds` (10 by default, 5 at least) is the time between two polls.
* `timeoutSeconds` (300 by default, at least the interval) is the longest wait. It is capped by the
  [maximum timeout](http-configuration.md#http-notifications-settings) of the settings, 3600 seconds by
  default. Still pending when it expires, the call is an error: "timed out after N s".

While polling:

* a transport error, a `5xx` or a `429` is tolerated up to **3 times in a row**: the call is polled
  again, and a 4th in a row is an error;
* any other status which is not a `2xx` (a `4xx`, a redirect) is an error at once.

The initial request does not get this tolerance: any status which is not a `2xx` is an error, whatever
the conditions.

The output of the notification is updated after every poll, so the progress of a long wait shows on the
notification record.

### The cost of waiting

The wait **blocks** the notification while it lasts, as the `SYNC` mode of the Jenkins, GitHub, GitLab
and Bitbucket channels does. This has two known costs:

* the wait holds one of the consumers of the notification queue, for up to the timeout. Other
  notifications are processed by the other consumers;
* a restart of Yontrack while a call waits delivers the notification again: the initial request is then
  **sent again**, a `POST` or a `PUT` included. Make the API safe for it, with an idempotency key in a
  header for example.

In a [workflow](../workflows/workflows.md), the `timeout` of the `notification` node (300 seconds by
default) must be longer than the `timeoutSeconds` of the call, with a margin for the requests
themselves: otherwise the node times out first.

## Limits

The [HTTP notifications settings](http-configuration.md#http-notifications-settings) bound every call:

* `requestTimeoutSeconds` (30 by default) is the timeout of the connection, and of the reading of the
  response;
* `maxResponseBytes` (65536 by default) is the size of the response body which is read: a longer body
  is cut, flagged as `truncated` in the output, and kept as text;
* `maxTimeoutSeconds` (3600 by default) caps the timeout of the [wait](#waiting-for-a-condition) for a
  condition.

## Difference with webhooks

The [`webhook`](../../generated/notifications/notification-backend-webhook.md) channel _pushes_ the
events of Yontrack to a listener: it always sends the event, and does not look at the answer. The
`http` channel calls an API as a step of the delivery: you choose what is sent, and the notification
fails when the API does.

## Testing with the mock deployment API

To try the channel, or a workflow built on it, without any real deployment tool, Yontrack has a **mock
deployment API**. It is a testing aid, disabled by default: enable it only on a demonstration or test
instance, with the `ontrack.config.extension.notifications.http.mock.enabled` property (the
`ONTRACK_CONFIG_EXTENSION_NOTIFICATIONS_HTTP_MOCK_ENABLED` environment variable). The `dev` profile
enables it. When it is disabled, its paths are those of no API at all: refused without an
authentication, `404` with one.

It lives under `/extension/notifications/http/mock` on the backend of Yontrack and, where it is enabled,
answers **without any authentication**: point an [HTTP configuration](http-configuration.md) at it with
the `none` authentication. Only its own paths are open; everything else of Yontrack still requires an
authentication. Its base URL is the one under which the backend reaches **itself**, which may differ
from the URL of the UI, for example `http://localhost:8080/extension/notifications/http/mock`. The
`httpMockDeploymentsUrl` GraphQL query gives it - the loopback URL of the backend, on the port it is
listening on - and exists only where the mock is enabled.

* A `GET` on the base URL itself describes the API, which is what the connection test of the
  configuration reads.
* `POST /deployments` starts a deployment and answers `201`, with its `Location` and the deployment,
  `{"id": "...", "status": "RUNNING", ...}`. The body is optional. It may give the `outcome` of the
  deployment, `SUCCEEDED` (the default) or `FAILED`, and its `durationSeconds`, 5 by default, at most
  3600. Anything else in the body is ignored.
* `GET /deployments/{id}` returns the deployment: `RUNNING` until its duration has elapsed, then
  `SUCCEEDED`, or `FAILED` when it was asked for. An unknown id answers `404`.

It keeps nothing: the creation time, the duration and the outcome of a deployment are written in its id,
so that it works with several replicas, and across restarts.

{% raw %}
```yaml
channel: http
channelConfig:
  config: mock-deployments
  method: POST
  path: /deployments
  body:
    version: "${build}"
    outcome: SUCCEEDED
    durationSeconds: 10
  successCondition: "response.body.status == 'SUCCEEDED'"
  failureCondition: "response.body.status == 'FAILED'"
  statusUrl: "response.headers['location']"
  intervalSeconds: 5
  timeoutSeconds: 60
```
{% endraw %}

## Configuration as Code

The HTTP configuration a call needs is defined [as code](http-configuration.md#configuration-as-code)
like any other, under `ontrack.config.http`; the `channel` and `channelConfig` of a subscription are the
ones of the [example above](#configuration), wherever subscriptions are defined.

## See also

* [HTTP call channel reference](../../generated/notifications/notification-backend-http.md)
* [HTTP configurations](http-configuration.md)
