package net.nemerosa.ontrack.graphql.schema.security

import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.graphql.schema.GQLRootQuery
import net.nemerosa.ontrack.graphql.support.intArgument
import net.nemerosa.ontrack.graphql.support.listType
import net.nemerosa.ontrack.graphql.support.stringArgument
import net.nemerosa.ontrack.model.security.AccountService
import net.nemerosa.ontrack.model.security.PermissionTarget
import net.nemerosa.ontrack.model.structure.ID
import org.springframework.stereotype.Component

@Component
class GQLRootQueryPermissionTargets(
    private val accountService: AccountService,
) : GQLRootQuery {
    override fun getFieldDefinition(): GraphQLFieldDefinition =
        GraphQLFieldDefinition.newFieldDefinition()
            .name("permissionTargets")
            .description("List of permission targets")
            .type(listType(PermissionTarget::class.java.simpleName))
            .argument(stringArgument(ARG_NAME, "Part of the name of the permission target to look for"))
            .argument(
                intArgument(
                    ARG_PROJECT_ID,
                    "ID of the project to grant a role on. When set, the search needs the management of the permissions of this project instead of the global management of the accounts."
                )
            )
            .dataFetcher { env ->
                val name: String? = env.getArgument(ARG_NAME)
                val projectId: Int? = env.getArgument(ARG_PROJECT_ID)
                if (projectId != null) {
                    accountService.searchPermissionTargets(name, ID.of(projectId))
                } else {
                    accountService.searchPermissionTargets(name)
                }
            }
            .build()

    companion object {
        private const val ARG_NAME = "token"
        private const val ARG_PROJECT_ID = "projectId"
    }
}