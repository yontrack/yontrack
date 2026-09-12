package net.nemerosa.ontrack.build

import java.io.File

/**
 * Port allocation for the integration test Compose stack
 * (`compose/docker-compose-it.yml`), on top of [StackSlots].
 *
 * See `docs/adr/0012-parallel-integration-test-stacks.md`.
 */
object ItStack {

    /**
     * Ten slots. The base ports below are chosen so that no two of them ever
     * want the same host port across those ten slots: two services may share
     * a base residue modulo 100 only if their ten-slot ranges do not overlap,
     * which here means Vault (8200-9100) staying clear of Elasticsearch
     * (9200-10100). `StackSlotsTest` holds the build to it.
     */
    const val SLOT_MAX = 9

    const val BASE_POSTGRES = 5432
    const val BASE_ELASTIC = 9200
    const val BASE_RABBIT = 5672
    const val BASE_RABBIT_MGMT = 15672
    const val BASE_VAULT = 8200

    val BASE_PORTS = listOf(
        BASE_POSTGRES,
        BASE_ELASTIC,
        BASE_RABBIT,
        BASE_RABBIT_MGMT,
        BASE_VAULT,
    )

    const val INSTANCE_ENV_PATH = ".yontrack-it/instance.env"
    const val SLOT_KEY = "IT_SLOT"

    /** Resolves the whole instance for a checkout. */
    fun resolve(rootDir: File, instanceEnv: File = File(rootDir, INSTANCE_ENV_PATH)): ItStackInstance {
        val path = rootDir.absolutePath
        val slot = StackSlots.resolveSlot(
            path = path,
            mainCheckout = StackSlots.isMainCheckout(rootDir),
            recordedSlot = StackSlots.readRecordedSlot(instanceEnv, SLOT_KEY),
            slotMax = SLOT_MAX,
            basePorts = BASE_PORTS,
            stackName = "integration test",
            portFree = StackSlots::portFree,
        )
        return ItStackInstance(slug = StackSlots.slug(path), slot = slot)
    }
}

/**
 * The resolved integration test stack for one checkout: its Compose project
 * name and the host ports its middleware publishes.
 */
data class ItStackInstance(
    val slug: String,
    val slot: Int,
) {

    val projectName: String = "yontrack-it-$slug"

    val postgresPort: Int = StackSlots.port(ItStack.BASE_POSTGRES, slot)
    val elasticPort: Int = StackSlots.port(ItStack.BASE_ELASTIC, slot)
    val rabbitPort: Int = StackSlots.port(ItStack.BASE_RABBIT, slot)
    val rabbitManagementPort: Int = StackSlots.port(ItStack.BASE_RABBIT_MGMT, slot)
    val vaultPort: Int = StackSlots.port(ItStack.BASE_VAULT, slot)

    val jdbcUrl: String = "jdbc:postgresql://localhost:$postgresPort/ontrack"
    val elasticUri: String = "http://localhost:$elasticPort"
    val vaultUri: String = "http://localhost:$vaultPort"

    /** Passed to `docker compose`, and read by `compose/docker-compose-it.yml`. */
    val composeEnvironment: Map<String, String> = mapOf(
        "YONTRACK_IT_POSTGRES_PORT" to postgresPort.toString(),
        "YONTRACK_IT_ELASTIC_PORT" to elasticPort.toString(),
        "YONTRACK_IT_RABBIT_PORT" to rabbitPort.toString(),
        "YONTRACK_IT_RABBIT_MGMT_PORT" to rabbitManagementPort.toString(),
        "YONTRACK_IT_VAULT_PORT" to vaultPort.toString(),
    )

    /**
     * The Spring properties the tests need in order to talk to *this*
     * instance rather than to the historical ports.
     */
    val systemProperties: Map<String, String> = mapOf(
        "spring.datasource.url" to jdbcUrl,
        "spring.rabbitmq.port" to rabbitPort.toString(),
        "spring.elasticsearch.uris" to elasticUri,
        "ontrack.config.vault.uri" to vaultUri,
    )

    fun writeInstanceEnv(file: File) {
        StackSlots.writeInstanceEnv(
            file = file,
            entries = mapOf(
                "IT_SLUG" to slug,
                ItStack.SLOT_KEY to slot.toString(),
                "IT_PROJECT" to projectName,
            ) + composeEnvironment,
            systemProperties = systemProperties,
        )
    }

    fun describe(): String =
        "slot $slot (project $projectName): postgres $postgresPort, elasticsearch $elasticPort, " +
                "rabbit $rabbitPort, vault $vaultPort"
}
