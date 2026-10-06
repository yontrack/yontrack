# OIDC authentication

!!! note

    See the [Helm chart documentation](https://github.com/yontrack/yontrack-chart) for a list of all options.

While some options can differ from provider to provider, the main options are set through Helm chart values for your Yontrack installation.

Typically, you'll use an external secret to store the OIDC secrets.

```yaml
auth:
  kind: oidc
  oidc:
    name: <display name for the provider>
    issuer: https://****
    credentials:
      secret:
        enabled: true
        secretName: <secret name>
```

The secret name must have the following keys:

* `clientId`
* `clientSecret`

## Sign-out

Signing out of Yontrack — from the user menu or from the [mobile UI](../mobile/index.md) — also
ends the session at the identity provider, so that signing back in asks who is there instead of
being answered silently. With the generic OIDC provider of the UI (`NEXTAUTH_PROVIDER=oidc`, as
set up on this page), Yontrack sends the browser to the provider's
`end_session_endpoint`, which sends it back to Yontrack.

**This needs one URL registered at the provider:**

```
https://<your-yontrack>/api/auth/signout-complete
```

| Provider           | Where to register it                                                                   |
|--------------------|----------------------------------------------------------------------------------------|
| Keycloak           | the client's *Valid post logout redirect URIs*                                         |
| Auth0              | the application's *Allowed Logout URLs*                                                |
| Microsoft Entra ID | the application's *Redirect URIs* — the same Web platform list as the callback URI     |

Until it is registered, signing out leaves the user on the provider's own page — an error page on
Keycloak and Auth0 — instead of coming back to Yontrack. They are signed out of Yontrack either
way.

!!! note "Keycloak"

    `+` in *Valid post logout redirect URIs* does not cover it: it reuses the *Valid redirect
    URIs*, which are the callback URI, `…/api/auth/callback/oidc`.

    A Yontrack whose UI talks to Keycloak through its Keycloak provider — its own Keycloak, the
    default, or one of yours, with `NEXTAUTH_PROVIDER` not set to `oidc` — ends the Keycloak
    session from the server, and needs no change at all.

### Turning it off

Set the `NEXTAUTH_FEDERATED_SIGNOUT` environment variable of the UI to `false` to sign out of
Yontrack only, and leave the provider's session alone — for example when the provider's session is
shared with other applications, and signing out of Yontrack should not sign the user out of them.

While the variable is not set, the UI logs at startup that federated sign-out is on, and which URL
to register.

### Limits

Whatever fails at the provider — the provider cannot be reached, or does not advertise an
`end_session_endpoint` — Yontrack still signs the user out locally, and logs a warning naming the
cause.

* **Auth0** — the *RP-Initiated Logout End Session Endpoint Discovery* setting of the tenant must be
  on. It is on by default for the tenants created since 14 November 2023, and off for the older
  ones, which then sign out of Yontrack only. Signing out ends the Auth0 session, not the session
  of an upstream provider Auth0 federates (Google, an enterprise connection).
* **Microsoft Entra ID** — signing out ends the Entra session of the browser, and so signs the
  user out of the other Microsoft applications open in it (Outlook, Teams). Entra still shows its
  account picker on the way. On an Entra-joined device, the device's own credential (its Primary
  Refresh Token) can sign the user back in without a password: the Entra session is ended, but a
  password is not necessarily asked for.
