package net.nemerosa.ontrack.repository

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.Ack
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.PropertySearchArguments
import java.util.function.BiFunction
import java.util.function.Predicate

/**
 * Repository to access the properties.
 */
interface PropertyRepository {

    fun hasProperty(typeName: String, entityType: ProjectEntityType, entityId: ID): Boolean

    fun loadProperty(typeName: String, entityType: ProjectEntityType, entityId: ID): TProperty?

    fun saveProperty(typeName: String, entityType: ProjectEntityType, entityId: ID, data: JsonNode)

    fun deleteProperty(typeName: String, entityType: ProjectEntityType, entityId: ID): Ack

    fun searchByProperty(typeName: String,
                         entityLoader: BiFunction<ProjectEntityType, ID, ProjectEntity>,
                         predicate: Predicate<TProperty>
    ): Collection<ProjectEntity>

    fun findBuildByBranchAndSearchkey(branchId: ID, typeName: String, searchArguments: PropertySearchArguments?): ID?

    fun findByEntityTypeAndSearchArguments(entityType: ProjectEntityType, typeName: String, searchArguments: PropertySearchArguments?): List<ID>

    /**
     * Loops over all the properties of a given type.
     */
    fun forEachEntityWithProperty(typeName: String, consumer: (TProperty) -> Unit)
}
