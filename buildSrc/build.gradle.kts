// buildSrc is a build of its own, and cannot use its own DependencyLocking to build itself: this is
// the same STRICT locking setup, inline (#1752). Keep the two in step.
buildscript {
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
    }
    dependencyLocking {
        lockMode.set(LockMode.STRICT)
    }
}

plugins {
    `kotlin-dsl`
}

/**
 * Configurations which cannot be locked, by name, each with the reason why - the same list as
 * DependencyLocking.EXCLUDED_CONFIGURATIONS, restricted to what exists in this build. Empty so far:
 * see that list for what was checked.
 */
val excludedFromLocking: Map<String, String> = mapOf()

dependencyLocking {
    lockAllConfigurations()
    lockMode.set(LockMode.STRICT)
}

configurations.matching { it.name in excludedFromLocking }.configureEach {
    resolutionStrategy.deactivateDependencyLocking()
}

tasks.register("resolveAndLockAll") {
    group = "help"
    description = "Resolves every resolvable configuration; run with --write-locks to write the lockfiles."
    notCompatibleWithConfigurationCache("Resolves the project's configurations at execution time")
    doFirst {
        require(gradle.startParameter.isWriteDependencyLocks) {
            "resolveAndLockAll only makes sense with --write-locks"
        }
    }
    doLast {
        configurations
            .filter { it.isCanBeResolved && it.name !in excludedFromLocking }
            .forEach { it.resolve() }
    }
}

gradlePlugin {
    plugins {
        create("ontrackVersioning") {
            id = "net.nemerosa.ontrack.versioning"
            implementationClass = "net.nemerosa.ontrack.build.OntrackVersioningPlugin"
            displayName = "Ontrack Versioning Plugin"
            description = "Computes project version from git & VERSION file and registers writeVersion task"
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    // KeycloakRealmsTest reads the realms: an edit to one of them must re-run the tests
    inputs.dir("../compose/keycloak/import")
        .withPropertyName("keycloakRealms")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
