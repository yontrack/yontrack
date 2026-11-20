package net.nemerosa.ontrack.extension.av.security

import net.nemerosa.ontrack.extension.av.AbstractAutoVersioningTestSupport
import net.nemerosa.ontrack.extension.av.AutoVersioningTestFixtures.createOrder
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditState
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditStore
import net.nemerosa.ontrack.model.security.Roles
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

/**
 * The `reschedule` authorization of an audit entry, for the UI to show the *Reschedule* button.
 */
class AutoVersioningAuditEntryAuthorizationIT : AbstractAutoVersioningTestSupport() {

    @Autowired
    private lateinit var store: AutoVersioningAuditStore

    private fun rescheduleAuthorization(projectRole: String?, globalRole: String? = null): Boolean =
        asAdmin {
            val source = project()
            project<Boolean> {
                branch<Boolean> {
                    val order = createOrder(sourceProject = source.name).apply {
                        store.create(this)
                        store.addState(
                            uuid = uuid,
                            targetBranch = this@branch,
                            state = AutoVersioningAuditState.THROTTLED,
                        )
                    }
                    val query: () -> Boolean = {
                        run(
                            """
                                {
                                    autoVersioningAuditEntries(filter: {uuid: "${order.uuid}"}) {
                                        pageItems {
                                            authorizations {
                                                name
                                                action
                                                authorized
                                            }
                                        }
                                    }
                                }
                            """
                        ).path("autoVersioningAuditEntries").path("pageItems").single()
                            .path("authorizations")
                            .single {
                                it.path("name").asText() == AutoVersioningAuditEntryAuthorizationContributor.AUTO_VERSIONING_AUDIT_ENTRY &&
                                        it.path("action").asText() == "reschedule"
                            }
                            .path("authorized").asBoolean()
                    }
                    when {
                        projectRole != null -> asAccountWithProjectRole(projectRole, query)
                        globalRole != null -> asAccountWithGlobalRole(globalRole, query)
                        else -> asUser(code = query)
                    }
                }
            }
        }

    @Test
    fun `Reschedule authorized for an auto-versioning controller of the target project`() {
        assertEquals(true, rescheduleAuthorization(AutoVersioningRoleContributor.AUTO_VERSIONING_CONTROLLER))
    }

    @Test
    fun `Reschedule authorized for an administrator`() {
        assertEquals(true, rescheduleAuthorization(projectRole = null, globalRole = Roles.GLOBAL_ADMINISTRATOR))
    }

    @Test
    fun `Reschedule not authorized for a participant of the target project`() {
        assertEquals(false, rescheduleAuthorization(Roles.PROJECT_PARTICIPANT))
    }

    @Test
    fun `Reschedule not authorized for a user without any role`() {
        assertEquals(false, rescheduleAuthorization(projectRole = null))
    }

}
