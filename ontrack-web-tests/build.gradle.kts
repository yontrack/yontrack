import com.github.gradle.node.NodeExtension
import com.github.gradle.node.npm.task.NpmTask
import net.nemerosa.ontrack.build.Coverage

plugins {
    id("com.github.node-gradle.node")
}

// Node environment

configure<NodeExtension> {
    version.set("24.21.0")
    npmVersion.set("11.19.0")
    download.set(true)
}

// Test environment

val isCI = System.getenv("CI") == "true"

// Backend coverage of the Playwright legs (#1819). The code they exercise runs in the
// `nemerosa/ontrack` container of the acceptance stacks, so the agent and the dump live in
// :ontrack-kdsl-acceptance; all this leg has to do is make sure the data is pulled out before its
// stack is stopped. Gated on `-Pcoverage`, so the task graph of an ordinary run is unchanged.
//
// The `main` leg shares the `kdslAcceptanceTest` Compose variant with the KDSL suite, whose
// default session type is `kdsl`; the CI workflow names it `ui-main` through COVERAGE_SESSION
// (#1821). A local run leaves it at the default, and its data lands in `build/jacoco/kdsl.exec`.
val coverage = providers.gradleProperty(Coverage.GRADLE_PROPERTY).isPresent

val playwrightInstall = tasks.register<NpmTask>("playwrightInstall") {
    dependsOn("npmInstall")
    args.set(listOf("run", "playwright-install"))
}

val playwrightSetup = tasks.register<NpmTask>("playwrightSetup") {
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

val uiTest = tasks.register<NpmTask>("uiTest") {
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
    // All the shards write into reports/main/junit, so the report carries the shard in its name. The
    // stamp's glob is reports/*/junit/*.xml and is unaffected.
    environment.put("JUNIT_REPORT_PATH", "reports/main/junit/report$shardSuffix.xml")
    environment.put("HTML_REPORT_PATH", "reports/main/html")

    if (coverage) {
        finalizedBy(":ontrack-kdsl-acceptance:kdslAcceptanceTestCoverageDump")
    }
}

// Specialized tests

val uiLdapTest = tasks.register<NpmTask>("uiLdapTest") {
    dependsOn(playwrightSetup)
    dependsOn(":ontrack-kdsl-acceptance:kdslLdapComposeUp")
    finalizedBy(":ontrack-kdsl-acceptance:kdslLdapComposeDown")

    shouldRunAfter(uiTest)
    shouldRunAfter(":ontrack-kdsl-acceptance:kdslAcceptanceTestComposeDown")

    args.set(listOf("run", "test-ldap"))
    environment.put("JUNIT_REPORT_PATH", "reports/ldap/junit/report.xml")
    environment.put("HTML_REPORT_PATH", "reports/ldap/html")

    if (coverage) {
        finalizedBy(":ontrack-kdsl-acceptance:kdslLdapCoverageDump")
    }
}

val uiOidcTest = tasks.register<NpmTask>("uiOidcTest") {
    dependsOn(playwrightSetup)
    dependsOn(":ontrack-kdsl-acceptance:kdslOidcComposeUp")
    finalizedBy(":ontrack-kdsl-acceptance:kdslOidcComposeDown")

    shouldRunAfter(uiLdapTest)
    shouldRunAfter(":ontrack-kdsl-acceptance:kdslLdapComposeDown")

    args.set(listOf("run", "test-oidc"))
    environment.put("JUNIT_REPORT_PATH", "reports/oidc/junit/report.xml")
    environment.put("HTML_REPORT_PATH", "reports/oidc/html")

    if (coverage) {
        finalizedBy(":ontrack-kdsl-acceptance:kdslOidcCoverageDump")
    }
}

// All tests

val uiTests = tasks.register("uiTests") {
    dependsOn(uiTest)
    dependsOn(uiLdapTest)
    dependsOn(uiOidcTest)
}
