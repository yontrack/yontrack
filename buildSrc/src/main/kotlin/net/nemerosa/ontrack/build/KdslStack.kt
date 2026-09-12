package net.nemerosa.ontrack.build

import java.io.File

/**
 * Port allocation for the KDSL acceptance Compose stacks
 * (`compose/docker-compose-kdsl.yml` and its `-ldap` and `-oidc` variants),
 * on top of [StackSlots].
 *
 * The three stacks share one slot. They are sequenced so that they are never
 * up at the same time -- `kdslLdapComposeUp` waits for
 * `kdslAcceptanceTestComposeDown`, and `kdslOidcComposeUp` for
 * `kdslLdapComposeDown` -- so one set of ports serves all three, and a
 * checkout has one set of acceptance ports to remember rather than three.
 *
 * See `docs/adr/0013-parallel-kdsl-acceptance-stacks.md`.
 */
object KdslStack {

    /**
     * Four slots, which is fewer than the ten the integration test stack
     * gets, for two reasons that point the same way.
     *
     * The arithmetic: with a `slot * 100` offset, Yontrack's management port
     * spans 8800-9100 over four slots and Elasticsearch starts at 9200. A
     * fifth slot would put the management port of one checkout on the
     * Elasticsearch port of another, and the base ports here are the ones the
     * tests and `ACCProperties` default to, so they are not free to move.
     *
     * The machine: an acceptance stack is a full Yontrack with a 2 GB heap,
     * the Next.js UI, Postgres, Elasticsearch, RabbitMQ, Keycloak and
     * InfluxDB. Four of those at once is already past what a developer
     * machine will carry, so the arithmetic is not the binding constraint.
     */
    const val SLOT_MAX = 3

    const val BASE_UI = 3000
    const val BASE_LDAP = 389
    const val BASE_LDAPS = 636
    const val BASE_POSTGRES = 5432
    const val BASE_RABBIT = 5672
    const val BASE_RABBIT_MGMT = 15672
    const val BASE_KEYCLOAK = 8008
    const val BASE_ONTRACK = 8080
    const val BASE_INFLUXDB = 8086
    const val BASE_ONTRACK_MGMT = 8800
    const val BASE_ELASTIC = 9200

    val BASE_PORTS = listOf(
        BASE_UI,
        BASE_LDAP,
        BASE_LDAPS,
        BASE_POSTGRES,
        BASE_RABBIT,
        BASE_RABBIT_MGMT,
        BASE_KEYCLOAK,
        BASE_ONTRACK,
        BASE_INFLUXDB,
        BASE_ONTRACK_MGMT,
        BASE_ELASTIC,
    )

    const val INSTANCE_ENV_PATH = ".yontrack-kdsl/instance.env"
    const val SLOT_KEY = "KDSL_SLOT"

    /** Resolves the whole instance for a checkout. */
    fun resolve(rootDir: File, instanceEnv: File = File(rootDir, INSTANCE_ENV_PATH)): KdslStackInstance {
        val path = rootDir.absolutePath
        val slot = StackSlots.resolveSlot(
            path = path,
            mainCheckout = StackSlots.isMainCheckout(rootDir),
            recordedSlot = StackSlots.readRecordedSlot(instanceEnv, SLOT_KEY),
            slotMax = SLOT_MAX,
            basePorts = BASE_PORTS,
            stackName = "KDSL acceptance",
            portFree = StackSlots::portFree,
        )
        return KdslStackInstance(slug = StackSlots.slug(path), slot = slot)
    }
}

/**
 * The resolved KDSL acceptance stack for one checkout: the Compose project
 * name of each of the three variants, and the host ports they publish.
 */
