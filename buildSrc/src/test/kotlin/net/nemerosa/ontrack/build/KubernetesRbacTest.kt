package net.nemerosa.ontrack.build

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Kubernetes acceptance stack (#2172) installs the RBAC that the user documentation tells an
 * operator to install, so that the acceptance test proves the documented permissions are enough. The
 * manifest `compose/kubernetes/manifests/rbac.yaml` is that documented block, with the namespace of
 * the Role and RoleBinding as the only difference: the stack runs the Jobs in `yontrack`, where the
 * documentation's example uses `yontrack-jobs`.
 */
class KubernetesRbacTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "compose/docker-compose-it.yml").isFile }
            ?: error("compose/docker-compose-it.yml not found above ${File("").absolutePath}")
    }

    /** The first yaml block following the "RBAC" heading of the documentation page. */
    private fun documentedRbac(): String {
        val lines = File(root, "ontrack-docs/docs/content/integrations/auto-versioning/kubernetes.md").readLines()
        val heading = lines.indexOfFirst { it.trim() == "## RBAC" }
        assertTrue(heading >= 0, "no `## RBAC` section in the Kubernetes documentation")
        val start = lines.drop(heading).indexOfFirst { it.trim() == "```yaml" }
        assertTrue(start >= 0, "no yaml block in the RBAC section")
        val block = lines.drop(heading + start + 1).takeWhile { it.trim() != "```" }
        return normalise(block.joinToString("\n"))
    }

    private fun manifest(): String =
        normalise(
            File(root, "compose/kubernetes/manifests/rbac.yaml").readLines()
                // The comment header explaining where the manifest comes from
                .dropWhile { it.startsWith("#") }
                .joinToString("\n")
        )

    private fun normalise(yaml: String): String =
        yaml.lines().joinToString("\n") { it.trimEnd() }.trim()

    @Test
    fun `the documented RBAC has a Role and a RoleBinding in the namespace of the example`() {
        val documented = documentedRbac()
        assertTrue(documented.contains("kind: Role\n"), "no Role in the documented RBAC")
        assertTrue(documented.contains("kind: RoleBinding\n"), "no RoleBinding in the documented RBAC")
        assertTrue(documented.contains("namespace: yontrack-jobs"), "the documented namespace of the Jobs changed")
    }

    @Test
    fun `the manifest of the acceptance stack is the documented RBAC in the namespace yontrack`() {
        val expected = documentedRbac().replace("namespace: yontrack-jobs", "namespace: yontrack")
        assertEquals(
            expected,
            manifest(),
            "compose/kubernetes/manifests/rbac.yaml must be the RBAC of the documentation, namespace aside",
        )
    }
}
