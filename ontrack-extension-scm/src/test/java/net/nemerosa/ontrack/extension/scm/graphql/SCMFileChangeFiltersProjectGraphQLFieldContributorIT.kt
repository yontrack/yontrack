package net.nemerosa.ontrack.extension.scm.graphql

import net.nemerosa.ontrack.extension.scm.model.SCMFileChangeFilter
import net.nemerosa.ontrack.extension.scm.service.SCMFileChangeFilterService
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.getRequiredBooleanField
import net.nemerosa.ontrack.json.getRequiredTextField
import net.nemerosa.ontrack.model.security.ProjectConfig
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

class SCMFileChangeFiltersProjectGraphQLFieldContributorIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var scmFileChangeFilterService: SCMFileChangeFilterService

    @Test
    @AsAdminTest
    fun `Getting the list of filters with full authorizations`() {
        project {
            val filterName = uid("f_")
            val filter = SCMFileChangeFilter(filterName, listOf("some-dir/**"))
            scmFileChangeFilterService.save(this, filter)
            asUser().withProjectFunction(this, ProjectConfig::class.java).call {
                run(
                    """
                    {
                        projects(id: $id) {
                            scmFileChangeFilters {
                                canManage
                                filters {
                                    name
                                    patterns
                                }
                            }
                        }
                    }
                """
                ) { data ->
                    val project = data.path("projects").path(0)
                    val scmFileChangeFilters = project.path("scmFileChangeFilters")
                    assertEquals(true, scmFileChangeFilters.getRequiredBooleanField("canManage"))
                    val filters = scmFileChangeFilters.path("filters")
                    assertEquals(1, filters.size())
                    val filterNode = filters.path(0)
                    assertEquals(filterName, filterNode.getRequiredTextField("name"))
                    assertEquals(listOf("some-dir/**"), filterNode.path("patterns").values().map { it.asText() })
                }
            }
        }
    }

    @Test
    @AsAdminTest
    fun `Getting the list of filters with no authorizations`() {
        project {
            val filterName = uid("f_")
            val filter = SCMFileChangeFilter(filterName, listOf("some-dir/**"))
            scmFileChangeFilterService.save(this, filter)
            asUser().withView(this).call {
                run(
                    """
                    {
                        projects(id: $id) {
                            scmFileChangeFilters {
                                canManage
                                filters {
                                    name
                                    patterns
                                }
                            }
                        }
                    }
                """
                ) { data ->
                    val project = data.path("projects").path(0)
                    val scmFileChangeFilters = project.path("scmFileChangeFilters")
                    assertEquals(false, scmFileChangeFilters.getRequiredBooleanField("canManage"))
                    val filters = scmFileChangeFilters.path("filters")
                    assertEquals(1, filters.size())
                    val filterNode = filters.path(0)
                    assertEquals(filterName, filterNode.getRequiredTextField("name"))
                    assertEquals(listOf("some-dir/**"), filterNode.path("patterns").values().map { it.asText() })
                }
            }
        }
    }

}