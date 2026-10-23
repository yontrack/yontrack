package net.nemerosa.ontrack.build

import java.io.File

/**
 * Port allocation for the KDSL acceptance Compose stacks
 * (`compose/docker-compose-kdsl.yml` and its `-ldap`, `-oidc` and `-ha`
 * variants), on top of [StackSlots].
 *
 * The four stacks share one slot. They are sequenced so that they are never
 * up at the same time -- `kdslLdapComposeUp` waits for
 * `kdslAcceptanceTestComposeDown`, `kdslOidcComposeUp` for
 * `kdslLdapComposeDown`, and `kdslHaComposeUp` for `kdslOidcComposeDown` --
 * so one set of ports serves all four, and a checkout has one set of
 * acceptance ports to remember rather than four.
 *
 * See `docs/adr/0013-parallel-kdsl-acceptance-stacks.md`.
 */
object KdslStack {

    /**
     * Four slots, which is fewer than the ten the integration test stack
     * gets.
     *
     * The machine: an acceptance stack is a full Yontrack with a 2 GB heap,
     * the Next.js UI, Postgres, RabbitMQ, Keycloak and InfluxDB. Four of those
     * at once is already past what a developer machine will carry.
     *
     * The arithmetic used to point the same way: Yontrack's management port
     * spans 8800-9100 over four slots, and Elasticsearch started at 9200.
     * Elasticsearch left the acceptance stacks with #1883 (ADR 0017), so that
     * bound is gone, but the Keycloak realms list the UI callback of every slot
     * literally (`KeycloakRealmsTest`), and the machine alone keeps it at four.
     */
    const val SLOT_MAX = 3

    const val BASE_UI = 3000
    const val BASE_LDAP = 389
    const val BASE_LDAPS = 636
    const val BASE_POSTGRES = 5432
    const val BASE_RABBIT = 5672
    const val BASE_RABBIT_MGMT = 15672

    /**
     * The JaCoCo agent's `tcpserver` port, published by the coverage-only
     * Compose override (#1819). It is here, and not hardcoded in the override,
     * for the same reason as every other port: two checkouts running the
     * acceptance tests at once must not fight over it.
     *
     * 6300 is JaCoCo's own default. Over four slots it spans 6300-6600, which
     * sits in the gap between RabbitMQ's 5672-5972 and Keycloak's 8008-8308.
     */
    const val BASE_JACOCO = 6300
    const val BASE_KEYCLOAK = 8008
    const val BASE_ONTRACK = 8080
    const val BASE_INFLUXDB = 8086
    const val BASE_ONTRACK_MGMT = 8800

    /**
     * MinIO's S3 API (#1962), above every other port of the stack. Only the main variant runs a
     * MinIO -- the `-ldap` and `-oidc` ones test authentication -- but they share the slot, so the
     * port is probed for all three.
     */
    const val BASE_MINIO = Minio.BASE_PORT

    /**
     * The two backends of the `-ha` variant (#2076), each reached directly, next to the nginx
     * proxy in front of them, which takes [BASE_ONTRACK]. Only that variant publishes them, but
     * the four share the slot, so they are probed for all four.
     *
     * A and B are one apart and the slots a hundred, so the two never meet. Over four slots the
     * management ports span 18880-19181, interleaved with MinIO's 19000-19300 without touching it:
     * `no two base ports collide over the slot range` keeps it that way.
     */
    const val BASE_HA_A = 18080
    const val BASE_HA_B = 18081
    const val BASE_HA_A_MGMT = 18880
    const val BASE_HA_B_MGMT = 18881

    val BASE_PORTS = listOf(
        BASE_UI,
        BASE_LDAP,
        BASE_LDAPS,
        BASE_POSTGRES,
        BASE_RABBIT,
        BASE_RABBIT_MGMT,
        BASE_JACOCO,
        BASE_KEYCLOAK,
        BASE_ONTRACK,
        BASE_INFLUXDB,
        BASE_ONTRACK_MGMT,
        BASE_MINIO,
        BASE_HA_A,
        BASE_HA_B,
        BASE_HA_A_MGMT,
        BASE_HA_B_MGMT,
    )

    const val INSTANCE_ENV_PATH = ".yontrack-kdsl/instance.env"
    const val SLOT_KEY = "KDSL_SLOT"

    /**
     * The Compose project names of a checkout's three acceptance stacks.
     *
     * They come from the checkout path alone: naming a project claims no slot
     * and probes no port. That is what lets the Compose extension be
     * configured without [resolve] running -- see its note.
     */
    fun names(rootDir: File): KdslStackNames = KdslStackNames(StackSlots.slug(rootDir.absolutePath))

