package net.nemerosa.ontrack.model.structure

/*
 * What the search results need of the structure entities to render and link them, in the
 * `data` of a search document. One shape per entity, so that all the `framework/search/{type}/Result`
 * components of the frontend receive the same one.
 */

/**
 * A project in the data of a search document: `{id, name}`.
 */
fun Project.searchDocumentData(): Map<String, Any?> = mapOf(
    "id" to id(),
    "name" to name,
)

/**
 * A branch in the data of a search document: `{id, name, description, disabled, project}`.
 */
fun Branch.searchDocumentData(): Map<String, Any?> = mapOf(
    "id" to id(),
    "name" to name,
    "description" to description,
    "disabled" to isDisabled,
    "project" to project.searchDocumentData(),
)

/**
 * A build in the data of a search document: `{id, name, description, branch {id, name, project}}`.
 */
fun Build.searchDocumentData(): Map<String, Any?> = mapOf(
    "id" to id(),
    "name" to name,
    "description" to description,
    "branch" to mapOf(
        "id" to branch.id(),
        "name" to branch.name,
        "project" to project.searchDocumentData(),
    ),
)

/**
 * The [references][SearchDocumentReference] of a project rendered by [Project.searchDocumentData] at
 * a dotted path of the data of a search document.
 */
fun projectSearchDocumentReferences(path: String): List<SearchDocumentReference> = listOf(
    SearchDocumentReference.project(path),
)

/**
 * The [references][SearchDocumentReference] of a branch rendered by [Branch.searchDocumentData] at a
 * dotted path of the data of a search document: the branch and its project.
 */
fun branchSearchDocumentReferences(path: String): List<SearchDocumentReference> =
    listOf(SearchDocumentReference.branch(path)) + projectSearchDocumentReferences("$path.project")

/**
 * The [references][SearchDocumentReference] of a build rendered by [Build.searchDocumentData] at a
 * dotted path of the data of a search document: its branch and its project.
 */
fun buildSearchDocumentReferences(path: String): List<SearchDocumentReference> =
    branchSearchDocumentReferences("$path.branch")
