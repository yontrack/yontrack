package net.nemerosa.ontrack.kdsl.acceptance.tests.kubernetes

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.kdsl.acceptance.tests.ACCProperties
import net.nemerosa.ontrack.kdsl.acceptance.tests.av.AbstractACCAutoVersioningTestSupport
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.waitUntil
import net.nemerosa.ontrack.kdsl.spec.extension.av.AutoVersioningAuditEntry
import net.nemerosa.ontrack.kdsl.spec.extension.av.AutoVersioningSourceConfig
import net.nemerosa.ontrack.kdsl.spec.extension.av.autoVersioning
import net.nemerosa.ontrack.kdsl.spec.extension.av.setAutoVersioningConfig
import net.nemerosa.ontrack.kdsl.spec.extension.scm.withMockScmRepository
import net.nemerosa.ontrack.kdsl.spec.Project
import kotlin.test.fail

/**
 * Support for the acceptance tests of the `kubernetes` post-processing (#2172), run by
 * `kdslKubernetesTest` against a Yontrack, a k3s cluster and a Git server in it.
 *
 * Everything is observed through Yontrack (the auto-versioning audit) and Git (the upgrade branch
 * on the Git server): never through the Kubernetes API.
 */
abstract class AbstractACCKubernetesTestSupport : AbstractACCAutoVersioningTestSupport() {

    /**
     * Runs one auto-versioning order, post-processed by the Job [dockerCommand] runs in, and gives
     * what it produced to [code].
     *
     * The mock repository holds `gradle.properties` at `some-version = 1.0.0`, and a dependency
     * promotes 2.0.0.
     *
     * @param expectedState State the audit entry of the order must reach
     */
    protected fun runPostProcessing(
        dockerCommand: String,
        expectedState: String,
        code: (Outcome) -> Unit,
    ) {
        withMockScmRepository(ontrack, prefix = "ontrack-kubernetes-test") {
            withAutoVersioning {
                repositoryFile("gradle.properties") { "some-version = 1.0.0" }
                val dependency = branchWithPromotion(promotion = "RELEASE")
                project {
                    branch {
                        configuredForMockRepository()
                        setAutoVersioningConfig(
                            listOf(
                                AutoVersioningSourceConfig(
                                    sourceProject = dependency.project.name,
                                    sourceBranch = dependency.name,
                                    sourcePromotion = "RELEASE",
                                    targetPath = "gradle.properties",
                                    targetProperty = "some-version",
                                    upgradeBranchPattern = UPGRADE_BRANCH,
                                    postProcessing = "kubernetes",
                                    postProcessingConfig = mapOf(
                                        "dockerImage" to ALLOWED_IMAGE,
                                        "dockerCommand" to dockerCommand,
                                        "secrets" to listOf(
                                            mapOf(
                                                "secret" to ALLOWED_SECRET,
                                                "key" to "token",
                                                "env" to "PP_TOKEN",
                                            )
                                        ),
                                    ).asJson(),
                                )
                            )
                        )
                        val target = this
                        dependency.apply {
                            build(name = "2.0.0") { promote("RELEASE") }
                        }

                        val entry = waitForEntry(target.project, expectedState)
                        code(Outcome(entry, git(repository)))
                    }
                }
            }
        }
    }

    /**
     * Waits for the audit entry of the target project to reach [state], with the time a first run
     * needs to pull the images in the cluster.
     */
    private fun waitForEntry(project: Project, state: String): AutoVersioningAuditEntry {
        var last: AutoVersioningAuditEntry? = null
        waitUntil(
            task = "The auto-versioning of ${project.name} reaches $state",
            timeout = COMPLETION_TIMEOUT,
            interval = 2_000L,
            onTimeout = { println("Last audit entry: $last") },
        ) {
            last = ontrack.autoVersioning.audit.entries(project = project.name, size = 1).firstOrNull()
            last?.mostRecentState?.state == state
        }
        return last ?: fail("No audit entry")
    }

    private fun git(repository: String) = KubernetesGitRepository(
        baseUrl = ACCProperties.Kubernetes.Git.url,
        organisation = GIT_ORGANISATION,
        repository = repository,
        username = GIT_USERNAME,
        password = GIT_PASSWORD,
    )

    /**
     * @property entry Audit entry of the order, in its final state
     * @property repository The repository on the Git server
     */
    class Outcome(
        val entry: AutoVersioningAuditEntry,
        val repository: KubernetesGitRepository,
    )

    companion object {

        /** Name of the upgrade branch, the same for all the tests: their repositories differ */
        const val UPGRADE_BRANCH = "feature/upgrade-kubernetes-<version>"

        /** What the upgrade branch of 2.0.0 starts with: its name ends with a suffix */
        const val UPGRADE_BRANCH_2_0_0 = "feature/upgrade-kubernetes-2.0.0"

        /** The only image the stack allows, see `ONTRACK_EXTENSION_KUBERNETES_ALLOWED_IMAGES` */
        const val ALLOWED_IMAGE = "busybox:1.37.0"

        /** The only Secret the stack allows, created by `compose/kubernetes/bootstrap.sh` */
        const val ALLOWED_SECRET = "post-processing-secret"

        /** Value of its `token` key, as created by `compose/kubernetes/bootstrap.sh` */
        const val ALLOWED_SECRET_VALUE = "secret-value-2172"

        // The Git server of the stack, as `compose/docker-compose-kdsl-kubernetes.yml` and
        // `compose/kubernetes/bootstrap.sh` set it up. The URL is the one of the port the stack
        // got, see ACCProperties.Kubernetes.Git.
        const val GIT_ORGANISATION = "yontrack"
        const val GIT_USERNAME = "yontrack"
        const val GIT_PASSWORD = "yontrack-git-secret"

        /**
         * A first run pulls the image of the harness and the one of the post-processing in the
         * cluster: 3 minutes.
         */
        const val COMPLETION_TIMEOUT = 180_000L
    }
}
