# HTTP configurations

An _HTTP configuration_ is a named **base URL** together with the **authentication** used to call
it. Every HTTP call that Yontrack makes on behalf of a notification goes through one: the calls stay
under its base URL, and they carry its authentication. The [HTTP call](http.md) channel makes its calls this way.

The HTTP configurations are managed by administrators, and their list _is_ the list of the hosts
Yontrack may call: there is no other allowlist. An anonymous API still needs a configuration, with
the `none` authentication.

## Managing the configurations

In the user menu, go to _Configurations_ > _HTTP configurations_. You need the right to manage
the global settings.

Each configuration has:

* a **name**, which identifies it;
* a **base URL**, an absolute `http` or `https` URL. It may have a path (`https://api.example.com/v1`)
  but neither a query nor a fragment, and it must not contain any credentials: those belong to the
  authentication;
* an **authentication**, of one of the types below.

The secrets of the authentication (password, token, header value) are stored encrypted and are never
displayed again. When editing a configuration, leave a secret blank to keep the existing one.

## Authentication types

| Type     | Configuration                                           | Sent with each request                     |
|----------|---------------------------------------------------------|--------------------------------------------|
| `none`   | _none_                                                  | nothing                                    |
| `basic`  | `username`, `password`                                  | `Authorization: Basic <base64>`            |
| `bearer` | `token`                                                 | `Authorization: Bearer <token>`            |
| `header` | `name`, `value`                                         | `<name>: <value>`                          |

These are the same types as the ones of the webhooks, `none` apart.

## Testing a configuration

The :octicons-question-16: button of a configuration, or the _Test_ button of its dialog, sends a
`GET` on the base URL with the authentication. The test fails when:

* the call cannot be made (unknown host, connection refused, timeout, TLS error);
* the API refuses the authentication (`401` or `403`);
* the API fails (any `5xx`);
* the API redirects the call (any `3xx`): redirects are never followed, as they could carry the
  credentials to another host.

Any other answer is a success, a `404` included: the root of an API often has nothing to show.

As for every configuration, this test also runs when a configuration is saved, unless the
configuration tests are disabled (`ontrack.config.configuration-test`).

## Configuration as Code

The HTTP configurations are defined under `ontrack.config.http`. The secrets are given through
[CasC secrets](../../configuration/casc.md#using-secrets):

{% raw %}
```yaml
ontrack:
  config:
    http:
      - name: deployments
        url: https://deploy.example.com/api
        authentication:
          type: bearer
          config:
            token: {{ secret.deployments.token }}
      - name: inventory
        url: https://inventory.example.com
        authentication:
          type: header
          config:
            name: X-Api-Key
            value: {{ secret.inventory.key }}
      - name: status-page
        url: https://status.example.com/api/v2
        # No authentication: same as `type: none`
```
{% endraw %}

The list is the complete list of the HTTP configurations: a configuration which is not in it is
deleted. When the CasC is rendered, the secrets are left blank.

## HTTP notifications settings

Global limits apply to every HTTP call. As an administrator, open the user menu, choose _System_ >
_Settings_, then select _HTTP notifications_:

| Setting                 | Default | Meaning                                                                       |
|-------------------------|---------|-------------------------------------------------------------------------------|
| `maxTimeoutSeconds`     | 3600    | Maximum timeout, in seconds, which a subscription may set on an HTTP call     |
| `maxResponseBytes`      | 65536   | Maximum size, in bytes, of a response body read: a longer body is truncated   |
| `requestTimeoutSeconds` | 30      | Timeout, in seconds, of a single request: both its connection and its reading |

All of them must be strictly positive. There is no switch to turn the HTTP calls off: without any
HTTP configuration, no call can be made.

In Configuration as Code, they are under `ontrack.config.settings.http-notifications`:

```yaml
ontrack:
  config:
    settings:
      http-notifications:
        maxTimeoutSeconds: 3600
        maxResponseBytes: 65536
        requestTimeoutSeconds: 30
```
