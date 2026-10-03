package net.nemerosa.ontrack.extension.audittrail.security

import net.nemerosa.ontrack.extension.audittrail.AbstractAuditTrailITSupport
import net.nemerosa.ontrack.model.structure.Build
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * `auditTrail/view` on a build, for the UI to know whether to offer its audit trail page: the
 * build has a trail to read — the licence is on, or entries were written before it lapsed.
 */
class BuildAuditTrailAuthorizationIT : AbstractAuditTrailITSupport() {

    /**
     * The `auditTrail/view` authorization of the build, as a user who can only see it.
     */
    private fun Build.canViewAuditTrail(): Boolean? {
        var result: Boolean? = null
        asUserWithView(this).call {
            run(
                """
                    {
                        build(id: ${id()}) {
                            authorizations { name action authorized }
                        }
                    }
                """
            ) { data ->
                result = data.path("build").path("authorizations")
                    .firstOrNull {
                        it.path("name").asString() == BuildAuditTrailAuthorizationContributor.AUDIT_TRAIL &&
                                it.path("action").asString() == "view"
                    }
                    ?.path("authorized")?.asBoolean()
            }
        }
        return result
    }

    @Test
    fun `The trail of a build is viewable while the licence is on`() {
        asAdmin {
            project {
                branch {
                    build {
                        assertEquals(true, canViewAuditTrail())
                    }
                }
            }
        }
    }

    @Test
    fun `The trail of a build is viewable after the licence lapsed when it has entries`() {
        asAdmin {
            project {
                branch {
                    build {
                        withoutTrail {
                            assertEquals(true, canViewAuditTrail())
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `A build has no trail to view while the licence is off and it has no entry`() {
        asAdmin {
            project {
                branch {
                    untrailedBuild {
                        withoutTrail {
                            assertEquals(false, canViewAuditTrail())
                        }
                    }
                }
            }
        }
    }
}
