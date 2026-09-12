package net.nemerosa.ontrack.build

import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.zip.CRC32

/**
 * Slot and port arithmetic shared by the Compose stacks the build drives --
 * the integration test stack ([ItStack]) and the KDSL acceptance stacks
 * ([KdslStack]).
 *
 * Several checkouts work on Yontrack at once, one agent per worktree, and a
 * stack whose host ports are hard-coded lets only one of them run. Each
 * checkout is therefore an *instance*: a slug derived from its directory names
 * the Compose project, and a *slot* offsets every published port by
 * `slot * 100`. This is the scheme `scripts/dev-stack.sh` applies to the
 * development stack, in the language the Gradle build speaks.
 *
 * Slot 0 belongs to the main working copy, which keeps the ports everyone
 * already has in their bookmarks -- and so does every CI runner, a fresh
 * clone with nothing else on it. A linked worktree hashes into slot 1 and up,
 * and any slot is bumped along until its ports are actually free, which is
 * what lets stacks of different families coexist on one machine even though
 * they share base ports.
 *
 * Everything here is pure apart from [portFree] and [isMainCheckout], and is
 * covered by `StackSlotsTest`.
 */
object StackSlots {

    /** Slot 0 belongs to the main working copy; linked worktrees hash from here up. */
    const val SLOT_MIN = 1

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

    fun slotFromPath(path: String, slotMax: Int): Int {
        val range = slotMax - SLOT_MIN + 1
        return SLOT_MIN + (pathHash(path) % range).toInt()
    }

    /**
     * Used when the hashed slot turns out to be taken. Never returns 0: that
     * slot belongs to the main working copy and is not up for grabs.
     */
    fun nextSlot(slot: Int, slotMax: Int): Int {
        val next = slot + 1
        return if (next > slotMax || next < SLOT_MIN) SLOT_MIN else next
    }

    fun offset(slot: Int): Int = slot * 100

    fun port(base: Int, slot: Int): Int = base + offset(slot)

    fun ports(basePorts: List<Int>, slot: Int): List<Int> = basePorts.map { port(it, slot) }

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
        slotMax: Int,
        basePorts: List<Int>,
        stackName: String,
        portFree: (Int) -> Boolean,
    ): Int {
        if (recordedSlot != null) return recordedSlot
        var slot = if (mainCheckout) 0 else slotFromPath(path, slotMax)
        var attempts = 0
        while (!ports(basePorts, slot).all(portFree)) {
            attempts++
            if (attempts > slotMax) {
                error(
                    "No free slot for the $stackName stack: every slot 0-$slotMax has ports in " +
                            "use. Stop a stack you no longer need (docker compose ls) and try again."
                )
            }
            slot = nextSlot(slot, slotMax)
        }
        return slot
    }

    /**
     * Reads back the slot recorded by a previous run, or `null` when there is
     * none to read.
     */
    fun readRecordedSlot(instanceEnv: File, key: String): Int? =
        instanceEnv.takeIf { it.isFile }
            ?.readLines()
            ?.firstOrNull { it.startsWith("$key=") }
            ?.substringAfter('=')
            ?.trim()
            ?.toIntOrNull()

    /**
     * True when this is the main working copy rather than a linked worktree:
     * a worktree's `.git` is a file pointing at the common directory, not a
     * directory of its own.
     */
    fun isMainCheckout(rootDir: File): Boolean = File(rootDir, ".git").isDirectory

    /** The first port an unprivileged process is allowed to bind on Linux. */
    const val FIRST_UNPRIVILEGED_PORT = 1024

    private const val CONNECT_TIMEOUT_MS = 200

    /**
     * True when nothing is listening on the port.
     *
     * Binding is the stricter test -- it fails for a listener on any address,
     * which is what Docker publishes -- and is used wherever it is allowed.
     * A privileged port cannot be bound by an unprivileged process on Linux
     * at all, so there a bind failure says nothing about whether the port is
     * in use: a CI runner would read every slot as taken. Those ports are
     * probed by connecting instead -- something answers, or nothing does.
     * The KDSL acceptance stack publishes LDAP on 389 and 636, which is the
     * only reason this branch exists.
     */
    fun portFree(port: Int): Boolean =
        if (port < FIRST_UNPRIVILEGED_PORT) nothingAnswersOn(port) else nothingBoundOn(port)

    fun nothingBoundOn(port: Int): Boolean =
        try {
            ServerSocket().use { socket ->
                socket.reuseAddress = false
                socket.bind(InetSocketAddress(port))
            }
            true
        } catch (ignored: Exception) {
            false
        }

    fun nothingAnswersOn(port: Int): Boolean =
        try {
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), CONNECT_TIMEOUT_MS) }
            false
        } catch (ignored: Exception) {
            true
        }

    /**
     * Records an instance, so that the next build reuses the same slot and
     * anything else that wants to talk to the stack -- psql, an IDE run
     * configuration, an agent poking at the database -- can discover its
     * ports.
     */
    fun writeInstanceEnv(
        file: File,
        entries: Map<String, String>,
        systemProperties: Map<String, String> = emptyMap(),
    ) {
        file.parentFile?.mkdirs()
        file.writeText(
            buildString {
                appendLine("# Generated by the Gradle build -- do not edit, do not commit.")
                entries.forEach { (key, value) -> appendLine("$key=$value") }
                systemProperties.forEach { (key, value) -> appendLine("# -D$key=$value") }
            }
        )
    }
}
