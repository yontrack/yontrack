package net.nemerosa.ontrack.graphql

import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.isNullOrNullNode
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.graphql.execution.ErrorType
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@AsAdminTest
class LabelMutationsGraphQLIT : AbstractQLKTITSupport() {

    // ========================================================================================
    // createLabel
    // ========================================================================================

    private fun createLabelQuery(category: String?, name: String, description: String?, color: String) =
        """
            mutation {
                createLabel(input: {
                    category: ${category?.let { "\"$it\"" } ?: "null"},
                    name: "$name",
                    description: ${description?.let { "\"$it\"" } ?: "null"},
                    color: "$color"
                }) {
                    label {
                        id
                        category
                        name
                        description
                        color
                    }
                    errors {
                        message
                        exception
                    }
                }
            }
        """

    @Test
    fun `Creating a label`() {
        val category = uid("C")
        val name = uid("N")
        run(createLabelQuery(category, name, "Some label", "#FF0000")) { data ->
            val node = checkGraphQLUserErrors(data, "createLabel").path("label")
            val id = node.path("id").asInt()
            assertEquals(category, node.path("category").asText())
            assertEquals(name, node.path("name").asText())
            assertEquals("Some label", node.path("description").asText())
            assertEquals("#FF0000", node.path("color").asText())
            val label = labelManagementService.getLabel(id)
            assertEquals(category, label.category)
            assertEquals(name, label.name)
        }
    }

    @Test
    fun `Creating a label without category nor description`() {
        val name = uid("N")
        run(createLabelQuery(null, name, null, "#00FF00")) { data ->
            val node = checkGraphQLUserErrors(data, "createLabel").path("label")
            assertTrue(node.path("category").isNullOrNullNode())
            assertTrue(node.path("description").isNullOrNullNode())
            assertEquals(name, node.path("name").asText())
        }
    }

    @Test
    fun `Creating a label with an invalid name`() {
        run(createLabelQuery(null, "not valid", null, "#00FF00")) { data ->
            assertUserError(data, "createLabel")
            assertTrue(data.path("createLabel").path("label").isNullOrNullNode())
        }
    }

    @Test
    fun `Creating a label with an invalid color`() {
        run(createLabelQuery(null, uid("N"), null, "red")) { data ->
            assertUserError(data, "createLabel")
        }
    }

    @Test
    fun `Creating a label which already exists`() {
        val existing = label()
        run(createLabelQuery(existing.category, existing.name, null, "#00FF00")) { data ->
            assertUserError(
                data, "createLabel",
                exception = "net.nemerosa.ontrack.model.labels.LabelCategoryNameAlreadyExistException"
            )
        }
    }

    @Test
    fun `Creating a label as a creator`() {
        val name = uid("N")
        asAccountWithGlobalRole(Roles.GLOBAL_CREATOR) {
            run(createLabelQuery(null, name, null, "#00FF00")) { data ->
                checkGraphQLUserErrors(data, "createLabel")
            }
        }
        assertEquals(1, labelManagementService.findLabels(null, name).size)
    }

    @Test
    fun `Creating a label is not granted without the label management function`() {
        val name = uid("N")
        asAccountWithGlobalRole(Roles.GLOBAL_READ_ONLY) {
            runWithError(
                createLabelQuery(null, name, null, "#00FF00"),
                errorClassification = ErrorType.FORBIDDEN
            )
        }
        assertTrue(labelManagementService.findLabels(null, name).isEmpty())
    }

    // ========================================================================================
    // updateLabel
    // ========================================================================================

    private fun updateLabelQuery(id: Int, category: String?, name: String, description: String?, color: String) =
        """
            mutation {
                updateLabel(input: {
                    id: $id,
                    category: ${category?.let { "\"$it\"" } ?: "null"},
                    name: "$name",
                    description: ${description?.let { "\"$it\"" } ?: "null"},
                    color: "$color"
                }) {
                    label {
                        id
                        category
                        name
                        description
                        color
                    }
                    errors {
                        message
                        exception
                    }
                }
            }
        """

    @Test
    fun `Updating a label`() {
        val label = label()
        val category = uid("C")
        val name = uid("N")
        run(updateLabelQuery(label.id, category, name, "Updated", "#0000FF")) { data ->
            val node = checkGraphQLUserErrors(data, "updateLabel").path("label")
            assertEquals(label.id, node.path("id").asInt())
            assertEquals(category, node.path("category").asText())
            assertEquals(name, node.path("name").asText())
            assertEquals("Updated", node.path("description").asText())
            assertEquals("#0000FF", node.path("color").asText())
        }
        val updated = labelManagementService.getLabel(label.id)
        assertEquals(category, updated.category)
        assertEquals(name, updated.name)
        assertEquals("Updated", updated.description)
        assertEquals("#0000FF", updated.color)
    }

    @Test
    fun `Updating a label which does not exist`() {
        run(updateLabelQuery(Int.MAX_VALUE, null, uid("N"), null, "#0000FF")) { data ->
            assertUserError(
                data, "updateLabel",
                exception = "net.nemerosa.ontrack.model.labels.LabelIdNotFoundException"
            )
        }
    }

    @Test
    fun `Updating a label is not granted without the label management function`() {
        val label = label()
        asAccountWithGlobalRole(Roles.GLOBAL_READ_ONLY) {
            runWithError(
                updateLabelQuery(label.id, null, uid("N"), null, "#0000FF"),
                errorClassification = ErrorType.FORBIDDEN
            )
        }
        assertEquals(label.name, labelManagementService.getLabel(label.id).name)
    }

