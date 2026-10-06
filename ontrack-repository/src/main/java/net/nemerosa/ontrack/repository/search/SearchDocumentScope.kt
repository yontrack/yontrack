package net.nemerosa.ontrack.repository.search

/**
 * Which documents a search looks into.
 *
 * @property types Types to look into, ordered by their display order, which is the last
 * criterion of the ranking
 * @property allProjects `true` if the documents of all projects are visible
 * @property projectIds IDs of the visible projects, when [allProjects] is `false`
 * @property projectLessTypes Types whose documents belonging to no project are visible
 * @property restrictedTypes Types whose documents belonging to a project are visible in some of
 * the visible projects only, with the IDs of these projects — the types of the indexers declaring
 * a project function which is not granted for every visible project
 * @property nonFuzzyTypes Types whose documents are not matched by similarity
 */
data class SearchDocumentScope(
    val types: List<String>,
    val allProjects: Boolean,
    val projectIds: List<Int>,
    val projectLessTypes: List<String>,
    val restrictedTypes: Map<String, List<Int>> = emptyMap(),
    val nonFuzzyTypes: List<String> = emptyList(),
) {

    private val visibleProjectIds: Set<Int> by lazy { projectIds.toSet() }

    /**
     * Whether the documents of a project are visible — and its name.
     */
    fun isProjectVisible(projectId: Int): Boolean = allProjects || projectId in visibleProjectIds
}
