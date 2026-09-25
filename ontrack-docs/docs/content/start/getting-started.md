# Getting started

The recommended way to install Yontrack is to use its Helm chart.

> Please refer to the [Helm chart documentation](https://github.com/yontrack/yontrack-chart) for more information.

## Quick start

The Yontrack Helm chart is available as an OCI Helm chart in Docker Hub.

```
helm install yontrack oci://registry-1.docker.io/yontrack/yontrack-chart
```

This installs the following services:

* Yontrack itself (backend & frontend)
* a Postgres 17 database
* a RabbitMQ message broker

Yontrack 6 needs no Elasticsearch: search runs in Postgres, with its `pg_trgm` extension (see
[Search index](../operations/search-index.md)).

The default authentication mechanism, if no other configuration is provided, relies on Keycloak and its own database, and two additional services are installed:

* a Keycloak instance configured for storing users
* a Postgres 17 database for Keycloak

## Authentication setup

By default, Yontrack is secured using a simple user store managed by Keycloak. If nothing is configured, the default user is `admin` with `admin` as a password.

While you may keep using this local Keycloak as an identity provider, in most cases, you'll configure Yontrack to use your own identity provider:

* [OIDC](../security/oidc.md)
* [LDAP](../security/ldap.md)

Once authentication has been setup, you can start configuring [groups](../security/groups.md) and map them to the [groups of your identity provider](../security/group-mappings.md).

## TLS and HSTS

Terminate TLS in front of Yontrack — at your ingress controller, load balancer or reverse proxy —
and set `Strict-Transport-Security` there. Yontrack deliberately does not send HSTS itself: see
[HTTP security headers](../security/http-headers.md), which also lists the headers Yontrack does
send and how to allow other sites to embed its pages.

## Security updates

Security fixes ship in the next Yontrack release and are not backported to older ones: keep your
installation on the latest release to receive them.

To report a vulnerability, do not open a public issue — follow the
[security policy](https://github.com/yontrack/yontrack/security/policy), which uses GitHub's
private vulnerability reporting.

## Configuration

Where to go next? Start [configuring](configuration.md) your Yontrack instance.
