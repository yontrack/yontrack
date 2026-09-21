package net.nemerosa.ontrack.repository

import net.nemerosa.ontrack.model.exceptions.ValidationStampFilterNameAlreadyDefinedException
import net.nemerosa.ontrack.model.exceptions.ValidationStampFilterNotFoundException
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.NameDescription
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.ValidationStampFilter
import net.nemerosa.ontrack.test.TestUtils.uid
import org.apache.commons.lang3.Strings
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.*
import kotlin.test.*

class ValidationStampFilterJdbcRepositoryIT : AbstractRepositoryTestSupport() {

    @Autowired
    private lateinit var filterRepository: ValidationStampFilterRepository

    private lateinit var branch: Branch

    @BeforeEach
    fun setup() {
        branch = do_create_branch()
    }

    @Test
    fun new_global_filter() {
        val filter = filterRepository.newValidationStampFilter(
            ValidationStampFilter(
                name = "My filter",
                vsNames = listOf("CI")
            )
        )
        assertTrue(filter.id.isSet)
    }

    @Test
    fun new_global_filter_with_existing_name() {
        filterRepository.newValidationStampFilter(
            ValidationStampFilter(
                name = "My filter",
                vsNames = listOf("CI")
            )
        )
        // Creates another filter with the same name
        assertFailsWith<ValidationStampFilterNameAlreadyDefinedException> {
            filterRepository.newValidationStampFilter(
                ValidationStampFilter(
                    name = "My filter",
                    vsNames = listOf("OTHER")
                )
            )
        }
    }

    @Test
    fun new_filter_with_both_project_and_branch() {
        val project = Project.of(NameDescription.nd("P", ""))
        assertFailsWith<IllegalStateException> {
            filterRepository.newValidationStampFilter(
                ValidationStampFilter(
                    name = "My filter",
                    project = project,
                    branch = Branch.of(project, NameDescription.nd("B", "")),
                    vsNames = listOf("CI")
                )
            )
        }
    }

    @Test
    fun new_project_filter() {
        val filter = filterRepository.newValidationStampFilter(
            ValidationStampFilter(
                name = "My filter",
                project = branch.project,
                vsNames = listOf("CI")
            )
        )
        assertTrue(filter.id.isSet)
    }

    @Test
    fun new_project_filter_with_existing_name() {
        filterRepository.newValidationStampFilter(
            ValidationStampFilter(
                name = "My filter",
                project = branch.project,
                vsNames = listOf("CI")
            )
        )
        // Creates another filter with the same name
        assertFailsWith<ValidationStampFilterNameAlreadyDefinedException> {
            filterRepository.newValidationStampFilter(
                ValidationStampFilter(
                    name = "My filter",
                    project = branch.project,
                    vsNames = listOf("OTHER")
                )
            )
        }
    }

    @Test
    fun new_project_filter_with_same_name_than_global() {
        filterRepository.newValidationStampFilter(
            ValidationStampFilter(
                name = "My filter",
                vsNames = listOf("CI")
            )
        )
        val filter = filterRepository.newValidationStampFilter(
            ValidationStampFilter(
                name = "My filter",
                project = branch.project,
                vsNames = listOf("CI")
            )
        )
        assertTrue(filter.id.isSet)
    }

    @Test
    fun new_branch_filter() {
        val filter = filterRepository.newValidationStampFilter(
            ValidationStampFilter(
                name = "My filter",
                branch = branch,
                vsNames = listOf("CI")
            )
        )
        assertTrue(filter.id.isSet)
    }

    @Test
    fun new_branch_filter_with_existing_name() {
        filterRepository.newValidationStampFilter(
            ValidationStampFilter(
                name = "My filter",
                branch = branch,
                vsNames = listOf("CI")
            )
        )
        // Creates another filter with the same name
        assertFailsWith<ValidationStampFilterNameAlreadyDefinedException> {
            filterRepository.newValidationStampFilter(
                ValidationStampFilter(
                    name = "My filter",
                    branch = branch,
                    vsNames = listOf("OTHER")
                )
            )
        }
    }

