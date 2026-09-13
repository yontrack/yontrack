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
* an Elasticsearch 9 single node
* a RabbitMQ message broker

The default authentication mechanism, if no other configuration is provided, relies on Keycloak and its own database, and two additional services are installed:

* a Keycloak instance configured for storing users
* a Postgres 17 database for Keycloak

## Authentication setup

By default, Yontrack is secured using a simple user store managed by Keycloak. If nothing is configured, the default user is `admin` with `admin` as a password.

While you may keep using this local Keycloak as an identity provider, in most cases, you'll configure Yontrack to use your own identity provider:

* [OIDC](../security/oidc.md)
* [LDAP](../security/ldap.md)

Once authentication has been setup, you can start configuring [groups](../security/groups.md) and map them to the [groups of your identity provider](../security/group-mappings.md).

## Configuration

Where to go next? Start [configuring](configuration.md) your Yontrack instance.
