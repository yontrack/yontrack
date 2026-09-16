# HTTP security headers

The Yontrack UI sends a set of security headers on every response — the desktop UI, the
[mobile UI](../mobile/index.md) under `/mobile`, its `/api/*` routes and its static files alike.
The API keeps the default headers of Spring Security (`X-Content-Type-Options`,
`X-Frame-Options: DENY`, `Cache-Control: no-store`).

| Header                                | Value                                                                  |
|---------------------------------------|------------------------------------------------------------------------|
| `X-Content-Type-Options`              | `nosniff`                                                              |
| `Referrer-Policy`                     | `strict-origin-when-cross-origin`                                      |
| `Permissions-Policy`                  | denies the camera, the microphone, geolocation, payment, USB and the other browser features the UI does not use |
| `Content-Security-Policy`             | `frame-ancestors 'self'` — see [Embedding Yontrack pages](#embedding-yontrack-pages) |
| `X-Frame-Options`                     | `SAMEORIGIN`                                                           |
| `Content-Security-Policy-Report-Only` | a full content security policy, reported but not enforced — see below  |

## Content security policy

The complete policy is sent as **report-only**: a browser reports what the policy would block in
its developer console, and blocks nothing. It allows inline styles, which the UI's component
library injects at run time, and images from any HTTPS origin, which is where issue trackers serve
their icons.

If you see `[Report Only]` messages in the browser console while using Yontrack, they are worth
reporting as an issue: they are what decides whether the policy can be enforced in a later version.

## Embedding Yontrack pages

By default, only Yontrack itself may display its pages in a frame. To embed Yontrack pages in
another site — an internal portal, a wiki — set the `YONTRACK_UI_FRAME_ANCESTORS` environment
variable on the **UI** container to the list of origins allowed to frame them, in the
[`frame-ancestors`](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Content-Security-Policy/frame-ancestors)
source list syntax:

```yaml
YONTRACK_UI_FRAME_ANCESTORS: "'self' https://portal.example.com"
```

* the quotes around `'self'` and `'none'` are part of the value;
* `'none'` forbids framing altogether;
* a value holding a `;`, a `,` or a line break is refused and the default is kept — a warning is logged once.

The variable is read when a page is served, so changing it needs a restart of the UI container
but no new image. It applies to the pages; the UI's `/api/*` routes and static files keep
`'self'`, since framing them displays nothing.

`X-Frame-Options: SAMEORIGIN` is still sent: every current browser ignores it when
`frame-ancestors` is present, and the few that do not know `frame-ancestors` keep refusing
cross-origin framing.

!!! note

    An embedded page still needs the user to be signed in, and the identity provider's own
    pages usually refuse to be framed. Yontrack's session cookie is `SameSite=Lax`, so a browser
    sends it to a frame only when the embedding site is on the same site as Yontrack — for example
    `portal.example.com` embedding `yontrack.example.com`. A page framed by another domain is
    shown signed out.

## HSTS belongs to the ingress

Yontrack does **not** send `Strict-Transport-Security`, on purpose. HSTS tells browsers that a
host — and, with `includeSubDomains`, every host under it — must only ever be reached over HTTPS.
Only the component which terminates TLS in front of Yontrack knows whether that is true, so that
is where it belongs: set it on your ingress controller, load balancer or reverse proxy. For
example, with the NGINX ingress controller:

```yaml
nginx.ingress.kubernetes.io/configuration-snippet: |
  more_set_headers "Strict-Transport-Security: max-age=31536000";
```

The same goes for any header you want to add or tighten beyond the ones listed above.
