package net.nemerosa.ontrack.repository.search

/**
 * Which documents a search looks into.
 *
 * @property types Types to look into, ordered by their display order, which is the last
 * criterion of the ranking
 * @property allProjects `true` if the documents of all projects are visible
 * @property projectIds IDs of the visible projects, when [allProjects] is `false`
 * @property projectLessTypes Types whose documents belonging to no project are visible
 */
data class SearchDocumentScope(
    val types: List<String>,
    val allProjects: Boolean,
    val projectIds: List<Int>,
    val projectLessTypes: List<String>,
)
