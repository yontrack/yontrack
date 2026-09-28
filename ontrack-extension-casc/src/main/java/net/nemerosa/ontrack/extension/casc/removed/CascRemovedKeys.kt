package net.nemerosa.ontrack.extension.casc.removed

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode

/**
 * Registry of the [removed CasC keys][CascRemovedKey], gathered from all the Spring beans of this
 * type.
 *
 * A CasC key without any context fails the CasC run ("No CasC context is defined"). A key listed
 * here is taken out of the CasC before it runs, with a warning, so that a CasC file written for a
 * previous version does not prevent Yontrack from starting.
 */
@Component
class CascRemovedKeys(
    private val removedKeys: List<CascRemovedKey>,
) {

    private val logger: Logger = LoggerFactory.getLogger(CascRemovedKeys::class.java)

    /**
     * Takes the removed keys out of the given CasC tree, logging a warning for each of them.
     *
     * @param node Complete CasC tree, starting from its root (above `ontrack`). It is modified in place.
     * @return Removed keys which were found (and taken out) in the tree
     */
    fun prune(node: JsonNode): List<CascRemovedKey> =
        removedKeys.filter { removedKey ->
            prune(node, removedKey)
        }

    private fun prune(node: JsonNode, removedKey: CascRemovedKey): Boolean {
        val parent = removedKey.path.dropLast(1).fold<String, JsonNode?>(node) { current, name ->
            current?.get(name)
        }
        val name = removedKey.path.last()
        return if (parent is ObjectNode && parent.has(name)) {
            parent.remove(name)
            logger.warn(removedKey.warning)
            true
        } else {
            false
        }
    }
}
