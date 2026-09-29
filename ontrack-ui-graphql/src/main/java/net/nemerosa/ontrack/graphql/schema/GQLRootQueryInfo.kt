package net.nemerosa.ontrack.graphql.schema

import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.graphql.support.GQLScalarLocalDateTime
import net.nemerosa.ontrack.graphql.support.stringField
import net.nemerosa.ontrack.model.structure.Info
import net.nemerosa.ontrack.model.structure.InfoService
import net.nemerosa.ontrack.model.structure.VersionInfo
import org.springframework.stereotype.Component

/**
 * Root `info` query to get information about the application.
 */
@Component
class GQLRootQueryInfo(
    private val infoService: InfoService,
    private val gqlTypeInfo: GQLTypeInfo
) : GQLRootQuery {

    override fun getFieldDefinition(): GraphQLFieldDefinition =
        GraphQLFieldDefinition.newFieldDefinition()
            .name("info")
            .description("Gets information about the application")
            .type(gqlTypeInfo.typeRef)
            .dataFetcher { infoService.info }
            .build()
}

@Component
class GQLTypeInfo(
    private val gqlTypeVersionInfo: GQLTypeVersionInfo
) : GQLType {

    override fun getTypeName(): String = Info::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType = GraphQLObjectType.newObject()
        .name(typeName)
        .description("Application information")
        .field {
            it.name(Info::version.name)
                .description("Version information")
                .type(gqlTypeVersionInfo.typeRef)
        }
        .build()

}

@Component
class GQLTypeVersionInfo : GQLType {
    override fun getTypeName(): String = VersionInfo::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType = GraphQLObjectType.newObject()
        .name(typeName)
        .description("Version information")
        .field {
            it.name("date")
                .deprecate("Removed in V6. No replacement. See #1921")
                .description("Creation date")
                .type(GQLScalarLocalDateTime.INSTANCE)
        }
        .stringField(VersionInfo::display, "Display version")
        .stringField(VersionInfo::full, "Full version")
        .stringField(VersionInfo::branch, "Git branch")
        .stringField(VersionInfo::commit, "Git commit")
        .build()

}