    @Test
    fun new_branch_filter_with_same_name_than_global() {
        filterRepository.newValidationStampFilter(
            ValidationStampFilter(
                name = "My filter",
                vsNames = listOf("CI")
            )
        )
        val filter = filterRepository.newValidationStampFilter(
            ValidationStampFilter(
                name = "My filter",
                branch = branch,
                vsNames = listOf("CI")
            )
        )
        assertTrue(filter.id.isSet)
    }

    @Test
    fun new_branch_filter_with_same_name_than_project() {
        filterRepository.newValidationStampFilter(
            ValidationStampFilter(
                name = "My filter",
                project = branch.project,
                vsNames = listOf("CI")
            )
        )
        val filter = filterRepository.newValidationStampFilter(
            ValidationStampFilter(
                name = "My filter",
                branch = branch,
                vsNames = listOf("CI")
            )
        )
        assertTrue(filter.id.isSet)
    }

    private fun createFilters(): String {
        val name = uid("F")
        // Global
        filterRepository.newValidationStampFilter(
            ValidationStampFilter(
                name = name,
                vsNames = listOf("GLOBAL")
            )
        )
        // Project
        filterRepository.newValidationStampFilter(
            ValidationStampFilter(
                name = name,
                project = branch.project,
                vsNames = listOf("PROJECT")
            )
        )
        // Branch
        filterRepository.newValidationStampFilter(
            ValidationStampFilter(
                name = name,
                branch = branch,
                vsNames = listOf("BRANCH")
            )
        )
        // OK
        return name
    }

    @Test
    fun global_filters() {
        val name = createFilters()
        val list = filterRepository.globalValidationStampFilters
            .filter { f -> Strings.CS.equals(name, f.name) }
        assertEquals(1, list.size.toLong())
        assertEquals(name, list[0].name)
        assertEquals(listOf("GLOBAL"), list[0].vsNames)
    }

    @Test
    fun project_filters() {
        val name = createFilters()
        val list = filterRepository.getProjectValidationStampFilters(branch.project)
            .filter { f -> Strings.CS.equals(name, f.name) }
        assertEquals(1, list.size.toLong())
        assertEquals(name, list[0].name)
        assertEquals(listOf("PROJECT"), list[0].vsNames)
    }

    @Test
    fun branch_filters() {
        val name = createFilters()
        val list = filterRepository.getBranchValidationStampFilters(branch)
            .filter { f -> Strings.CS.equals(name, f.name) }
        assertEquals(1, list.size.toLong())
        assertEquals(name, list[0].name)
        assertEquals(listOf("BRANCH"), list[0].vsNames)
    }

    @Test
    fun by_name_global_only() {
        val name = uid("F")
        filterRepository.newValidationStampFilter(
            ValidationStampFilter(
                name = name,
                vsNames = listOf("GLOBAL")
            )
        )
        val f = filterRepository.getValidationStampFilterByName(branch, name).orElse(null)
        assertNotNull(f) {
            assertEquals(name, it.name)
            assertEquals(listOf("GLOBAL"), it.vsNames)
        }
    }

    @Test
    fun by_name_global_and_project() {
        val name = uid("F")
        filterRepository.newValidationStampFilter(
            ValidationStampFilter(
                name = name,
                vsNames = listOf("GLOBAL")
            )
        )
        filterRepository.newValidationStampFilter(
            ValidationStampFilter(
                name = name,
                project = branch.project,
                vsNames = listOf("PROJECT")
            )
        )
        val f = filterRepository.getValidationStampFilterByName(branch, name).orElse(null)
        assertNotNull(f)
        assertEquals(name, f.name)
        assertEquals(listOf("PROJECT"), f.vsNames)
    }

    @Test
    fun by_name_branch() {
        val name = uid("F")
        filterRepository.newValidationStampFilter(
            ValidationStampFilter(
                name = name,
                vsNames = listOf("GLOBAL")
            )
        )
        filterRepository.newValidationStampFilter(
            ValidationStampFilter(
                name = name,
                project = branch.project,
                vsNames = listOf("PROJECT")
            )
        )
        filterRepository.newValidationStampFilter(
            ValidationStampFilter(
                name = name,
                branch = branch,
                vsNames = listOf("BRANCH")
            )
        )
        val f = filterRepository.getValidationStampFilterByName(branch, name).orElse(null)
        assertNotNull(f)
        assertEquals(name, f.name)
        assertEquals(listOf("BRANCH"), f.vsNames)
    }

