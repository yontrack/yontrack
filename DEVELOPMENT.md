# Development quick start

To start developing with Yontrack, follow these instructions.

## Prerequisites

You need:

* JDK 21
* Docker (Desktop)

## Getting the code

Get a clean working copy of the Yntrack GitHub repository:

```bash
git clone git@github.com:yontrack/yontrack.git yontrack
cd yontrack
```

## Prepares the environment

Start the middleware needed by Yontrack:

```bash
./gradlew devComposeUp
```

This starts:

* a Postgres database
* an ElasticSearch single-node instance
* a RabbitMQ server
* a Postgres database for Keycloak
* a Keycloak server
* a Kibana server

> To tear down the environment, you can run `./gradlew devComposeDown`.

## Running Yontrack

### Backend

In Intellij IDEA, you can use the provided `Application (kdsl)` [run configuration](.run/Application%20(kdsl).run.xml)
run configuration. It runs Yontrack in `dev` mode with a few environment variables set to ease the development.

> The backend is available on http://localhost:8080 but should not be used directly. The Spring Boot actuator is running
> at http://localhost:8800/manage.

### Frontend

In Intellij IDEA, you can use the provided (NPM) `dev` [run configuration](.run/dev.run.xml) run configuration.

> This launches `npm run dev` in the `ontrack-web-core` directory.

This launches Yontrack on http://localhost:3000.
