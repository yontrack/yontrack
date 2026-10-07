package net.nemerosa.ontrack.graphql.schema.events

import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.GraphQLBeanConverter
import net.nemerosa.ontrack.model.events.EventsExportInfo
import org.springframework.stereotype.Component

/**
 * What the export of the events matching a filter would hold.
 */
@Component
class GQLTypeEventsExportInfo : GQLType {

    override fun getTypeName(): String = EventsExportInfo::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLBeanConverter.asObjectType(EventsExportInfo::class, cache)
}
