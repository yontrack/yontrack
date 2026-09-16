# Cross-origin requests (CORS)

By default, the Yontrack API answers **no cross-origin call made from a browser**. A web page
served by another site can't read what `/graphql`, `/rest/...` or `/extension/...` return, even
when it holds a valid token.

This doesn't affect Yontrack itself. The Yontrack UI, [mobile UI](../mobile/index.md) included,
never calls the API from the browser. The browser talks to the UI, and the UI's server calls the
API. Clients that are not browsers aren't concerned either: the `yontrack` CLI, the GitHub
Actions, the MCP server, scripts and webhooks never go through CORS.

!!! note "Changed in 5.5"

    Earlier versions allowed any origin (`Access-Control-Allow-Origin: *`) on the API and on the
    hooks. If a page on another site called the Yontrack API from a browser, list that site's
    origin as described below when you upgrade.

## Allowing an origin

When a page served by another site must call the API from the browser (an internal portal, say),
list its origin in the `ontrack.config.security.cors.allowed-origins` property of the Yontrack
**API** container:

```yaml
ontrack:
  config:
    security:
      cors:
        allowed-origins:
          - https://portal.example.com
```

or, as environment variables:

```yaml
ONTRACK_CONFIG_SECURITY_CORS_ALLOWED_ORIGINS: "https://portal.example.com,https://wiki.example.com"
```

* an origin is a scheme, a host and a port if it's not the default one, with no path and no
  trailing slash: `https://portal.example.com`, `http://localhost:4000`;
* the listed origins may call `/graphql`, `/rest/**` and `/extension/**` with the `GET`, `POST`,
  `PUT`, `DELETE` and `HEAD` methods and any header, and may read the answers;
* every origin that isn't listed is refused;
* `*` isn't a sensible value: it lets every site in again.

The hooks (`/hook/secured/...`) never accept cross-origin calls, whatever the list says. They
are called by other servers, not by browsers.

The property is described with the other
[general configuration properties](../generated/configurations/net.nemerosa.ontrack.model.support.OntrackConfigProperties.md) as well.
