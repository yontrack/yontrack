import com.github.gradle.node.NodeExtension
import com.github.gradle.node.npm.task.NpmTask
import net.nemerosa.ontrack.build.Coverage

plugins {
    base
    id("com.github.node-gradle.node")
}

configure<NodeExtension> {
    version.set("24.21.0")
    npmVersion.set("11.19.0")
    download.set(true)
}

val webBuild by tasks.registering(NpmTask::class) {
    dependsOn("npmInstall")
    args.set(listOf("run", "build"))
}

val test by tasks.registering(NpmTask::class) {
    dependsOn("npmInstall")
    args.set(listOf("run", "test"))
}

tasks.named("build") {
    // dependsOn(webBuild)
    dependsOn(test)
}

// ===================================================================================================================
// Test coverage collection (#1820)
//
// The same suite, run with `--coverage`. It is a *second* task on a *second* npm script rather
// than a flag on `test`, because `test` is what `build` and `dockerBuild` depend on and the design
// collects coverage on coverage runs only -- see `docs/grilling/2026-09-coverage/README.md`.
//
// Nothing hangs off `check` or `build`: this is called explicitly, by a developer or by the
// coverage job of #1821, which then reads `coverage/coverage-summary.json`.
//
// This project is excluded from `javaProjects` in the root build script and so never sees the
// `-Pcoverage` reading done there; the property is read again here, under the one name
// `Coverage.GRADLE_PROPERTY` spells, so that there is still a single definition of it.
// ===================================================================================================================

val coverageEnabled = providers.gradleProperty(Coverage.GRADLE_PROPERTY).isPresent

val testCoverage by tasks.registering(NpmTask::class) {
    group = "verification"
    description = "Runs the Jest tests with coverage, writing the reports to `coverage/`. Requires -P${Coverage.GRADLE_PROPERTY}."
    dependsOn("npmInstall")
    args.set(listOf("run", "test:coverage"))
    // The reason is read back as "Skipping task ... as task onlyIf '<reason>' is false", so it is
    // phrased as the condition that has to hold.
    onlyIf("-P${Coverage.GRADLE_PROPERTY} is set") { coverageEnabled }
}

// Docker image

val dockerBuild by tasks.registering(Exec::class) {
    dependsOn(test)
    workingDir = projectDir
    commandLine("sh", "-c", """
        docker image build -t nemerosa/ontrack-ui:${project.version} . && \
        docker image tag nemerosa/ontrack-ui:${project.version} nemerosa/ontrack-ui:latest && \
        docker image tag nemerosa/ontrack-ui:${project.version} yontrack/yontrack-ui:${project.version} && \
        docker image tag nemerosa/ontrack-ui:${project.version} yontrack/yontrack-ui:latest
    """)
}
