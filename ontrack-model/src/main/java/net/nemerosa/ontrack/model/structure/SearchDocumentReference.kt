package net.nemerosa.ontrack.model.structure

/**
 * Reference, in the [data][SearchDocument.data] of a search document, to a project or a branch: an
 * object carrying its `id` and its `name`, the shape [searchDocumentData] gives them.
 *
 * The names of the references an [indexer][SearchDocumentIndexer.nameReferences] declares are
 * resolved when searching, not when indexing: the `name` of each of them is replaced by the current
 * name of the project or branch whose `id` it carries, so that a result shows the current names of
 * the entities it refers to, even when its document was written before they were renamed. The name
 * of a project the user cannot see, or which does not exist any longer, is `null`.
 *
 * @property type [ProjectEntityType.PROJECT] or [ProjectEntityType.BRANCH]
 * @property path Path of the object in the data, from its root. An array met on the path is walked
 * into: each of its elements is a reference.
 */
data class SearchDocumentReference(
    val type: ProjectEntityType,
    val path: List<String>,
) {

    init {
        require(type == ProjectEntityType.PROJECT || type == ProjectEntityType.BRANCH) {
            "A search document reference is to a project or a branch"
        }
        require(path.isNotEmpty()) { "The path of a search document reference cannot be empty" }
    }

    companion object {

        /**
         * Reference to a project at a dotted path of the data, `build.branch.project` say.
         */
        fun project(path: String) = SearchDocumentReference(ProjectEntityType.PROJECT, path.split('.'))

        /**
         * Reference to a branch at a dotted path of the data, `build.branch` say.
         */
        fun branch(path: String) = SearchDocumentReference(ProjectEntityType.BRANCH, path.split('.'))
    }
}
