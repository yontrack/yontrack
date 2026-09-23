import net.nemerosa.ontrack.build.BuildInfoVersion
import org.springframework.boot.gradle.dsl.SpringBootExtension

plugins {
    `java-library`
    id("com.google.cloud.tools.jib")
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
    runtimeOnly(project(":ontrack-extension-indicators"))
    runtimeOnly(project(":ontrack-extension-casc"))
    runtimeOnly(project(":ontrack-extension-elastic"))
    runtimeOnly(project(":ontrack-extension-slack"))
    runtimeOnly(project(":ontrack-extension-chart"))
    runtimeOnly(project(":ontrack-extension-delivery-metrics"))
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
    runtimeOnly(project(":ontrack-extension-findings"))
}

// `project.version` only ever holds the base version (5.4.0). The release-candidate suffix is
// computed by the CI shell *after* Gradle has run - see the "Compute the version" step in
// .github/workflows/ci.yml. The `build` job exports it as VERSION, and bootBuildInfo runs inside
// its `dockerBuild jibDockerBuild` step, so the value is in scope.
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
    container {
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