data class KdslStackInstance(
    val slug: String,
    val slot: Int,
) {

    val projectName: String = "yontrack-kdsl-$slug"
    val ldapProjectName: String = "yontrack-kdsl-ldap-$slug"
    val oidcProjectName: String = "yontrack-kdsl-oidc-$slug"

    val uiPort: Int = StackSlots.port(KdslStack.BASE_UI, slot)
    val ldapPort: Int = StackSlots.port(KdslStack.BASE_LDAP, slot)
    val ldapsPort: Int = StackSlots.port(KdslStack.BASE_LDAPS, slot)
    val postgresPort: Int = StackSlots.port(KdslStack.BASE_POSTGRES, slot)
    val rabbitPort: Int = StackSlots.port(KdslStack.BASE_RABBIT, slot)
    val rabbitManagementPort: Int = StackSlots.port(KdslStack.BASE_RABBIT_MGMT, slot)
    val keycloakPort: Int = StackSlots.port(KdslStack.BASE_KEYCLOAK, slot)
    val ontrackPort: Int = StackSlots.port(KdslStack.BASE_ONTRACK, slot)
    val influxdbPort: Int = StackSlots.port(KdslStack.BASE_INFLUXDB, slot)
    val ontrackManagementPort: Int = StackSlots.port(KdslStack.BASE_ONTRACK_MGMT, slot)
    val elasticPort: Int = StackSlots.port(KdslStack.BASE_ELASTIC, slot)

    val ontrackUrl: String = "http://localhost:$ontrackPort"
    val ontrackManagementUrl: String = "http://localhost:$ontrackManagementPort/manage"
    val influxdbUrl: String = "http://localhost:$influxdbPort"
    val uiUrl: String = "http://localhost:$uiPort"
    val keycloakUrl: String = "http://localhost:$keycloakPort"

    /** Passed to `docker compose`, and read by the three `docker-compose-kdsl*.yml` files. */
    val composeEnvironment: Map<String, String> = mapOf(
        "YONTRACK_KDSL_UI_PORT" to uiPort.toString(),
        "YONTRACK_KDSL_LDAP_PORT" to ldapPort.toString(),
        "YONTRACK_KDSL_LDAPS_PORT" to ldapsPort.toString(),
        "YONTRACK_KDSL_POSTGRES_PORT" to postgresPort.toString(),
        "YONTRACK_KDSL_RABBIT_PORT" to rabbitPort.toString(),
        "YONTRACK_KDSL_RABBIT_MGMT_PORT" to rabbitManagementPort.toString(),
        "YONTRACK_KDSL_KEYCLOAK_PORT" to keycloakPort.toString(),
        "YONTRACK_KDSL_ONTRACK_PORT" to ontrackPort.toString(),
        "YONTRACK_KDSL_INFLUXDB_PORT" to influxdbPort.toString(),
        "YONTRACK_KDSL_ONTRACK_MGMT_PORT" to ontrackManagementPort.toString(),
        "YONTRACK_KDSL_ELASTIC_PORT" to elasticPort.toString(),
    )

    /**
     * The `ACCProperties` knobs that point the acceptance tests at *this*
     * instance rather than at the historical ports.
     *
     * `connection.internal.url` is deliberately absent: it is the URL
     * Yontrack uses to call *itself* from inside its own container, where the
     * host port has no meaning and 8080 is always right.
     */
    val systemProperties: Map<String, String> = mapOf(
        "ontrack.acceptance.connection.url" to ontrackUrl,
        "ontrack.acceptance.connection.mgt.url" to ontrackManagementUrl,
        "ontrack.acceptance.influxdb.url" to influxdbUrl,
    )

    fun writeInstanceEnv(file: File) {
        StackSlots.writeInstanceEnv(
            file = file,
            entries = mapOf(
                "KDSL_SLUG" to slug,
                KdslStack.SLOT_KEY to slot.toString(),
                "KDSL_PROJECT" to projectName,
                "KDSL_LDAP_PROJECT" to ldapProjectName,
                "KDSL_OIDC_PROJECT" to oidcProjectName,
                "YONTRACK_KDSL_UI_URL" to uiUrl,
                "YONTRACK_KDSL_ONTRACK_URL" to ontrackUrl,
                "YONTRACK_KDSL_KEYCLOAK_URL" to keycloakUrl,
            ) + composeEnvironment,
            systemProperties = systemProperties,
        )
    }

    fun describe(): String =
        "slot $slot (project $projectName): yontrack $ontrackPort, management " +
                "$ontrackManagementPort, ui $uiPort, keycloak $keycloakPort, influxdb $influxdbPort"
}
