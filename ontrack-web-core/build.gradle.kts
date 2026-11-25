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

val webBuild = tasks.register<NpmTask>("webBuild") {
    dependsOn("npmInstall")
    args.set(listOf("run", "build"))
}

val test = tasks.register<NpmTask>("test") {
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

val testCoverage = tasks.register<NpmTask>("testCoverage") {
    group = "verification"
    description = "Runs the Jest tests with coverage, writing the reports to `coverage/`. Requires -P${Coverage.GRADLE_PROPERTY}."
    dependsOn("npmInstall")
    args.set(listOf("run", "test:coverage"))
    // The reason is read back as "Skipping task ... as task onlyIf '<reason>' is false", so it is
    // phrased as the condition that has to hold.
    onlyIf("-P${Coverage.GRADLE_PROPERTY} is set") { coverageEnabled }
}

// ===================================================================================================================
// SBOM of the UI (#2179)
//
// The packages the UI ships, in CycloneDX, written to the same place as the backend's
// (`ontrack-ui/build/reports/cyclonedx/application.cdx.json`). `--omit dev`: the production
// dependencies only - which is only true as long as anything used to build, lint or test the UI sits
// in `devDependencies`.
//
// Read from `node_modules`, which `npmInstall` fills: the lock file alone lacks the license of
// some packages.
//
// `--omit optional`: none of the optional dependencies is in the image. They are the native
// builds of the Next.js compiler (`@next/swc-*`), used by `next build` only; `sharp` and its
// native builds (`@img/*`), which `next.config.js` keeps out of the standalone output; and the
// TypeScript types of `dompurify`. The task fails if the SBOM still lists `sharp`: its native
// build is LGPL-3.0, and the image is not meant to ship it - see `next.config.js`.
// ===================================================================================================================

val sbomFile = layout.buildDirectory.file("reports/cyclonedx/application.cdx.json")

tasks.register<NpmTask>("sbom") {
    group = "reporting"
    description = "Writes the CycloneDX SBOM of the UI's production dependencies to build/reports/cyclonedx/."
    dependsOn("npmInstall")
    inputs.files("package.json", "package-lock.json")
    outputs.file(sbomFile)
    args.set(
        listOf(
            "sbom", "--sbom-format", "cyclonedx", "--sbom-type", "application",
            "--omit", "dev", "--omit", "optional",
        )
    )
    // `npm sbom` only prints to the standard output.
    val output = java.io.ByteArrayOutputStream()
    execOverrides.set { standardOutput = output }
    doLast {
        val sbom = output.toByteArray()
        @Suppress("UNCHECKED_CAST")
        val components = (groovy.json.JsonSlurper().parse(sbom) as Map<String, Any?>)["components"]
                as List<Map<String, Any?>>
        val excluded = components
            .map { listOfNotNull(it["group"]?.toString()?.takeIf(String::isNotEmpty), it["name"]).joinToString("/") }
            .filter { it == "sharp" || it.startsWith("@img/") }
        if (excluded.isNotEmpty()) {
            throw GradleException(
                "The SBOM of the UI lists $excluded, which the image does not ship - see next.config.js"
            )
        }
        sbomFile.get().asFile.apply {
            parentFile.mkdirs()
            writeBytes(sbom)
        }
    }
}

// Docker image

val dockerBuild = tasks.register<Exec>("dockerBuild") {
    dependsOn(test)
    workingDir = projectDir
    commandLine("sh", "-c", """
        docker image build --build-context root=.. --build-arg VERSION=${project.version} -t nemerosa/ontrack-ui:${project.version} . && \
        docker image tag nemerosa/ontrack-ui:${project.version} nemerosa/ontrack-ui:latest && \
        docker image tag nemerosa/ontrack-ui:${project.version} yontrack/yontrack-ui:${project.version} && \
        docker image tag nemerosa/ontrack-ui:${project.version} yontrack/yontrack-ui:latest
    """)
}