    // ========================================================================================
    // deleteLabel
    // ========================================================================================

    private fun deleteLabelQuery(id: Int) =
        """
            mutation {
                deleteLabel(input: {id: $id}) {
                    errors {
                        message
                        exception
                    }
                }
            }
        """

    @Test
    fun `Deleting a label`() {
        val label = label()
        run(deleteLabelQuery(label.id)) { data ->
            checkGraphQLUserErrors(data, "deleteLabel")
        }
        assertNull(labelManagementService.findLabelById(label.id))
    }

    @Test
    fun `Deleting a label is not granted without the label management function`() {
        val label = label()
        asAccountWithGlobalRole(Roles.GLOBAL_READ_ONLY) {
            runWithError(
                deleteLabelQuery(label.id),
                errorClassification = ErrorType.FORBIDDEN
            )
        }
        assertNotNull(labelManagementService.findLabelById(label.id))
    }

    // ========================================================================================
    // setProjectLabels
    // ========================================================================================

    private fun setProjectLabelsQuery(project: Project, labelIds: List<Int>) =
        """
            mutation {
                setProjectLabels(input: {
                    projectId: ${project.id()},
                    labelIds: [${labelIds.joinToString(", ")}]
                }) {
                    project {
                        id
                        labels {
                            id
                        }
                    }
                    errors {
                        message
                        exception
                    }
                }
            }
        """

    private fun projectLabelIds(project: Project): Set<Int> =
        projectLabelManagementService.getLabelsForProject(project).map { it.id }.toSet()

    @Test
    fun `Setting the labels of a project replaces the whole set`() {
        val l1 = label()
        val l2 = label()
        val l3 = label()
        project {
            labels = listOf(l1, l2)
            run(setProjectLabelsQuery(this, listOf(l2.id, l3.id))) { data ->
                val node = checkGraphQLUserErrors(data, "setProjectLabels").path("project")
                assertEquals(id(), node.path("id").asInt())
                assertEquals(
                    setOf(l2.id, l3.id),
                    node.path("labels").map { it.path("id").asInt() }.toSet()
                )
            }
            assertEquals(setOf(l2.id, l3.id), projectLabelIds(this))
        }
    }

    @Test
    fun `Setting an empty list of labels removes all the labels of a project`() {
        val l1 = label()
        project {
            labels = listOf(l1)
            run(setProjectLabelsQuery(this, emptyList())) { data ->
                checkGraphQLUserErrors(data, "setProjectLabels")
            }
            assertEquals(emptySet(), projectLabelIds(this))
        }
    }

    @Test
    fun `Setting a label which does not exist`() {
        val l1 = label()
        project {
            labels = listOf(l1)
            run(setProjectLabelsQuery(this, listOf(Int.MAX_VALUE))) { data ->
                assertUserError(
                    data, "setProjectLabels",
                    exception = "net.nemerosa.ontrack.model.labels.LabelIdNotFoundException"
                )
            }
            assertEquals(setOf(l1.id), projectLabelIds(this))
        }
    }

    @Test
    fun `Setting the labels of a project as a project owner`() {
        val l1 = label()
        project {
            asAccountWithProjectRole(Roles.PROJECT_OWNER) {
                run(setProjectLabelsQuery(this, listOf(l1.id))) { data ->
                    checkGraphQLUserErrors(data, "setProjectLabels")
                }
            }
            assertEquals(setOf(l1.id), projectLabelIds(this))
        }
    }

    @Test
    fun `Setting the labels of a project is not granted without the project label management function`() {
        val l1 = label()
        project {
            asAccountWithProjectRole(Roles.PROJECT_PARTICIPANT) {
                runWithError(
                    setProjectLabelsQuery(this, listOf(l1.id)),
                    errorClassification = ErrorType.FORBIDDEN
                )
            }
            assertEquals(emptySet(), projectLabelIds(this))
        }
    }

    // ========================================================================================
    // labels authorization on project
    // ========================================================================================

    private fun Project.labelsAuthorization(): Boolean? {
        val data = run(
            """
                {
                    project(id: ${id()}) {
                        authorizations {
                            name
                            action
                            authorized
                        }
                    }
                }
            """
        )
        return data.path("project").path("authorizations")
            .find { it.path("name").asText() == "project" && it.path("action").asText() == "labels" }
            ?.path("authorized")?.asBoolean()
    }

    @Test
    fun `Labels authorization on a project for an administrator`() {
        project {
            assertEquals(true, labelsAuthorization())
        }
    }

    @Test
    fun `Labels authorization on a project for a project owner`() {
        project {
            asAccountWithProjectRole(Roles.PROJECT_OWNER) {
                assertEquals(true, labelsAuthorization())
            }
        }
    }

    @Test
    fun `Labels authorization on a project for a project manager`() {
        project {
            asAccountWithProjectRole(Roles.PROJECT_MANAGER) {
                assertEquals(true, labelsAuthorization())
            }
        }
    }

    @Test
    fun `Labels authorization on a project for automation`() {
        project {
            asAccountWithGlobalRole(Roles.GLOBAL_AUTOMATION) {
                assertEquals(true, labelsAuthorization())
            }
        }
    }

    @Test
    fun `Labels authorization on a project for a project participant`() {
        project {
            asAccountWithProjectRole(Roles.PROJECT_PARTICIPANT) {
                assertEquals(false, labelsAuthorization())
            }
        }
    }

    @Test
    fun `Labels authorization on a project for a read-only user`() {
        project {
            asAccountWithProjectRole(Roles.PROJECT_READ_ONLY) {
                assertEquals(false, labelsAuthorization())
            }
        }
    }

}
