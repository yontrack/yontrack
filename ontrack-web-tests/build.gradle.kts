import com.github.gradle.node.NodeExtension
import com.github.gradle.node.npm.task.NpmTask

plugins {
    id("com.github.node-gradle.node")
}

// Node environment

configure<NodeExtension> {
    version.set("20.2.0")
    npmVersion.set("9.6.6")
    download.set(true)
}

// Test environment

val isCI = System.getenv("CI") == "true"

val playwrightInstall by tasks.registering(NpmTask::class) {
    dependsOn("npmInstall")
    args.set(listOf("run", "playwright-install"))
}

val playwrightSetup by tasks.registering(NpmTask::class) {
    dependsOn(playwrightInstall)
    args.set(listOf("run", "playwright-setup"))
}

// Testing

// Splitting the suite across CI runners. Playwright reads `--shard` from the command line only —
// there is no equivalent setting in playwright.config.js — so it is appended to the npm arguments
// here. Unset means the whole suite, which is every local run.
//
// Playwright balances the shards on test *count*, walking the spec files in path order. Measured
// against the per-test durations of a real run, that lands within a few seconds of an optimal
// duration-balanced split, and unlike a hand-written list of files it takes in every new spec on
// its own.
val shardIndex: Int = System.getProperty("shard.index")?.toIntOrNull() ?: 1
val shardTotal: Int = System.getProperty("shard.total")?.toIntOrNull() ?: 1
val isSharded = shardTotal > 1
val shardSuffix = if (isSharded) "-$shardIndex" else ""

val uiTest by tasks.registering(NpmTask::class) {
    dependsOn(playwrightSetup)
    if (!isCI) {
        dependsOn(":ontrack-kdsl-acceptance:kdslAcceptanceTestComposeUp")
        finalizedBy(":ontrack-kdsl-acceptance:kdslAcceptanceTestComposeDown")
    }

    args.set(
        if (isSharded) {
            listOf("run", "test", "--", "--shard=$shardIndex/$shardTotal")
        } else {
            listOf("run", "test")
        }
    )
    // Both shards write into reports/main/junit, so the report carries the shard in its name. The
    // stamp's glob is reports/*/junit/*.xml and is unaffected.
    environment.put("JUNIT_REPORT_PATH", "reports/main/junit/report$shardSuffix.xml")
    environment.put("HTML_REPORT_PATH", "reports/main/html")
}

// Specialized tests

val uiLdapTest by tasks.registering(NpmTask::class) {
    dependsOn(playwrightSetup)
    dependsOn(":ontrack-kdsl-acceptance:kdslLdapComposeUp")
    finalizedBy(":ontrack-kdsl-acceptance:kdslLdapComposeDown")

    shouldRunAfter(uiTest)
    shouldRunAfter(":ontrack-kdsl-acceptance:kdslAcceptanceTestComposeDown")

    args.set(listOf("run", "test-ldap"))
    environment.put("JUNIT_REPORT_PATH", "reports/ldap/junit/report.xml")
    environment.put("HTML_REPORT_PATH", "reports/ldap/html")
}

val uiOidcTest by tasks.registering(NpmTask::class) {
    dependsOn(playwrightSetup)
    dependsOn(":ontrack-kdsl-acceptance:kdslOidcComposeUp")
    finalizedBy(":ontrack-kdsl-acceptance:kdslOidcComposeDown")

    shouldRunAfter(uiLdapTest)
    shouldRunAfter(":ontrack-kdsl-acceptance:kdslLdapComposeDown")

    args.set(listOf("run", "test-oidc"))
    environment.put("JUNIT_REPORT_PATH", "reports/oidc/junit/report.xml")
    environment.put("HTML_REPORT_PATH", "reports/oidc/html")
}

// All tests

val uiTests by tasks.registering {
    dependsOn(uiTest)
    dependsOn(uiLdapTest)
    dependsOn(uiOidcTest)
}
