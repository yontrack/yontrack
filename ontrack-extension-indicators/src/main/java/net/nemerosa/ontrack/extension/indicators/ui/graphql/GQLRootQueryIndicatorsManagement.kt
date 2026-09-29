package net.nemerosa.ontrack.extension.indicators.ui.graphql

import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.indicators.acl.IndicatorPortfolioAccess
import net.nemerosa.ontrack.extension.indicators.acl.IndicatorTypeManagement
import net.nemerosa.ontrack.extension.indicators.acl.IndicatorViewManagement
import net.nemerosa.ontrack.graphql.schema.GQLRootQuery
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.booleanField
import net.nemerosa.ontrack.graphql.support.toNotNull
import net.nemerosa.ontrack.model.security.SecurityService
import org.springframework.stereotype.Component

@Component
@Deprecated("Removed in V6. No replacement. See #1893")
class GQLRootQueryIndicatorsManagement(
    private val gqlIndicatorsManagement: GQLTypeIndicatorsManagement,
    private val securityService: SecurityService,
) : GQLRootQuery {

    override fun getFieldDefinition(): GraphQLFieldDefinition = GraphQLFieldDefinition.newFieldDefinition()
        .name("indicatorsManagement")
        .description("List of available commands for the management of the indicators")
        .deprecate("Removed in V6. No replacement. See #1893")
        .type(gqlIndicatorsManagement.typeRef.toNotNull())
        .dataFetcher { _ ->
            getIndicatorsManagement()
        }
        .build()

    private fun getIndicatorsManagement(): IndicatorsManagement {
        val typeManagement = securityService.isGlobalFunctionGranted(IndicatorTypeManagement::class.java)
        return IndicatorsManagement(
            portfolios = securityService.isGlobalFunctionGranted(IndicatorPortfolioAccess::class.java),
            configuration = typeManagement,
            categories = typeManagement,
            types = typeManagement,
            views = securityService.isGlobalFunctionGranted(IndicatorViewManagement::class.java),
        )
    }
}

@APIDescription("Management flags for the indicators")
@Deprecated("Removed in V6. No replacement. See #1893")
class IndicatorsManagement(
    @APIDescription("Access to the indicator portfolios")
    val portfolios: Boolean,
    @APIDescription("Management of the configurable indicators")
    val configuration: Boolean,
    @APIDescription("Management of the indicators categories")
    val categories: Boolean,
    @APIDescription("Management of the indicators types")
    val types: Boolean,
    @APIDescription("Management of the indicators views")
    val views: Boolean,
)

@Component
@Deprecated("Removed in V6. No replacement. See #1893")
class GQLTypeIndicatorsManagement : GQLType {

    override fun getTypeName(): String = "IndicatorsManagement"

    override fun createType(cache: GQLTypeCache): GraphQLObjectType = GraphQLObjectType.newObject()
        .name(typeName)
        .description("Management of indicators")
        .booleanField(IndicatorsManagement::portfolios)
        .booleanField(IndicatorsManagement::configuration)
        .booleanField(IndicatorsManagement::categories)
        .booleanField(IndicatorsManagement::types)
        .booleanField(IndicatorsManagement::views)
        .build()

}