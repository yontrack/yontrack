package net.nemerosa.ontrack.service.search

import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.SearchDocumentReference
import net.nemerosa.ontrack.repository.search.SearchDocumentHit
import net.nemerosa.ontrack.repository.search.SearchDocumentReferenceName
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode

/**
 * Resolves, when searching, the names of the projects and branches the data of the documents found
 * refer to (ADR 0017, *amendment*): the documents keep the IDs of the projects and branches they
 * refer to, and their current names are looked up in one go for all the documents found.
 */
object SearchDocumentNames {

    /**
     * Replaces the names of the references of the hits by the current names of their projects and
     * branches.
     *
     * @param hits Documents found
     * @param references References declared for a type of documents
     * @param lookup Current names of some projects and branches, by their IDs — called at most
     * once, and only when the hits have references
     * @param visible Whether the user can see a project, given its ID: the name of a project the
     * user cannot see, or of one of its branches, is `null`, as is the name of a project or branch
     * which does not exist any longer
     * @return The hits, in the same order, with new data when it has references
     */
    fun resolve(
        hits: List<SearchDocumentHit>,
        references: (type: String) -> List<SearchDocumentReference>,
        lookup: (projectIds: Set<Int>, branchIds: Set<Int>) -> List<SearchDocumentReferenceName>,
        visible: (projectId: Int) -> Boolean,
    ): List<SearchDocumentHit> {
        // Data of the hits, as copies, with their references
        val copies = hits.map { hit ->
            val typeReferences = references(hit.type)
            if (typeReferences.isEmpty()) {
                null
            } else {
                val data = hit.data.deepCopy()
                data to typeReferences.flatMap { reference ->
                    objectsAt(data, reference.path).map { reference.type to it }
                }.filter { (_, node) -> id(node) != null }
            }
        }
        val all = copies.filterNotNull().flatMap { (_, nodes) -> nodes }
        if (all.isEmpty()) {
            return hits
        }
        // One lookup for all the hits
        val projectIds = all.filter { (type, _) -> type == ProjectEntityType.PROJECT }.mapNotNull { (_, node) -> id(node) }.toSet()
        val branchIds = all.filter { (type, _) -> type == ProjectEntityType.BRANCH }.mapNotNull { (_, node) -> id(node) }.toSet()
        val names = lookup(projectIds, branchIds)
            .filter { visible(it.projectId) }
            .associate { (it.type to it.id) to it.name }
        // Current names
        all.forEach { (type, node) ->
            val name = names[type to id(node)]
            if (name != null) {
                node.put(NAME, name)
            } else {
                node.putNull(NAME)
            }
        }
        return hits.mapIndexed { index, hit ->
            copies[index]?.let { (data, _) -> hit.copy(data = data) } ?: hit
        }
    }

    private const val ID = "id"
    private const val NAME = "name"

    private fun id(node: ObjectNode): Int? = node.get(ID)?.takeIf { it.isIntegralNumber }?.intValue()

    /**
     * The objects at a path of some data, walking into the arrays met on the way.
     */
    private fun objectsAt(node: JsonNode?, path: List<String>): List<ObjectNode> = when {
        node == null || node.isNull || node.isMissingNode -> emptyList()
        node.isArray -> node.values().flatMap { objectsAt(it, path) }
        path.isEmpty() -> listOfNotNull(node as? ObjectNode)
        else -> objectsAt(node.get(path.first()), path.drop(1))
    }
}