    /**
     * Resolves the whole instance for a checkout.
     *
     * This *claims a slot*: unless one is already recorded and still this checkout's - see
     * [StackSlots.resolveSlot] - it probes every
     * port of every slot, and fails the build when none of them is free. Call
     * it from a task that is running, never from the configuration of a build
     * file -- a build that is not going to touch the acceptance stack must
     * neither pay for the probe nor die on it.
     */
    fun resolve(rootDir: File, instanceEnv: File = File(rootDir, INSTANCE_ENV_PATH)): KdslStackInstance {
        val path = rootDir.absolutePath
        val slot = StackSlots.resolveSlot(
            path = path,
            mainCheckout = StackSlots.isMainCheckout(rootDir),
            recordedSlot = StackSlots.readRecordedSlot(instanceEnv, SLOT_KEY),
            slotMax = SLOT_MAX,
            basePorts = BASE_PORTS,
            stackName = "KDSL acceptance",
            stackUp = { StackSlots.composeProjectsUp(names(rootDir).let { listOf(it.projectName, it.ldapProjectName, it.oidcProjectName, it.haProjectName) }) },
            portFree = StackSlots::portFree,
            log = ::println,
        )
        return KdslStackInstance(slug = StackSlots.slug(path), slot = slot)
    }
}

/**
 * The Compose project name of each of the four acceptance variants.
 *
 * Split out of [KdslStackInstance] because it is the half that needs no slot:
 * a project is named after the checkout, not after the ports it publishes.
 * See [KdslStack.names].
 */
data class KdslStackNames(val slug: String) {

    val projectName: String = "yontrack-kdsl-$slug"
    val ldapProjectName: String = "yontrack-kdsl-ldap-$slug"
    val oidcProjectName: String = "yontrack-kdsl-oidc-$slug"
    val haProjectName: String = "yontrack-kdsl-ha-$slug"
}

/**
 * The resolved KDSL acceptance stack for one checkout: the Compose project
 * name of each of the four variants, and the host ports they publish.
 */
