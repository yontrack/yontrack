package net.nemerosa.ontrack.build

import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.zip.CRC32

/**
 * Port allocation for the integration test Compose stack
 * (`compose/docker-compose-it.yml`).
 *
 * Several checkouts run integration tests at the same time -- one agent per
 * worktree -- and a single hard-coded set of published ports lets only one of
 * them run. This applies to the IT stack the scheme
 * `scripts/dev-stack.sh` already applies to the development stack: a checkout
 * is an *instance*, identified by a slug derived from its directory and
 * allocated a *slot* that offsets every published port by `slot * 100`.
 *
 * Slot 0 is reserved for the main working copy, so a plain `./gradlew
 * integrationTest` on a clean machine -- and every CI runner, which is a
 * fresh clone -- keeps the historical ports and nothing has to be told about
 * any of this. A linked worktree hashes into slot 1-9, and a slot whose ports
 * are already taken is bumped along until a free one is found. That last part
 * is what also keeps the IT stack out of the way of a *development* stack
 * running in the same checkout: the two families share their base ports, so
 * the one that starts second simply moves.
 *
 * Everything in this file is pure apart from [portFree] and the two `resolve`
 * entry points, and is covered by `ItStackTest`.
 */
object ItStack {

    /** Slot 0 belongs to the main working copy; linked worktrees hash into 1-9. */
    const val SLOT_MIN = 1
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

    /** Turns a checkout path into a short, filesystem- and Docker-safe name. */
    fun slug(path: String): String {
        val name = File(path).name
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        return name.ifEmpty { "instance" }
    }

    /**
     * Hashes the *absolute* path, so two checkouts that happen to share a
     * basename (every `5.0` directory, for instance) still get different slots.
     */
    fun pathHash(path: String): Long {
        val crc = CRC32()
        crc.update(path.toByteArray())
        return crc.value
    }

    fun slotFromPath(path: String): Int {
        val range = SLOT_MAX - SLOT_MIN + 1
        return SLOT_MIN + (pathHash(path) % range).toInt()
    }

    /**
     * Used when the hashed slot turns out to be taken. Never returns 0: that
     * slot belongs to the main working copy and is not up for grabs.
     */
    fun nextSlot(slot: Int): Int {
        val next = slot + 1
        return if (next > SLOT_MAX || next < SLOT_MIN) SLOT_MIN else next
    }

    fun offset(slot: Int): Int = slot * 100

    fun port(base: Int, slot: Int): Int = base + offset(slot)

    fun ports(slot: Int): List<Int> = BASE_PORTS.map { port(it, slot) }

    fun projectName(slug: String): String = "yontrack-it-$slug"

    /**
     * Picks the slot for a checkout: the one already recorded if there is one,
     * otherwise 0 for the main working copy or a hash of the path for a
     * worktree, bumped along until the ports are actually free.
     *
     * A recorded slot is taken as-is and never probed: the stack it names is
     * very probably up, which is exactly when its ports are *not* free.
     */
    fun resolveSlot(
        path: String,
        mainCheckout: Boolean,
        recordedSlot: Int?,
        portFree: (Int) -> Boolean,
    ): Int {
        if (recordedSlot != null) return recordedSlot
        var slot = if (mainCheckout) 0 else slotFromPath(path)
        var attempts = 0
        while (!ports(slot).all(portFree)) {
            attempts++
            if (attempts > SLOT_MAX) {
                error(
                    "No free slot for the integration test stack: every slot 0-$SLOT_MAX " +
                            "has ports in use. Stop a stack you no longer need " +
                            "(docker compose ls) and try again."
                )
            }
            slot = nextSlot(slot)
        }
        return slot
    }

    /**
     * Reads back the slot recorded by a previous run, or `null` when there is
     * none to read.
     */
    fun readRecordedSlot(instanceEnv: File): Int? =
        instanceEnv.takeIf { it.isFile }
            ?.readLines()
            ?.firstOrNull { it.startsWith("IT_SLOT=") }
            ?.substringAfter('=')
            ?.trim()
            ?.toIntOrNull()

    /**
     * True when this is the main working copy rather than a linked worktree:
     * a worktree's `.git` is a file pointing at the common directory, not a
     * directory of its own.
     */
    fun isMainCheckout(rootDir: File): Boolean = File(rootDir, ".git").isDirectory

    /** True when nothing is listening on the port, on any address. */
    fun portFree(port: Int): Boolean =
        try {
            ServerSocket().use { socket ->
                socket.reuseAddress = false
                socket.bind(InetSocketAddress(port))
            }
            true
        } catch (ignored: Exception) {
            false
        }

    /** Resolves the whole instance for a checkout. */
    fun resolve(rootDir: File, instanceEnv: File = File(rootDir, INSTANCE_ENV_PATH)): ItStackInstance {
        val path = rootDir.absolutePath
        val slot = resolveSlot(
            path = path,
            mainCheckout = isMainCheckout(rootDir),
            recordedSlot = readRecordedSlot(instanceEnv),
            portFree = ::portFree,
        )
        return ItStackInstance(slug = slug(path), slot = slot)
    }

    const val INSTANCE_ENV_PATH = ".yontrack-it/instance.env"
}

/**
 * The resolved integration test stack for one checkout: its Compose project
 * name and the host ports its middleware publishes.
 */
data class ItStackInstance(
    val slug: String,
    val slot: Int,
) {

    val projectName: String = ItStack.projectName(slug)

    val postgresPort: Int = ItStack.port(ItStack.BASE_POSTGRES, slot)
    val elasticPort: Int = ItStack.port(ItStack.BASE_ELASTIC, slot)
    val rabbitPort: Int = ItStack.port(ItStack.BASE_RABBIT, slot)
    val rabbitManagementPort: Int = ItStack.port(ItStack.BASE_RABBIT_MGMT, slot)
    val vaultPort: Int = ItStack.port(ItStack.BASE_VAULT, slot)

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

    /**
     * Records the instance, so that the next build reuses the same slot and
     * anything else that wants to talk to this stack -- psql, an IDE run
     * configuration -- can discover its ports.
     */
    fun writeInstanceEnv(file: File) {
        file.parentFile?.mkdirs()
        file.writeText(
            buildString {
                appendLine("# Generated by the Gradle build -- do not edit, do not commit.")
                appendLine("IT_SLUG=$slug")
                appendLine("IT_SLOT=$slot")
                appendLine("IT_PROJECT=$projectName")
                composeEnvironment.forEach { (key, value) -> appendLine("$key=$value") }
                systemProperties.forEach { (key, value) -> appendLine("# -D$key=$value") }
            }
        )
    }

    fun describe(): String =
        "slot $slot (project $projectName): postgres $postgresPort, elasticsearch $elasticPort, " +
                "rabbit $rabbitPort, vault $vaultPort"
}
