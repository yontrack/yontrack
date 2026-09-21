package net.nemerosa.ontrack.extension.casc.context.core.admin

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.syncForward
import net.nemerosa.ontrack.extension.casc.context.AbstractCascContext
import net.nemerosa.ontrack.json.JsonParseException
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.json.schema.JsonArrayType
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import net.nemerosa.ontrack.model.security.AccountService
import net.nemerosa.ontrack.model.security.GroupMappingService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class GroupMappingsCasc(
    private val groupMappingService: GroupMappingService,
    private val accountService: AccountService,
) : AbstractCascContext(), SubAdminContext {

    private val logger: Logger = LoggerFactory.getLogger(GroupMappingsCasc::class.java)

    /**
     * Mapping must be done after the groups have been defined
     */
    override val priority: Int = AccountGroupsAdminContext.PRIORITY - 1

    override fun run(node: JsonNode, paths: List<String>) {
        val existingMappings = groupMappingService.groupMappings
        val items = node.mapIndexed { index, child ->
            try {
                child.parse<GroupMappingsCascItem>()
            } catch (ex: JsonParseException) {
                throw IllegalStateException(
                    "Cannot parse into ${GroupMappingsCascItem::class.qualifiedName}: ${path(paths + index.toString())}",
                    ex
                )
            }
        }

        fun update(item: GroupMappingsCascItem) {
            val accountGroup = accountService.findAccountGroupByName(item.group)
            if (accountGroup != null) {
                groupMappingService.mapGroup(
                    item.idp,
                    accountGroup
                )
            } else {
                logger.warn("Cannot map group ${item.group} as it does not exist.")
            }
        }

        syncForward(
            from = items,
            to = existingMappings,
        ) {
            equality { a, b -> a.idp == b.idpGroup }
            onCreation { item -> update(item) }
            onModification { item, _ -> update(item) }
            onDeletion { existing ->
                groupMappingService.mapGroup(idpGroup = existing.idpGroup, accountGroup = null)
            }
        }
    }

    override fun render(): JsonNode =
        groupMappingService.groupMappings.map {
            GroupMappingsCascItem(
                idp = it.idpGroup,
                group = it.group.name
            )
        }.asJson()

    override fun jsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType {
        return JsonArrayType(
            items = jsonTypeBuilder.toType(GroupMappingsCascItem::class),
            description = "List of group mappings",
        )
    }

    override val field: String = "group-mappings"
}

@APIDescription("Mapping between an IDP group and a Yontrack group")
data class GroupMappingsCascItem(
    @APIDescription("IDP group name")
    val idp: String,
    @APIDescription("Yontrack group name")
    val group: String,
)
