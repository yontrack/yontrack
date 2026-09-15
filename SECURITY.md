# Security policy

Yontrack is self-hosted by the people who use it, so a vulnerability in it is a vulnerability in
their infrastructure. Please report it privately, never in a public issue, discussion or pull request.

## Reporting a vulnerability

Use GitHub's private vulnerability reporting:

**[Report a vulnerability](https://github.com/yontrack/yontrack/security/advisories/new)**

The form is also available from the repository's *Security* tab. The report and the conversation
that follows stay visible to the maintainers only, until an advisory is published. There is no
security e-mail address: the form is the only channel.

## What to include

- **The Yontrack version** you are running, as shown at the bottom of the user menu or by the
  tag of the `yontrack/yontrack` image.
- **The deployment kind**: the Helm chart (and its version) or your own way of running the images,
  and the identity provider in use (the built-in Keycloak, your own OIDC provider, LDAP).
- **A reproduction**: the steps, requests or GraphQL queries that trigger the issue, and the
  permissions of the account used — anonymous, a given role, or an API token.
- **The impact** as you understand it: what an attacker can read, change or run.

A partial report is still welcome: send what you have rather than holding it back.

## Supported versions

Security fixes are made on the **latest release only**, and ship in the **next release**. They are
not backported to older release lines: to receive a fix, upgrade to the release that carries it.

| Version                | Security fixes |
|------------------------|----------------|
| Latest release         | Yes            |
| Any older release      | No             |

The releases are listed at <https://github.com/yontrack/yontrack/releases>.

## Automated scanning

The code on `main` and the images built from it are scanned continuously — dependency and image
scanning, static analysis, secret scanning and dynamic application security testing — and their
findings are triaged privately by the maintainers.
