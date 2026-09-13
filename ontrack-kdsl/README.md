KDSL Connector
==============

Low level client to the Ontrack API and generated Apollo GraphQL calls.

## Generating the Apollo GraphQL client

Start Yontrack with `scripts/dev-stack.sh up` and run the [`ontrack.graphql.sh`](ontrack.graphql.sh) script -
it reads the management port of this checkout's stack from `.yontrack-dev/instance.env`.

Then generate the Apollo classes by running:

```
./gradlew :ontrack-kdsl:generateApolloSources
```
