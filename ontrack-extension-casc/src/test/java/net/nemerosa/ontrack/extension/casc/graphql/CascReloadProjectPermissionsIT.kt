package net.nemerosa.ontrack.extension.casc.graphql

import net.nemerosa.ontrack.extension.casc.CascConfigurationProperties
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.model.security.AccountGroup
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.nio.file.Files
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What the demo relies on: `ontrack-demo-seed` deletes and recreates every project on each
 * `demo-smoke` reset, and a project's permissions go with the deleted project. The DAST
 * `scan-project` account holds its role through CasC `project-permissions`, so without a reload
 * after the seed it has no project at all from the first reset onwards.
 *
 * `.github/workflows/demo-smoke.yml` calls the `reloadCasc` mutation right after the seed
 * (`scripts/demo-smoke.sh casc`). This pins the two things that step depends on: that the
 * mutation restores a project permission lost with a deleted project, and that running it again
 * changes nothing - it runs on every `main` BRONZE deployment.
 *
 * See issue #1768 and `security/dast/casc.yaml`.
 */
class CascReloadProjectPermissionsIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var cascConfigurationProperties: CascConfigurationProperties

    @Test
    fun `Reloading the CasC restores a project permission lost with the project`() {
        asAdmin {
            val group = doCreateAccountGroup()
            val projectName = uid("p")
            project(projectName)

            withCascLocation(group, projectName) {
                // The permission is granted by the first load
                reloadCasc()
                assertEquals(
                    Roles.PROJECT_PARTICIPANT,
                    projectRole(group, projectName),
                    "The CasC granted the role"
                )

                // What the demo seed does: the project is deleted and recreated under the same name
                deleteProjectByName(projectName)
                project(projectName)
                assertNull(
                    projectRole(group, projectName),
                    "The recreated project has lost the permission - this is what #1768 is about"
                )

                // What demo-smoke.yml now does right after the seed
                reloadCasc()
                assertEquals(
                    Roles.PROJECT_PARTICIPANT,
                    projectRole(group, projectName),
                    "The reload put the role back"
                )
            }
        }
    }

    @Test
    fun `Reloading the CasC twice is idempotent`() {
        asAdmin {
            val group = doCreateAccountGroup()
            val projectName = uid("p")
            project(projectName)

            withCascLocation(group, projectName) {
                reloadCasc()
                reloadCasc()
                // Exactly one association, not two, and still the declared role
                val associations = accountService.getProjectPermissionsForAccountGroup(group)
                assertEquals(1, associations.size, "One permission after two reloads")
                assertEquals(Roles.PROJECT_PARTICIPANT, associations.first().projectRole.id)
            }
        }
    }

    /**
     * Writes the `project-permissions` fragment to a file and points the CasC locations at it for
     * the duration of [code], restoring them afterwards - the configuration properties are a
     * shared singleton.
     */
    private fun withCascLocation(group: AccountGroup, projectName: String, code: () -> Unit) {
        val file = Files.createTempFile("casc-project-permissions", ".yaml")
        val previousLocations = cascConfigurationProperties.locations
        try {
            file.writeText(
                """
                    ontrack:
                        admin:
                            project-permissions:
                                - group: ${group.name}
                                  role: ${Roles.PROJECT_PARTICIPANT}
                                  projects:
                                    - $projectName
                """.trimIndent()
            )
            cascConfigurationProperties.locations = listOf("file:$file")
            code()
        } finally {
            cascConfigurationProperties.locations = previousLocations
            file.deleteIfExists()
        }
    }

    /**
     * Runs the mutation `scripts/demo-smoke.sh casc` sends to the demo, verbatim.
     */
    private fun reloadCasc() {
        val data = run(
            """
                mutation {
                    reloadCasc {
                        errors {
                            message
                        }
                    }
                }
            """
        )
        assertNoUserError(data, "reloadCasc")
    }

    /**
     * The role [group] holds on the project named [projectName], or null when it holds none.
     */
    private fun projectRole(group: AccountGroup, projectName: String): String? {
        val project = projectByName(projectName)
        return accountService.getProjectPermissionsForAccountGroup(group)
            .firstOrNull { it.projectId == project.id() }
            ?.projectRole?.id
    }

    private fun projectByName(name: String): Project =
        structureService.findProjectByNameIfAuthorized(name)
            ?: error("Cannot find project $name")

    private fun deleteProjectByName(name: String) {
        structureService.deleteProject(projectByName(name).id)
    }
}
