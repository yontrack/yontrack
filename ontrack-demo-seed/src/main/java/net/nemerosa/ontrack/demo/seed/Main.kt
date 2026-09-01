package net.nemerosa.ontrack.demo.seed

import net.nemerosa.ontrack.kdsl.connector.support.DefaultConnector
import net.nemerosa.ontrack.kdsl.spec.Ontrack
import kotlin.system.exitProcess

/**
 * Resets the demo environment and seeds it again.
 *
 * Destructive by design: it deletes every project and every environment on the instance it
 * is pointed at, then recreates the demo dataset. See [DemoSeedConfig] for the environment
 * variables it reads, and `doc/dev-guide/demo-seed.md` for how it is run.
 */
fun main() {
    val config = try {
        DemoSeedConfig.from(System.getenv())
    } catch (ex: IllegalArgumentException) {
        System.err.println("ERROR: ${ex.message}")
        exitProcess(1)
    }

    println("Resetting the demo at ${config.url}")

    val ontrack = Ontrack(DefaultConnector(url = config.url, token = config.token))
    val changelog = GitChangelogSource(
        directory = config.repository,
        max = config.changelogMax,
        warn = { println("WARNING: $it") },
    ).entries()
    println("Changelog since the last release: ${changelog.size} commit(s)")

    DemoSeed(KdslDemoTarget(ontrack)).run(DemoContent.dataset(changelog))

    println("The demo at ${config.url} has been reset.")
}
