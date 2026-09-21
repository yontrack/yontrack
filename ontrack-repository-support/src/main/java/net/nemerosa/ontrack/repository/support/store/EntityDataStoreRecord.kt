package net.nemerosa.ontrack.repository.support.store

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.Signature

data class EntityDataStoreRecord(
    val id: Int,
    val entity: ProjectEntity?,
    val category: String,
    val name: String,
    val groupName: String?,
    val signature: Signature,
    val data: JsonNode
) : Comparable<EntityDataStoreRecord> {

    override fun compareTo(other: EntityDataStoreRecord): Int {
        val i = -this.signature.time.compareTo(other.signature.time)
        if (i != 0) {
            return i
        } else {
            return other.id - this.id
        }
    }

}
