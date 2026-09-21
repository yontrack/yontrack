package net.nemerosa.ontrack.extension.casc.entities

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.PropertyService
import net.nemerosa.ontrack.model.structure.PropertyType

abstract class AbstractCascEntityPropertyContext<T>(
    override val field: String,
    private val propertyType: PropertyType<T>,
    private val propertyService: PropertyService,
) : CascEntityPropertyContext {

    override fun run(
        entity: ProjectEntity,
        node: JsonNode,
        paths: List<String>,
    ) {
        // Parsing
        val value: T = parseProperty(node)
        // Setting the property
        propertyService.editProperty(
            entity,
            propertyType::class.java,
            value
        )
    }

    protected open fun parseProperty(node: JsonNode): T =
        propertyType.fromStorage(node)

}