package net.nemerosa.ontrack.service

import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.exceptions.ValidationStampFilterNameAlreadyDefinedException
import net.nemerosa.ontrack.model.exceptions.ValidationStampFilterNotFoundException
import net.nemerosa.ontrack.model.security.GlobalSettings
import net.nemerosa.ontrack.model.security.ValidationStampFilterCreate
import net.nemerosa.ontrack.model.security.ValidationStampFilterMgt
import net.nemerosa.ontrack.model.security.ValidationStampFilterShare
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.ValidationStampFilter
import net.nemerosa.ontrack.model.structure.ValidationStampFilterService
import net.nemerosa.ontrack.test.TestUtils.uid
import org.apache.commons.lang3.Strings
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.AccessDeniedException
import kotlin.test.*

@AsAdminTest
class ValidationStampFilterServiceIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var filterService: ValidationStampFilterService

    private lateinit var branch: Branch

    @BeforeEach
    fun setup() {
        branch = doCreateBranch()
    }

    /**
     * Regression test for #628
     */
    @Test
    fun `Deleting a validation stamp used by validation stamp filter`() {
        project {
            branch {
                // Creates a validation stamp
                val vs = validationStamp()
                // Creates a build
                build {
                    // Validates this build
                    validate(vs)
                }
                // Creates a validation stamp filter containing the validation stamp for this branch
                val vsf = validationStampFilter(branch = this, vsNames = listOf(vs.name))
                // Makes sure we can download all filters for this branch
                val filters: List<ValidationStampFilter> = filterService.getBranchValidationStampFilters(this, false)
                assertEquals(
                    listOf(vsf.name),
                    filters.map { it.name }
                )
                assertEquals(
                    listOf(listOf(vs.name)),
                    filters.map { it.vsNames }
                )
                // Now, deletes the validation stamp
                asAdmin().execute {
                    structureService.deleteValidationStamp(vs.id)
                }
                // Makes sure we can download all filters for this branch
                val newFilters: List<ValidationStampFilter> = filterService.getBranchValidationStampFilters(this, false)
                assertEquals(
                    listOf(vsf.name),
                    newFilters.map { it.name }
                )
                // ... and that the name is NOT gone from the list
                assertEquals(
                    listOf(listOf(vs.name)),
                    filters.map { it.vsNames }
                )
            }
        }
    }

    @Test
    fun new_global_filter() {
        asUser().with(GlobalSettings::class.java).execute {
            val filter = filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = "My filter",
                    vsNames = listOf("CI")
                )
            )
            assertTrue(filter.id.isSet)
        }
    }

    @Test
    fun new_global_filter_with_existing_name() {
        asUser().with(GlobalSettings::class.java).execute {
            filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = "My filter",
                    vsNames = listOf("CI")
                )
            )
            // Creates another filter with the same name
            assertFailsWith<ValidationStampFilterNameAlreadyDefinedException> {
                filterService.newValidationStampFilter(
                    ValidationStampFilter(
                        name = "My filter",
                        vsNames = listOf("OTHER")
                    )
                )
            }
        }
    }

    @Test
    fun new_filter_with_both_project_and_branch() {
        asUser().withProjectFunction(branch, ValidationStampFilterMgt::class.java).execute {
            assertFailsWith<IllegalStateException> {
                filterService.newValidationStampFilter(
                    ValidationStampFilter(
                        name = "My filter",
                        project = branch.project,
                        branch = branch,
                        vsNames = listOf("CI")
                    )
                )
            }
        }
    }

    @Test
    fun new_project_filter() {
        asUser().withProjectFunction(branch, ValidationStampFilterMgt::class.java).execute {
            val filter = filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = "My filter",
                    project = branch.project,
                    vsNames = listOf("CI")
                )
            )
            assertTrue(filter.id.isSet)
        }
    }

    @Test
    fun new_project_filter_with_existing_name() {
        asUser().withProjectFunction(branch, ValidationStampFilterMgt::class.java).execute {
            filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = "My filter",
                    project = branch.project,
                    vsNames = listOf("CI")
                )
            )
            // Creates another filter with the same name
            assertFailsWith<ValidationStampFilterNameAlreadyDefinedException> {
                filterService.newValidationStampFilter(
                    ValidationStampFilter(
                        name = "My filter",
                        project = branch.project,
                        vsNames = listOf("OTHER")
                    )
                )
            }
        }
    }

    @Test
    fun new_project_filter_with_same_name_than_global() {
        asUser().with(GlobalSettings::class.java).execute {
            filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = "My filter",
                    vsNames = listOf("CI")
                )
            )
        }
        asUser().withProjectFunction(branch, ValidationStampFilterMgt::class.java).execute {
            val filter = filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = "My filter",
                    project = branch.project,
                    vsNames = listOf("CI")
                )
            )
            assertTrue(filter.id.isSet)
        }
    }

    @Test
    fun new_branch_filter() {
        asUser().withProjectFunction(branch, ValidationStampFilterCreate::class.java).execute {
            val filter = filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = "My filter",
                    branch = branch,
                    vsNames = listOf("CI")
                )
            )
            assertTrue(filter.id.isSet)
        }
    }

    @Test
    fun new_branch_filter_with_existing_name() {
        asUser().withProjectFunction(branch, ValidationStampFilterCreate::class.java).execute {
            filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = "My filter",
                    branch = branch,
                    vsNames = listOf("CI")
                )
            )
            // Creates another filter with the same name
            assertFailsWith<ValidationStampFilterNameAlreadyDefinedException> {
                filterService.newValidationStampFilter(
                    ValidationStampFilter(
                        name = "My filter",
                        branch = branch,
                        vsNames = listOf("OTHER")
                    )
                )
            }
        }
    }

    @Test
    fun new_branch_filter_with_same_name_than_global() {
        asUser().with(GlobalSettings::class.java).execute {
            filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = "My filter",
                    vsNames = listOf("CI")
                )
            )
        }
        asUser().withProjectFunction(branch, ValidationStampFilterCreate::class.java).execute {
            val filter = filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = "My filter",
                    branch = branch,
                    vsNames = listOf("CI")
                )
            )
            assertTrue(filter.id.isSet)
        }
    }

    @Test
    fun new_branch_filter_with_same_name_than_project() {
        asUser().withProjectFunction(branch, ValidationStampFilterMgt::class.java).execute {
            filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = "My filter",
                    project = branch.project,
                    vsNames = listOf("CI")
                )
            )
        }
        asUser().withProjectFunction(branch, ValidationStampFilterCreate::class.java).execute {
            val filter = filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = "My filter",
                    branch = branch,
                    vsNames = listOf("CI")
                )
            )
            assertTrue(filter.id.isSet)
        }
    }

    private fun createFilters(): String {
        val name = uid("F")
        asUser().with(GlobalSettings::class.java).call {
            // Global
            filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = name,
                    vsNames = listOf("GLOBAL")
                )
            )
        }
        asUser().withProjectFunction(branch, ValidationStampFilterMgt::class.java).call {
            // Project
            filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = name,
                    project = branch.project,
                    vsNames = listOf("PROJECT")
                )
            )
        }
        asUser().withProjectFunction(branch, ValidationStampFilterCreate::class.java).call {
            // Branch
            filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = name,
                    branch = branch,
                    vsNames = listOf("BRANCH")
                )
            )
        }
        // OK
        return name
    }

    @Test
    fun global_filters() {
        val name = createFilters()
        asUser().with(GlobalSettings::class.java).execute {
            val list = filterService.globalValidationStampFilters
                .filter { f -> Strings.CS.equals(name, f.name) }
            assertEquals(1, list.size.toLong())
            assertEquals(name, list[0].name)
            assertEquals(listOf("GLOBAL"), list[0].vsNames)
        }
    }

    @Test
    fun project_filters_only() {
        val name = createFilters()
        val list = asUser().withView(branch).call {
            filterService.getProjectValidationStampFilters(branch.project, false)
                .filter { f -> Strings.CS.equals(name, f.name) }
        }
        assertEquals(1, list.size.toLong())
        assertEquals(name, list[0].name)
        assertEquals(listOf("PROJECT"), list[0].vsNames)
    }

    @Test
    fun project_filters_include_all() {
        val name = createFilters()
        val list = asUser().withView(branch).call {
            filterService.getProjectValidationStampFilters(branch.project, true)
                .filter { f -> Strings.CS.equals(name, f.name) }
        }
        assertEquals(1, list.size.toLong())
        assertEquals(name, list[0].name)
        assertEquals(listOf("PROJECT"), list[0].vsNames)
    }

    @Test
    fun branch_filters_only() {
        val name = createFilters()
        val list = asUser().withView(branch).call {
            filterService.getBranchValidationStampFilters(branch, false)
                .filter { f -> Strings.CS.equals(name, f.name) }
        }
        assertEquals(1, list.size.toLong())
        assertEquals(name, list[0].name)
        assertEquals(listOf("BRANCH"), list[0].vsNames)
    }

    @Test
    fun branch_filters_include_all() {
        val name = createFilters()
        val list = asUser().withView(branch).call {
            filterService.getBranchValidationStampFilters(branch, true)
                .filter { f -> Strings.CS.equals(name, f.name) }
        }
        assertEquals(1, list.size.toLong())
        assertEquals(name, list[0].name)
        assertEquals(listOf("BRANCH"), list[0].vsNames)
    }

    @Test
    fun by_name_global_only() {
        val name = uid("F")
        asUser().with(GlobalSettings::class.java).execute {
            filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = name,
                    vsNames = listOf("GLOBAL")
                )
            )
        }
        val f =
            asUser().withView(branch).call { filterService.getValidationStampFilterByName(branch, name).orElse(null) }
        assertNotNull(f)
        assertEquals(name, f.name)
        assertEquals(listOf("GLOBAL"), f.vsNames)
    }

    @Test
    fun by_name_global_and_project() {
        val name = uid("F")
        asUser().with(GlobalSettings::class.java).execute {
            filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = name,
                    vsNames = listOf("GLOBAL")
                )
            )
        }
        asUser().withProjectFunction(branch, ValidationStampFilterMgt::class.java).execute {
            filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = name,
                    project = branch.project,
                    vsNames = listOf("PROJECT")
                )
            )
        }
        val f =
            asUser().withView(branch).call { filterService.getValidationStampFilterByName(branch, name).orElse(null) }
        assertNotNull(f)
        assertEquals(name, f.name)
        assertEquals(listOf("PROJECT"), f.vsNames)
    }

    @Test
    fun by_name_branch() {
        val name = uid("F")
        asUser().with(GlobalSettings::class.java).execute {
            filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = name,
                    vsNames = listOf("GLOBAL")
                )
            )
        }
        asUser().withProjectFunction(branch, ValidationStampFilterMgt::class.java).execute {
            filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = name,
                    project = branch.project,
                    vsNames = listOf("PROJECT")
                )
            )
        }
        asUser().withProjectFunction(branch, ValidationStampFilterCreate::class.java).execute {
            filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = name,
                    branch = branch,
                    vsNames = listOf("BRANCH")
                )
            )
        }
        val f =
            asUser().withView(branch).call { filterService.getValidationStampFilterByName(branch, name).orElse(null) }
        assertNotNull(f)
        assertEquals(name, f.name)
        assertEquals(listOf("BRANCH"), f.vsNames)
    }

    @Test
    fun by_name_none() {
        createFilters()
        val f = asUser().withView(branch)
            .call { filterService.getValidationStampFilterByName(branch, uid("FX")).orElse(null) }
        assertNull(f)
    }

    @Test
    fun save_global() {
        asUser().with(GlobalSettings::class.java).execute {
            var f = filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = uid("F"),
                    vsNames = listOf("GLOBAL")
                )
            )
            filterService.saveValidationStampFilter(f.withVsNames(listOf("GLOBAL", "ONTRACK")))
            f = filterService.getValidationStampFilter(f.id)
            assertEquals(listOf("GLOBAL", "ONTRACK"), f.vsNames)
        }
    }

    @Test
    fun save_project() {
        asUser().withProjectFunction(branch, ValidationStampFilterMgt::class.java).execute {
            var f = filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = uid("F"),
                    project = branch.project,
                    vsNames = listOf("PROJECT")
                )
            )
            filterService.saveValidationStampFilter(f.withVsNames(listOf("PROJECT", "ONTRACK")))
            f = filterService.getValidationStampFilter(f.id)
            assertEquals(listOf("PROJECT", "ONTRACK"), f.vsNames)
        }
    }

    @Test
    fun save_branch() {
        asUser().withProjectFunction(branch, ValidationStampFilterCreate::class.java).execute {
            var f = filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = uid("F"),
                    branch = branch,
                    vsNames = listOf("BRANCH")
                )
            )
            filterService.saveValidationStampFilter(f.withVsNames(listOf("BRANCH", "ONTRACK")))
            f = filterService.getValidationStampFilter(f.id)
            assertEquals(listOf("BRANCH", "ONTRACK"), f.vsNames)
        }
    }

    @Test
    fun delete() {
        asUser().with(GlobalSettings::class.java).execute {
            val f = filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = uid("F"),
                    vsNames = listOf("GLOBAL")
                )
            )
            filterService.deleteValidationStampFilter(f)
            // Checks it is gone
            try {
                filterService.getValidationStampFilter(f.id)
                fail("It should have been deleted")
            } catch (ex: ValidationStampFilterNotFoundException) {
                assertEquals(String.format("Validation stamp filter with ID %s not found", f.id), ex.message)
            }
        }
    }

    @Test
    fun share_from_branch_to_project() {
        val f = asUser().withProjectFunction(branch, ValidationStampFilterCreate::class.java).call {
            filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = uid("F"),
                    branch = branch,
                    vsNames = listOf("CI")
                )
            )
        }
        asUser().withProjectFunction(branch, ValidationStampFilterShare::class.java).execute {
            val f2 = filterService.shareValidationStampFilter(f, branch.project)
            assertEquals(f.id(), f2.id())
            assertNotNull(f2.project)
            assertNull(f2.branch)
        }
    }

    @Test
    fun share_from_branch_to_global() {
        val f = asUser().withProjectFunction(branch, ValidationStampFilterCreate::class.java).call {
            filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = uid("F"),
                    branch = branch,
                    vsNames = listOf("CI")
                )
            )
        }
        asUser().with(GlobalSettings::class.java).execute {
            val f2 = filterService.shareValidationStampFilter(f)
            assertEquals(f.id(), f2.id())
            assertNull(f2.project)
            assertNull(f2.branch)
        }
    }

    @Test
    fun share_from_project_to_global() {
        val f = asUser().withProjectFunction(branch, ValidationStampFilterMgt::class.java).call {
            filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = uid("F"),
                    project = branch.project,
                    vsNames = listOf("CI")
                )
            )
        }
        asUser().with(GlobalSettings::class.java).execute {
            val f2 = filterService.shareValidationStampFilter(f)
            assertEquals(f.id(), f2.id())
            assertNull(f2.project)
            assertNull(f2.branch)
        }
    }

    @Test
    fun sharing_from_branch_to_project_remove_branch_filter() {
        val f = asUser().withProjectFunction(branch, ValidationStampFilterCreate::class.java).call {
            filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = uid("F"),
                    branch = branch,
                    vsNames = listOf("BRANCH")
                )
            )
        }
        asUser().withProjectFunction(branch, ValidationStampFilterMgt::class.java).execute {
            // Shares at project level
            filterService.shareValidationStampFilter(f, branch.project)
        }
        // Gets filters for the branch
        val filters = asUserWithView(branch).call { filterService.getBranchValidationStampFilters(branch, false) }
        assertTrue(filters.isEmpty(), "Branch has no longer any filter")
    }

    @Test
    fun sharing_from_branch_to_global_remove_branch_filter() {
        val f = asUser().withProjectFunction(branch, ValidationStampFilterCreate::class.java).call {
            filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = uid("F"),
                    branch = branch,
                    vsNames = listOf("BRANCH")
                )
            )
        }
        asUser().with(GlobalSettings::class.java).execute {
            // Shares at global level
            filterService.shareValidationStampFilter(f)
        }
        // Gets filters for the branch
        val filters = asUserWithView(branch).call { filterService.getBranchValidationStampFilters(branch, false) }
        assertTrue(filters.isEmpty(), "Branch has no longer any filter")
    }

    @Test
    fun sharing_from_project_to_global_remove_project_filter() {
        val f = asUser().withProjectFunction(branch, ValidationStampFilterMgt::class.java).call {
            filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = uid("F"),
                    project = branch.project,
                    vsNames = listOf("PROJECT")
                )
            )
        }
        asUser().with(GlobalSettings::class.java).execute {
            // Shares at global level
            filterService.shareValidationStampFilter(f)
        }
        // Gets filters for the project
        val filters =
            asUserWithView(branch).call { filterService.getProjectValidationStampFilters(branch.project, false) }
        assertTrue(filters.isEmpty(), "Project has no longer any filter")
    }

    @Test
    fun `Cannot create a global filter when no right`() {
        asUserWithView(branch).execute {
            assertFailsWith<AccessDeniedException> {
                filterService.newValidationStampFilter(
                    ValidationStampFilter(
                        name = uid("F"),
                        vsNames = listOf("GLOBAL")
                    )
                )
            }
        }
    }

    @Test
    fun `Cannot create a global filter when only management right`() {
        asUser().withProjectFunction(branch, ValidationStampFilterMgt::class.java).execute {
            assertFailsWith<AccessDeniedException> {
                filterService.newValidationStampFilter(
                    ValidationStampFilter(
                        name = uid("F"),
                        vsNames = listOf("GLOBAL")
                    )
                )
            }
        }
    }

    @Test
    fun `Cannot create a global filter when only share right`() {
        asUser().withProjectFunction(branch, ValidationStampFilterShare::class.java).execute {
            assertFailsWith<AccessDeniedException> {
                filterService.newValidationStampFilter(
                    ValidationStampFilter(
                        name = uid("F"),
                        vsNames = listOf("GLOBAL")
                    )
                )
            }
        }
    }

    @Test
    fun `Cannot create a global filter when only create right`() {
        asUser().withProjectFunction(branch, ValidationStampFilterCreate::class.java).execute {
            assertFailsWith<AccessDeniedException> {
                filterService.newValidationStampFilter(
                    ValidationStampFilter(
                        name = uid("F"),
                        vsNames = listOf("GLOBAL")
                    )
                )
            }
        }
    }

    @Test
    fun `Can create a global filter`() {
        asUser().with(GlobalSettings::class.java).execute {
            filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = uid("F"),
                    vsNames = listOf("GLOBAL")
                )
            )
        }
    }

    @Test
    fun `Cannot create a project filter when no right`() {
        asUserWithView(branch).execute {
            assertFailsWith<AccessDeniedException> {
                filterService.newValidationStampFilter(
                    ValidationStampFilter(
                        name = uid("F"),
                        vsNames = listOf("PROJECT"),
                        project = branch.project
                    )
                )
            }
        }
    }

    @Test
    fun `Cannot create a project filter when only create right`() {
        asUser().withProjectFunction(branch, ValidationStampFilterCreate::class.java).execute {
            assertFailsWith<AccessDeniedException> {
                filterService.newValidationStampFilter(
                    ValidationStampFilter(
                        name = uid("F"),
                        vsNames = listOf("PROJECT"),
                        project = branch.project
                    )
                )
            }
        }
    }

    @Test
    fun `Cannot create a project filter when only share right`() {
        asUser().withProjectFunction(branch, ValidationStampFilterShare::class.java).execute {
            assertFailsWith<AccessDeniedException> {
                filterService.newValidationStampFilter(
                    ValidationStampFilter(
                        name = uid("F"),
                        vsNames = listOf("PROJECT"),
                        project = branch.project
                    )
                )
            }
        }
    }

    @Test
    fun `Can create a project filter`() {
        asUser().withProjectFunction(branch, ValidationStampFilterMgt::class.java).execute {
            filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = uid("F"),
                    vsNames = listOf("PROJECT"),
                    project = branch.project
                )
            )
        }
    }

    @Test
    fun `Cannot create a branch filter when no right`() {
        asUserWithView(branch).execute {
            assertFailsWith<AccessDeniedException> {
                filterService.newValidationStampFilter(
                    ValidationStampFilter(
                        name = uid("F"),
                        vsNames = listOf("PROJECT"),
                        branch = branch
                    )
                )
            }
        }
    }

    @Test
    fun `Can create a branch filter when share right`() {
        asUser().withProjectFunction(branch, ValidationStampFilterShare::class.java).execute {
            filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = uid("F"),
                    vsNames = listOf("PROJECT"),
                    branch = branch
                )
            )
        }
    }

    @Test
    fun `Can create a branch filter when mgt right`() {
        asUser().withProjectFunction(branch, ValidationStampFilterMgt::class.java).execute {
            filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = uid("F"),
                    vsNames = listOf("PROJECT"),
                    branch = branch
                )
            )
        }
    }

    @Test
    fun `Can create a branch filter`() {
        asUser().withProjectFunction(branch, ValidationStampFilterCreate::class.java).execute {
            filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = uid("F"),
                    vsNames = listOf("PROJECT"),
                    branch = branch
                )
            )
        }
    }

    @Test
    fun `Cannot share a project filter when no right`() {
        val f = asUser().withProjectFunction(branch, ValidationStampFilterCreate::class.java).call {
            filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = uid("F"),
                    vsNames = listOf("PROJECT"),
                    branch = branch
                )
            )
        }
        asUserWithView(branch).execute {
            assertFailsWith<AccessDeniedException> {
                filterService.shareValidationStampFilter(
                    f,
                    branch.project
                )
            }
        }
    }

    @Test
    fun `Cannot share a project filter when only create right`() {
        val f = asUser().withProjectFunction(branch, ValidationStampFilterCreate::class.java).call {
            filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = uid("F"),
                    vsNames = listOf("PROJECT"),
                    branch = branch
                )
            )
        }
        asUser().withProjectFunction(branch, ValidationStampFilterCreate::class.java).call {
            assertFailsWith<AccessDeniedException> {
                filterService.shareValidationStampFilter(
                    f,
                    branch.project
                )
            }
        }
    }

    @Test
    fun `Can share a project filter when mgt right`() {
        val f = asUser().withProjectFunction(branch, ValidationStampFilterCreate::class.java).call {
            filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = uid("F"),
                    vsNames = listOf("PROJECT"),
                    branch = branch
                )
            )
        }
        asUser().withProjectFunction(branch, ValidationStampFilterMgt::class.java).call {
            filterService.shareValidationStampFilter(
                f,
                branch.project
            )
        }
    }

    @Test
    fun `Can share a project filter when share right`() {
        val f = asUser().withProjectFunction(branch, ValidationStampFilterCreate::class.java).call {
            filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = uid("F"),
                    vsNames = listOf("PROJECT"),
                    branch = branch
                )
            )
        }
        asUser().withProjectFunction(branch, ValidationStampFilterShare::class.java).call {
            filterService.shareValidationStampFilter(
                f,
                branch.project
            )
        }
    }

    @Test
    fun `Cannot share a global filter when no right`() {
        val f = asUser().withProjectFunction(branch, ValidationStampFilterCreate::class.java).call {
            filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = uid("F"),
                    vsNames = listOf("PROJECT"),
                    branch = branch
                )
            )
        }
        asUserWithView(branch).execute {
            assertFailsWith<AccessDeniedException> {
                filterService.shareValidationStampFilter(
                    f
                )
            }
        }
    }

    @Test
    fun `Cannot share a global filter when only mgt right`() {
        val f = asUser().withProjectFunction(branch, ValidationStampFilterCreate::class.java).call {
            filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = uid("F"),
                    vsNames = listOf("PROJECT"),
                    branch = branch
                )
            )
        }
        asUser().withProjectFunction(branch, ValidationStampFilterMgt::class.java).call {
            assertFailsWith<AccessDeniedException> {
                filterService.shareValidationStampFilter(
                    f
                )
            }
        }
    }

    @Test
    fun `Cannot share a global filter when only share right`() {
        val f = asUser().withProjectFunction(branch, ValidationStampFilterCreate::class.java).call {
            filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = uid("F"),
                    vsNames = listOf("PROJECT"),
                    branch = branch
                )
            )
        }
        asUser().withProjectFunction(branch, ValidationStampFilterShare::class.java).call {
            assertFailsWith<AccessDeniedException> {
                filterService.shareValidationStampFilter(
                    f
                )
            }
        }
    }

    @Test
    fun `Cannot share a global filter when only create right`() {
        val f = asUser().withProjectFunction(branch, ValidationStampFilterCreate::class.java).call {
            filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = uid("F"),
                    vsNames = listOf("PROJECT"),
                    branch = branch
                )
            )
        }
        asUser().withProjectFunction(branch, ValidationStampFilterCreate::class.java).call {
            assertFailsWith<AccessDeniedException> {
                filterService.shareValidationStampFilter(
                    f
                )
            }
        }
    }

    @Test
    fun empty_patterns() {
        asUser().with(GlobalSettings::class.java).execute {
            var f = filterService.newValidationStampFilter(
                ValidationStampFilter(
                    name = uid("F"),
                    vsNames = emptyList()
                )
            )
            f = filterService.getValidationStampFilter(f.id)
            assertNotNull(f.vsNames)
            assertTrue(f.vsNames.isEmpty())
        }
    }

    // DSL

    private fun validationStampFilter(
        name: String = uid("VSF"),
        project: Project? = null,
        branch: Branch? = null,
        vsNames: List<String>
    ) = filterService.newValidationStampFilter(
        ValidationStampFilter(
            name = name,
            project = project,
            branch = branch,
            vsNames = vsNames
        )
    )

}
