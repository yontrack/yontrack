package net.nemerosa.ontrack.extension.audittrail.ui

import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.extension.audittrail.verification.TrailVerification
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.GraphQLBeanConverter
import net.nemerosa.ontrack.model.annotations.getAPITypeName
import org.springframework.stereotype.Component

@Component
class GQLTypeAuditTrailVerification : GQLType {

    override fun getTypeName(): String = getAPITypeName(TrailVerification::class)

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLBeanConverter.asObjectType(TrailVerification::class, cache)
}