data class KdslStackInstance(
    val slug: String,
    val slot: Int,
) {

    private val names = KdslStackNames(slug)

    val projectName: String get() = names.projectName
    val ldapProjectName: String get() = names.ldapProjectName
    val oidcProjectName: String get() = names.oidcProjectName
    val haProjectName: String get() = names.haProjectName

    val uiPort: Int = StackSlots.port(KdslStack.BASE_UI, slot)
    val ldapPort: Int = StackSlots.port(KdslStack.BASE_LDAP, slot)
    val ldapsPort: Int = StackSlots.port(KdslStack.BASE_LDAPS, slot)
    val postgresPort: Int = StackSlots.port(KdslStack.BASE_POSTGRES, slot)
    val rabbitPort: Int = StackSlots.port(KdslStack.BASE_RABBIT, slot)
    val rabbitManagementPort: Int = StackSlots.port(KdslStack.BASE_RABBIT_MGMT, slot)
    val jacocoPort: Int = StackSlots.port(KdslStack.BASE_JACOCO, slot)
    val keycloakPort: Int = StackSlots.port(KdslStack.BASE_KEYCLOAK, slot)
    val ontrackPort: Int = StackSlots.port(KdslStack.BASE_ONTRACK, slot)
    val influxdbPort: Int = StackSlots.port(KdslStack.BASE_INFLUXDB, slot)
    val ontrackManagementPort: Int = StackSlots.port(KdslStack.BASE_ONTRACK_MGMT, slot)
    val minioPort: Int = StackSlots.port(KdslStack.BASE_MINIO, slot)
    val haAPort: Int = StackSlots.port(KdslStack.BASE_HA_A, slot)
    val haBPort: Int = StackSlots.port(KdslStack.BASE_HA_B, slot)
    val haAManagementPort: Int = StackSlots.port(KdslStack.BASE_HA_A_MGMT, slot)
    val haBManagementPort: Int = StackSlots.port(KdslStack.BASE_HA_B_MGMT, slot)

    val ontrackUrl: String = "http://localhost:$ontrackPort"
    val ontrackManagementUrl: String = "http://localhost:$ontrackManagementPort/manage"
    val influxdbUrl: String = "http://localhost:$influxdbPort"
    val uiUrl: String = "http://localhost:$uiPort"
    val keycloakUrl: String = "http://localhost:$keycloakPort"

    /**
     * MinIO as seen from the host. Yontrack, in its container, reaches it as `http://minio:9000`,
     * which `compose/docker-compose-kdsl.yml` configures itself.
     */
    val minioUrl: String = "http://localhost:$minioPort"

    /** Backend A of the `-ha` variant, bypassing the proxy, which is on [ontrackUrl]. */
    val haAUrl: String = "http://localhost:$haAPort"
    val haAManagementUrl: String = "http://localhost:$haAManagementPort/manage"

    /** Backend B of the `-ha` variant, bypassing the proxy, which is on [ontrackUrl]. */
    val haBUrl: String = "http://localhost:$haBPort"
    val haBManagementUrl: String = "http://localhost:$haBManagementPort/manage"

    /** Passed to `docker compose`, and read by the four `docker-compose-kdsl*.yml` files. */
    val composeEnvironment: Map<String, String> = mapOf(
        "YONTRACK_KDSL_UI_PORT" to uiPort.toString(),
        "YONTRACK_KDSL_LDAP_PORT" to ldapPort.toString(),
        "YONTRACK_KDSL_LDAPS_PORT" to ldapsPort.toString(),
        "YONTRACK_KDSL_POSTGRES_PORT" to postgresPort.toString(),
        "YONTRACK_KDSL_RABBIT_PORT" to rabbitPort.toString(),
        "YONTRACK_KDSL_RABBIT_MGMT_PORT" to rabbitManagementPort.toString(),
        "YONTRACK_KDSL_JACOCO_PORT" to jacocoPort.toString(),
        "YONTRACK_KDSL_KEYCLOAK_PORT" to keycloakPort.toString(),
        "YONTRACK_KDSL_ONTRACK_PORT" to ontrackPort.toString(),
        "YONTRACK_KDSL_INFLUXDB_PORT" to influxdbPort.toString(),
        "YONTRACK_KDSL_ONTRACK_MGMT_PORT" to ontrackManagementPort.toString(),
        "YONTRACK_KDSL_MINIO_PORT" to minioPort.toString(),
        "YONTRACK_KDSL_HA_A_PORT" to haAPort.toString(),
        "YONTRACK_KDSL_HA_B_PORT" to haBPort.toString(),
        "YONTRACK_KDSL_HA_A_MGMT_PORT" to haAManagementPort.toString(),
        "YONTRACK_KDSL_HA_B_MGMT_PORT" to haBManagementPort.toString(),
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

    /**
     * The `ACCProperties` knobs of `kdslHaTest`, which runs the `ha` package against the `-ha`
     * variant (#2076): `ontrack` through the proxy, `ontrackA` and `ontrackB` straight to each
     * backend.
     *
     * The proxy publishes no management port, so the token -- which lives in the database and
     * works on both backends -- is obtained from A's.
     */
    val haSystemProperties: Map<String, String>
        get() = mapOf(
            "ontrack.acceptance.connection.url" to ontrackUrl,
            "ontrack.acceptance.connection.mgt.url" to haAManagementUrl,
        ) + haBackendSystemProperties

    /**
     * The properties only the `ha` package reads: each backend, bypassing the proxy, and the Compose
     * project of the stack, whose containers a test kills and starts again (#2079).
     */
    private val haBackendSystemProperties: Map<String, String> = mapOf(
        "ontrack.acceptance.ha.a.url" to haAUrl,
        "ontrack.acceptance.ha.a.mgt.url" to haAManagementUrl,
        "ontrack.acceptance.ha.b.url" to haBUrl,
        "ontrack.acceptance.ha.b.mgt.url" to haBManagementUrl,
        "ontrack.acceptance.ha.project" to haProjectName,
    )

    /**
     * The variables `ontrack-web-tests/ontrack/connection.js` reads, pointing
     * the Playwright suite at *this* instance. Its defaults are the slot-0
     * ports, so without them a linked worktree gets its token from -- and
     * runs against -- another checkout's stack, or none at all (#1847).
     *
     * `ONTRACK_MGT_URL` is the management *root*: the fixture appends
     * `/manage` itself, so it is not [ontrackManagementUrl].
     */
    val playwrightEnvironment: Map<String, String> = mapOf(
        "ONTRACK_MGT_URL" to "http://localhost:$ontrackManagementPort",
        "ONTRACK_UI_URL" to uiUrl,
        "ONTRACK_BACKEND_URL" to ontrackUrl,
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
                "KDSL_HA_PROJECT" to haProjectName,
                "YONTRACK_KDSL_UI_URL" to uiUrl,
                "YONTRACK_KDSL_ONTRACK_URL" to ontrackUrl,
                "YONTRACK_KDSL_KEYCLOAK_URL" to keycloakUrl,
                "YONTRACK_KDSL_MINIO_URL" to minioUrl,
                "YONTRACK_KDSL_HA_A_URL" to haAUrl,
                "YONTRACK_KDSL_HA_B_URL" to haBUrl,
            ) + composeEnvironment,
            systemProperties = systemProperties + haBackendSystemProperties,
        )
    }

    fun describe(): String =
        "slot $slot (project $projectName): yontrack $ontrackPort, management " +
                "$ontrackManagementPort, ui $uiPort, keycloak $keycloakPort, influxdb $influxdbPort, minio $minioPort"
}