    @Test
    fun by_name_none() {
        createFilters()
        val f = filterRepository.getValidationStampFilterByName(branch, uid("FX")).orElse(null)
        assertNull(f)
    }

    @Test
    fun save_global() {
        var f = filterRepository.newValidationStampFilter(
            ValidationStampFilter(
                name = uid("F"),
                vsNames = listOf("GLOBAL")
            )
        )
        filterRepository.saveValidationStampFilter(f.withVsNames(Arrays.asList("GLOBAL", "ONTRACK")))
        f = filterRepository.getValidationStampFilter(f.id)
        assertEquals(Arrays.asList("GLOBAL", "ONTRACK"), f.vsNames)
    }

    @Test
    fun save_project() {
        var f = filterRepository.newValidationStampFilter(
            ValidationStampFilter(
                name = uid("F"),
                project = branch.project,
                vsNames = listOf("PROJECT")
            )
        )
        filterRepository.saveValidationStampFilter(f.withVsNames(Arrays.asList("PROJECT", "ONTRACK")))
        f = filterRepository.getValidationStampFilter(f.id)
        assertEquals(Arrays.asList("PROJECT", "ONTRACK"), f.vsNames)
    }

    @Test
    fun save_branch() {
        var f = filterRepository.newValidationStampFilter(
            ValidationStampFilter(
                name = uid("F"),
                branch = branch,
                vsNames = listOf("BRANCH")
            )
        )
        filterRepository.saveValidationStampFilter(f.withVsNames(Arrays.asList("BRANCH", "ONTRACK")))
        f = filterRepository.getValidationStampFilter(f.id)
        assertEquals(Arrays.asList("BRANCH", "ONTRACK"), f.vsNames)
    }

    @Test
    fun delete() {
        val f = filterRepository.newValidationStampFilter(
            ValidationStampFilter(
                name = uid("F"),
                vsNames = listOf("GLOBAL")
            )
        )
        filterRepository.deleteValidationStampFilter(f.id)
        // Checks it is gone
        try {
            filterRepository.getValidationStampFilter(f.id)
            fail("It should have been deleted")
        } catch (ex: ValidationStampFilterNotFoundException) {
            assertEquals(String.format("Validation stamp filter with ID %s not found", f.id), ex.message)
        }

    }

    @Test
    fun share_from_branch_to_project() {
        val f = filterRepository.newValidationStampFilter(
            ValidationStampFilter(
                name = uid("F"),
                branch = branch,
                vsNames = listOf("CI")
            )
        )
        val f2 = filterRepository.shareValidationStampFilter(f, branch.project)
        assertTrue(f.id() == f2.id())
        assertNotNull(f2.project)
        assertNull(f2.branch)
    }

    @Test
    fun share_from_branch_to_global() {
        val f = filterRepository.newValidationStampFilter(
            ValidationStampFilter(
                name = uid("F"),
                branch = branch,
                vsNames = listOf("CI")
            )
        )
        val f2 = filterRepository.shareValidationStampFilter(f)
        assertTrue(f.id() == f2.id())
        assertNull(f2.project)
        assertNull(f2.branch)
    }

    @Test
    fun share_from_project_to_global() {
        val f = filterRepository.newValidationStampFilter(
            ValidationStampFilter(
                name = uid("F"),
                project = branch.project,
                vsNames = listOf("CI")
            )
        )
        val f2 = filterRepository.shareValidationStampFilter(f)
        assertTrue(f.id() == f2.id())
        assertNull(f2.project)
        assertNull(f2.branch)
    }

    @Test
    fun empty_patterns() {
        var f = filterRepository.newValidationStampFilter(
            ValidationStampFilter(
                name = uid("F"),
                vsNames = emptyList()
            )
        )
        f = filterRepository.getValidationStampFilter(f.id)
        assertNotNull(f.vsNames)
        assertTrue(f.vsNames.isEmpty())
    }

}
