package net.nemerosa.ontrack.build

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.LockMode

/**
 * Gradle dependency locking, in STRICT mode, for one project of the build (#1752).
 *
 * * every configuration is locked into the project's `gradle.lockfile`, but for the
 *   [EXCLUDED_CONFIGURATIONS];
 * * the plugin classpath of a subproject is locked into its `buildscript-gradle.lockfile`. The
 *   root project's cannot be set up from here - its plugins are resolved before its build script
 *   body runs - so the root build script does it in its own `buildscript {}` block;
 * * `resolveAndLockAll` resolves every resolvable configuration, which is what writes their lock
 *   state when run with `--write-locks`. `./gradlew dependencies --write-locks` alone would only
 *   cover the configurations of the root project.
 *
 * `buildSrc` is a build of its own and cannot use this class to build itself: its build script
 * carries the same setup inline.
 */
object DependencyLocking {

    const val RESOLVE_AND_LOCK_ALL = "resolveAndLockAll"

    /**
     * Configurations which cannot be locked, by name, each with the reason why. Locking is
     * deactivated on them, and `resolveAndLockAll` does not resolve them. Nothing else is
     * excluded.
     *
     * Empty so far. The Kotlin plugin configurations known to break STRICT mode
     * (gradle/gradle#25758), `kotlinScriptDefExtensions` and `testKotlinScriptDefExtensions`, were
     * checked with Gradle 8.13 and Kotlin 2.2.20: they lock fine, as `empty=` entries, and the
     * build passes. Add an entry here only for a configuration that demonstrably cannot be locked.
     */
    val EXCLUDED_CONFIGURATIONS: Map<String, String> = mapOf()

    fun configure(project: Project) {
        project.dependencyLocking {
            lockAllConfigurations()
            lockMode.set(LockMode.STRICT)
        }
        project.configurations.matching { it.name in EXCLUDED_CONFIGURATIONS }.configureEach {
            resolutionStrategy.deactivateDependencyLocking()
        }

        if (project != project.rootProject) {
            project.buildscript.configurations.named("classpath") {
                resolutionStrategy.activateDependencyLocking()
            }
            project.buildscript.dependencyLocking.lockMode.set(LockMode.STRICT)
        }

        project.tasks.register(RESOLVE_AND_LOCK_ALL) {
            group = "help"
            description = "Resolves every resolvable configuration; run with --write-locks to write the lockfiles."
            notCompatibleWithConfigurationCache("Resolves the project's configurations at execution time")
            doFirst {
                require(project.gradle.startParameter.isWriteDependencyLocks) {
                    "$RESOLVE_AND_LOCK_ALL only makes sense with --write-locks"
                }
            }
            doLast {
                project.configurations
                    .filter { it.isCanBeResolved && it.name !in EXCLUDED_CONFIGURATIONS }
                    .forEach { it.resolve() }
            }
        }
    }
}
