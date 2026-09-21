package net.nemerosa.ontrack.model.deliverymap

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import tools.jackson.databind.node.NullNode
import net.nemerosa.ontrack.json.asJson

/**
 * One node of a [DeliveryMap], being a promotion level, a validation stamp or a slot.
 *
 * Every checkpoint names the latest build to have *arrived* at it and says what became of it there.
 * Arriving is not the same as succeeding: only a validation stamp can show a build that arrived and
 * failed. See `CONTEXT.md`.
 *
 * The exception is the [DeliveryMapCheckpointTypes.UNRESOLVED] checkpoint, which stands for a name a
 * configuration asked for and which matches nothing. It has no entity behind it and therefore no
 * arrival - see [unresolved].
 *
 * @property id Stable across fetches and unique across kinds. #1707 keeps node positions when only
 * the builds moved, and React Flow can only do that if it sees the same node ids; a regenerated id
 * remounts the node and loses its position. Build it through [DeliveryMapCheckpointTypes].
 * @property type Kind of checkpoint. Deliberately a string and not an enum: the set of kinds is open,
 * so that an extension can contribute a kind the core has never heard of. The frontend dispatches on
 * it through its own registry of checkpoint renderers.
 * @property data Kind-specific payload, read by the renderer selected by [type]. It is what carries
 * the link to the underlying entity.
 * @property members The checkpoints an *aggregate* checkpoint stands for, empty for every other kind.
 * Members are not laid out: they are shown inside their aggregate, on demand.
 */
@APIDescription("One node of a delivery map")
data class DeliveryMapCheckpoint(
    @APIDescription("Identifier, stable across fetches and unique across kinds")
    val id: String,
    @APIDescription("Kind of checkpoint, selecting how it is rendered")
    val type: String,
    @APIDescription("Label of the checkpoint")
    val name: String,
    @APIDescription("Description of the checkpoint")
    val description: String? = null,
    @APIDescription("Kind-specific payload, read by the renderer selected by the type")
    val data: JsonNode = NullNode.instance,
    @APIDescription("Latest build to have arrived at this checkpoint, and what became of it there")
    val arrival: DeliveryMapArrival? = null,
    @APIDescription("Checkpoints an aggregate checkpoint stands for, empty for every other kind")
    val members: List<DeliveryMapCheckpoint> = emptyList(),
) {
    companion object {

        /**
         * The checkpoint a configuration pointing at nothing is drawn as: it carries the [name] the
         * configuration asked for and the kind of thing it was looking for, and is marked as matching
         * nothing.
         *
         * Drawing nothing instead would make the map quietly agree with the broken configuration,
         * which is the failure this exists to prevent (#1705).
         *
         * It is built here rather than by [DeliveryMapCheckpointFactory] because it needs no service
         * to build - there is no entity behind it - and a contributor should not have to take the
         * factory as a dependency just to say that a name matched nothing.
         *
         * @param reference Kind of checkpoint the configuration named, as a
         * [DeliveryMapCheckpointTypes] constant or an extension's own
         * @param name Name the configuration asked for
         */
        fun unresolved(reference: String, name: String) = DeliveryMapCheckpoint(
            id = DeliveryMapCheckpointTypes.unresolved(reference, name),
            type = DeliveryMapCheckpointTypes.UNRESOLVED,
            name = name,
            data = UnresolvedCheckpointData(reference = reference).asJson(),
            // No arrival, ever: nothing can arrive at something which does not exist.
        )

    }
}
