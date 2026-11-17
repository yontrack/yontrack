# HTTP call

The `http` channel calls an HTTP API when an event occurs: one `GET`, `POST` or `PUT`, under an
[HTTP configuration](http-configuration.md). Since notifications can be used as
[workflow](../workflows/workflows.md) nodes, a call can also be one step of a workflow, typically to
start a deployment.

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
like the ones of the configuration, are left out.

In a workflow, the next nodes can use the response through the
[`workflow`](../../generated/templating/renderables/templating-renderable-workflow.md) templating
function, for example `${workflow.deploy?path=body.id}` for a node named `deploy`. Only text
values can be used this way.

## Limits

The [HTTP notifications settings](http-configuration.md#http-notifications-settings) bound every call:

* `requestTimeoutSeconds` (30 by default) is the timeout of the connection, and of the reading of the
  response;
* `maxResponseBytes` (65536 by default) is the size of the response body which is read: a longer body
  is cut, flagged as `truncated` in the output, and kept as text.

## Difference with webhooks

The [`webhook`](../../generated/notifications/notification-backend-webhook.md) channel _pushes_ the
events of Yontrack to a listener: it always sends the event, and does not look at the answer. The
`http` channel calls an API as a step of the delivery: you choose what is sent, and the notification
fails when the API does.

## Configuration as Code

The HTTP configuration a call needs is defined [as code](http-configuration.md#configuration-as-code)
like any other, under `ontrack.config.http`; the `channel` and `channelConfig` of a subscription are the
ones of the [example above](#configuration), wherever subscriptions are defined.

## See also

* [HTTP call channel reference](../../generated/notifications/notification-backend-http.md)
* [HTTP configurations](http-configuration.md)
