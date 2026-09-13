# Management port

Besides its API on port `8080`, Yontrack serves the Spring Boot actuator endpoints on a
separate **management port**, `8800` by default, under the `/manage` base path.

!!! danger "Never expose the management port publicly"

    The management port has **no authentication**. It is meant to be reached by the probes and
    the monitoring of the platform Yontrack runs on — a Kubernetes liveness probe, a Prometheus
    scraper — and by nothing else. Do not route it through an ingress, a load balancer or a
    reverse proxy, and do not publish it on a public interface.

    The Docker Compose files shipped as starting points bind it to `127.0.0.1` only.

## What is exposed by default

| Endpoint              | Purpose                                                     |
|-----------------------|-------------------------------------------------------------|
| `/manage/health`      | Overall status (`UP` / `DOWN`), **without** any details     |
| `/manage/info`        | Application information                                     |
| `/manage/prometheus`  | Metrics in the Prometheus format                            |

Every other endpoint answers as if it did not exist.

Health only returns its overall status: the details of each component (database, Elasticsearch,
RabbitMQ, ...) are not shown to anonymous callers, and the management port has no other kind.

!!! note "Changed in 5.5"

    Earlier versions exposed **every** actuator endpoint (`env`, `configprops`, `beans`,
    `heapdump`, ...) and the full health details on the management port. If your tooling relied
    on one of them, re-enable it explicitly as shown below.

## Re-enabling an endpoint

The list of exposed endpoints is controlled by the `management.endpoints.web.exposure.include`
property — for example, with an environment variable:

```yaml
MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE: "health,info,prometheus,graphql"
```

The property **replaces** the default list, so repeat `health,info,prometheus` in it.

| Endpoint id    | What it serves                                             | Also needs                                         |
|----------------|------------------------------------------------------------|----------------------------------------------------|
| `graphql`      | The GraphQL schema, in SDL                                 |                                                    |
| `graphqlJson`  | The GraphQL schema, as an introspection result             |                                                    |
| `influxdb`     | Resets the InfluxDB connection (`POST`)                    | InfluxDB enabled                                   |
| `account`      | Creates an API token for any account — **testing only**    | `MANAGEMENT_ENDPOINT_ACCOUNT_ACCESS: unrestricted` |
| `env`, `beans`, `loggers`, ... | The [Spring Boot actuator endpoints](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html) | |

To show the health details again, set `management.endpoint.health.show-details` to `always`
(`MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS: always`).

!!! warning

    `account` hands out an administrator-grade token to whoever asks. Never enable it outside of
    a test environment.
