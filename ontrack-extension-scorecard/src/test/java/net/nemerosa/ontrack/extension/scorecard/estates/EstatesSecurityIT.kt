package net.nemerosa.ontrack.extension.scorecard.estates

import net.nemerosa.ontrack.extension.scorecard.security.EstateManagement
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.labels.LabelManagement
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.AccessDeniedException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EstatesSecurityIT : EstatesTestSupport() {

    @Test
    fun `EstateManagement is granted to the built-in roles holding LabelManagement`() {
        Roles.GLOBAL_ROLES.forEach { id ->
            val role = rolesService.getGlobalRole(id).orElseThrow()
            assertEquals(
                role.isGlobalFunctionGranted(LabelManagement::class.java),
                role.isGlobalFunctionGranted(EstateManagement::class.java),
                "EstateManagement for $id"
            )
        }
        // As of today
        assertTrue(rolesService.getGlobalRole(Roles.GLOBAL_ADMINISTRATOR).orElseThrow().isGlobalFunctionGranted(EstateManagement::class.java))
        assertTrue(rolesService.getGlobalRole(Roles.GLOBAL_CREATOR).orElseThrow().isGlobalFunctionGranted(EstateManagement::class.java))
    }

    @Test
    @AsAdminTest
    fun `A creator manages the estates`() {
        val a = asAdmin { label() }
        asGlobalRole(Roles.GLOBAL_CREATOR) {
            val estate = estateService.create(EstateInput(name = uid("E"), labels = listOf(a.getDisplay())))
            estateService.update(estate.id, EstateInput(name = estate.name, description = "Updated", labels = listOf(a.getDisplay())))
            estateService.delete(estate.id)
        }
    }

    @Test
    @AsAdminTest
    fun `Roles without LabelManagement cannot manage the estates, but can read them`() {
        val a = asAdmin { label() }
        val estate = estate(a)
        listOf(
            Roles.GLOBAL_AUTOMATION,
            Roles.GLOBAL_CONTROLLER,
            Roles.GLOBAL_VALIDATION_MANAGER,
            Roles.GLOBAL_PARTICIPANT,
            Roles.GLOBAL_READ_ONLY,
        ).forEach { role ->
            asGlobalRole(role) {
                assertFailsWith<AccessDeniedException>("Creation denied to $role") {
                    estateService.create(EstateInput(name = uid("E"), labels = listOf(a.getDisplay())))
                }
                assertFailsWith<AccessDeniedException>("Update denied to $role") {
                    estateService.update(estate.id, EstateInput(name = estate.name, labels = listOf(a.getDisplay())))
                }
                assertFailsWith<AccessDeniedException>("Deletion denied to $role") {
                    estateService.delete(estate.id)
                }
                assertFailsWith<AccessDeniedException>("Recompute denied to $role") {
                    estateService.recompute(estate)
                }
                assertEquals(estate.name, estateService.findByName(estate.name)?.name, "Read by $role")
                assertTrue(estateService.findAll().any { it.id == estate.id }, "Listed for $role")
            }
        }
    }

    @Test
    fun `The projects of an estate are filtered by the right to see them`() {
        val a = asAdmin { label() }
        val visible = asAdmin { project().apply { labels = listOf(a) } }
        asAdmin { project().apply { labels = listOf(a) } }
        val estate = estate(a)
        withNoGrantViewToAll {
            asUserWithView(visible).call {
                assertEquals(listOf(visible.name), estateService.getProjects(estate).map { it.name })
            }
        }
    }
}
