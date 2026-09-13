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
