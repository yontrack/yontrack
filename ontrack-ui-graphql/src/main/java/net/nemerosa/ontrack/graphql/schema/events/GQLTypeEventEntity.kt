package net.nemerosa.ontrack.graphql.schema.events

import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.Scalars.GraphQLInt
import graphql.Scalars.GraphQLString
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.enumField
import net.nemerosa.ontrack.graphql.support.getTypeDescription
import net.nemerosa.ontrack.model.annotations.getPropertyDescription
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import org.springframework.stereotype.Component

/**
 * Entity an event is about.
 */
@APIDescription("Entity an event is about")
data class EventEntity(
    @APIDescription("Type of the entity")
    val type: ProjectEntityType,
    @APIDescription("ID of the entity")
    val id: Int,
    @APIDescription("Display name of the entity, like `Branch P/X`")
    val displayName: String,
) {
    companion object {
        fun of(entity: ProjectEntity) = EventEntity(
            type = entity.projectEntityType,
            id = entity.id(),
            displayName = entity.entityDisplayName,
        )
    }
}

@Component
class GQLTypeEventEntity : GQLType {

    override fun getTypeName(): String = EventEntity::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description(getTypeDescription(EventEntity::class))
            .enumField(EventEntity::type)
            .field {
                it.name(EventEntity::id.name)
                    .description(getPropertyDescription(EventEntity::id))
                    .type(GraphQLNonNull(GraphQLInt))
            }
            .field {
                it.name(EventEntity::displayName.name)
                    .description(getPropertyDescription(EventEntity::displayName))
                    .type(GraphQLNonNull(GraphQLString))
            }
            .build()
}
