import net.nemerosa.ontrack.build.BuildInfoVersion
import net.nemerosa.ontrack.build.notices.ThirdPartyNoticesTask
import org.cyclonedx.gradle.CyclonedxDirectTask
import org.springframework.boot.gradle.dsl.SpringBootExtension

plugins {
    `java-library`
    id("com.google.cloud.tools.jib")
    id("org.cyclonedx.bom")
}

apply(plugin = "org.springframework.boot")

dependencies {
    api("org.springframework.boot:spring-boot-starter-webmvc")
    api("org.springframework.boot:spring-boot-starter-security")
    api("org.springframework.boot:spring-boot-starter-actuator")
    api("org.springframework.boot:spring-boot-starter-aspectj")
    api("org.springframework.boot:spring-boot-starter-jdbc")
    api("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    api(project(":ontrack-ui-support"))
    api(project(":ontrack-ui-graphql"))
    api(project(":ontrack-extension-api"))
    api(project(":ontrack-extension-support"))

    implementation(project(":ontrack-job"))
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.apache.commons:commons-lang3")
    implementation("org.apache.commons:commons-text")
    implementation("commons-io:commons-io")
    implementation("jakarta.validation:jakarta.validation-api")

    runtimeOnly(project(":ontrack-service"))
    runtimeOnly(project(":ontrack-repository-impl"))
    runtimeOnly(project(":ontrack-rabbitmq"))
    runtimeOnly(project(":ontrack-database"))

    // Metric runtimes
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    testImplementation(testFixtures(project(":ontrack-it-utils")))
    testImplementation(testFixtures(project(":ontrack-model")))
    testImplementation(testFixtures(project(":ontrack-ui-graphql")))
    testImplementation(testFixtures(project(":ontrack-extension-api")))
    testImplementation(testFixtures(project(":ontrack-extension-support")))
    testImplementation(testFixtures(project(":ontrack-extension-casc")))
    // Waiting for the re-indexation of the search documents after a rename (#2083)
    testImplementation(testFixtures(project(":ontrack-extension-queue")))
    // Reading the CSV export of the events
    testImplementation("com.opencsv:opencsv")

    // List of extensions needed for some tests spanning all modules
    testImplementation(project(":ontrack-extension-config"))
    testImplementation(project(":ontrack-extension-casc"))
    testImplementation(project(":ontrack-extension-general"))

    // List of extensions to include in core
    runtimeOnly(project(":ontrack-extension-general"))
    runtimeOnly(project(":ontrack-extension-jenkins"))
    runtimeOnly(project(":ontrack-extension-jira"))
    runtimeOnly(project(":ontrack-extension-artifactory"))
    runtimeOnly(project(":ontrack-extension-issues"))
    runtimeOnly(project(":ontrack-extension-scm"))
    runtimeOnly(project(":ontrack-extension-git"))
    runtimeOnly(project(":ontrack-extension-github"))
    runtimeOnly(project(":ontrack-extension-gitlab"))
    runtimeOnly(project(":ontrack-extension-stash"))
    runtimeOnly(project(":ontrack-extension-bitbucket-cloud"))
    runtimeOnly(project(":ontrack-extension-stale"))
    runtimeOnly(project(":ontrack-extension-vault"))
    runtimeOnly(project(":ontrack-extension-influxdb"))
    runtimeOnly(project(":ontrack-extension-sonarqube"))
    runtimeOnly(project(":ontrack-extension-casc"))
    runtimeOnly(project(":ontrack-extension-elastic"))
    runtimeOnly(project(":ontrack-extension-slack"))
    runtimeOnly(project(":ontrack-extension-chart"))
    runtimeOnly(project(":ontrack-extension-auto-versioning"))
    runtimeOnly(project(":ontrack-extension-license"))
    runtimeOnly(project(":ontrack-extension-tfc"))
    runtimeOnly(project(":ontrack-extension-recordings"))
    runtimeOnly(project(":ontrack-extension-notifications"))
    runtimeOnly(project(":ontrack-extension-hook"))
    runtimeOnly(project(":ontrack-extension-queue"))
    runtimeOnly(project(":ontrack-extension-workflows"))
    runtimeOnly(project(":ontrack-extension-environments"))
    runtimeOnly(project(":ontrack-extension-config"))
    runtimeOnly(project(":ontrack-extension-kubernetes"))
    runtimeOnly(project(":ontrack-extension-findings"))
    runtimeOnly(project(":ontrack-extension-scorecard"))
    runtimeOnly(project(":ontrack-extension-audit-trail"))
    runtimeOnly(project(":ontrack-extension-agents"))
}

// `project.version` only ever holds the base version (6.0.1, 6.0.0-beta.7). The release-candidate
// version (6.0.1-rc.700, 6.0.0-beta.7.rc.700) is written by `writeVersion` and read by the CI shell
// - see the "Compute the version" step in .github/workflows/ci.yml. The `build` job exports it as
// VERSION, and bootBuildInfo runs inside its `dockerBuild jibDockerBuild` step, so the value is in
// scope.
//
// The displayed version is the base version, the rc one is kept as `full`: a release re-tags this
// image without rebuilding it, so the base version is the only one it can show once released.
// See BuildInfoVersion.
val buildInfoVersion = BuildInfoVersion.compute(
    projectVersion = project.version.toString(),
    version = providers.environmentVariable("VERSION").orNull,
    branch = providers.environmentVariable("GITHUB_REF_NAME").orNull,
    build = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull,
    commit = providers.environmentVariable("GITHUB_SHA").orNull,
)

configure<SpringBootExtension> {
    buildInfo {
        properties {
            time = null
            // Read back as VersionInfo.display by EnvServiceImpl.
            version = buildInfoVersion.version
            // EnvServiceImpl reads these four by name; leaving them unset is what made
            // VersionInfo.full/branch/build/commit report "n/a" on every deployed instance.
            additional.putAll(buildInfoVersion.additional)
        }
    }
}

// The SBOM of the application (#2179): what it ships, so its runtime classpath only - no test, build
// or development-only library. The Spring Boot plugin writes it to
// build/reports/cyclonedx/application.cdx.json and embeds it in the boot jar. The `sbom` actuator
// endpoint is not exposed: library versions are no business of an anonymous caller.
tasks.named<CyclonedxDirectTask>("cyclonedxDirectBom") {
    includeConfigs = listOf("runtimeClasspath")
    // Every library has to be in the inventory: an unresolved one is a gap, not a detail.
    failOnUnresolvedDependencies = true
}

// The third-party notices of the image (#2182): every external module of the runtime classpath - the
// SBOM above, less the Yontrack modules - with the license, notice and copying files its jars
// package, verbatim, and the standard text of the licenses of those which package none, from
// src/notices/licenses. Shipped by Jib as /app/THIRD-PARTY-NOTICES.txt.
val thirdPartyNoticesDir = layout.buildDirectory.dir("notices")

val thirdPartyNotices = tasks.register<ThirdPartyNoticesTask>("thirdPartyNotices") {
    group = "reporting"
    description = "Writes the THIRD-PARTY-NOTICES.txt of the backend image to build/notices/."
    // The external modules only: without the filter, the task would build every Yontrack module
    // only to leave its jar out.
    val runtimeArtifacts = configurations.runtimeClasspath.get().incoming
        .artifactView { componentFilter { it is ModuleComponentIdentifier } }
        .artifacts
    artifacts.set(runtimeArtifacts.resolvedArtifacts)
    classpath.from(runtimeArtifacts.artifactFiles)
    sbom.set(tasks.named<CyclonedxDirectTask>("cyclonedxDirectBom").flatMap { it.jsonOutput })
    licenseTexts.set(layout.projectDirectory.dir("src/notices/licenses"))
    title.set(provider { "Yontrack ${project.version} - backend image" })
    output.set(thirdPartyNoticesDir.map { it.file("THIRD-PARTY-NOTICES.txt") })
}

// The licence of Yontrack, from the root of the repository. Shipped by Jib as /app/LICENSE.
val licenseFilesDir = layout.buildDirectory.dir("license")

val licenseFiles = tasks.register<Copy>("licenseFiles") {
    from(rootProject.layout.projectDirectory) {
        include("LICENSE")
    }
    into(licenseFilesDir)
}

val isMacOS = System.getProperty("os.name").lowercase().contains("mac")

jib {
    to {
        image = "nemerosa/ontrack"
        tags = setOf(version as String, "latest")
    }
    from {
        image = "azul/zulu-openjdk-alpine:25"
        platforms {
            if (isMacOS) {
                platform {
                    architecture = "arm64"
                    os = "linux"
                }
            } else {
                platform {
                    architecture = "amd64"
                    os = "linux"
                }
            }
        }
    }
    extraDirectories {
        paths {
            path {
                setFrom(thirdPartyNoticesDir.get().asFile)
                into = "/app"
            }
            path {
                setFrom(licenseFilesDir.get().asFile)
                into = "/app"
            }
        }
    }
    container {
        // Mitigation of CVE-2026-114938 (#2158): remove once the base image is on JDK 25.0.5.1 or later
        jvmFlags = listOf("-XX:-UseBASE64Intrinsics")
        ports = listOf("8080", "8800")
        volumes = listOf("/var/ontrack/data")
        environment = mapOf(
            "ONTRACK_CONFIG_APPLICATION_WORKING_DIR" to "/var/ontrack/data",
        )
    }
}

tasks.named("jibDockerBuild") {
    shouldRunAfter("integrationTest")
}

listOf("jib", "jibDockerBuild", "jibBuildTar").forEach { name ->
    tasks.named(name) { dependsOn(thirdPartyNotices, licenseFiles) }
}

val jibDockerBuildYontrack = tasks.register<Exec>("jibDockerBuildYontrack") {
    dependsOn("jibDockerBuild")
    commandLine(
        "sh", "-c",
        "docker image tag nemerosa/ontrack:${project.version} yontrack/yontrack:${project.version} && " +
        "docker image tag nemerosa/ontrack:latest yontrack/yontrack:latest"
    )
}

val jibYontrack = tasks.register<Exec>("jibYontrack") {
    dependsOn("jib")
    commandLine("sh", "-c", """
        docker pull nemerosa/ontrack:${project.version} && \
        docker tag nemerosa/ontrack:${project.version} yontrack/yontrack:${project.version} && \
        docker push yontrack/yontrack:${project.version} && \
        docker tag nemerosa/ontrack:latest yontrack/yontrack:latest && \
        docker push yontrack/yontrack:latest
    """)
}

val dockerBuild = tasks.register("dockerBuild") {
    dependsOn("jibDockerBuild", jibDockerBuildYontrack)
}
