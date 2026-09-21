package net.nemerosa.ontrack.extension.casc.context.core.admin

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.syncForward
import net.nemerosa.ontrack.extension.casc.context.AbstractCascContext
import net.nemerosa.ontrack.json.JsonParseException
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.json.schema.JsonArrayType
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import net.nemerosa.ontrack.model.security.AccountGroupInput
import net.nemerosa.ontrack.model.security.AccountService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Account groups as code.
 */
@Component
class AccountGroupsAdminContext(
    private val accountService: AccountService,
) : AbstractCascContext(), SubAdminContext {

    companion object {
        const val PRIORITY = 10
    }

    private val logger: Logger = LoggerFactory.getLogger(AccountGroupsAdminContext::class.java)

    override val field: String = "groups"

    override val priority: Int = PRIORITY

    override fun jsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType {
        return JsonArrayType(
            items = jsonTypeBuilder.toType(AccountGroupInput::class),
            description = "List of account groups",
        )
    }

    override fun run(node: JsonNode, paths: List<String>) {
        // Items to provision
        val items = node.mapIndexed { index, child ->
            try {
                child.parse<AccountGroupInput>()
            } catch (ex: JsonParseException) {
                throw IllegalStateException(
                    "Cannot parse into ${AccountGroupInput::class.qualifiedName}: ${path(paths + index.toString())}",
                    ex
                )
            }
        }
        // Existing items
        val existing = existingGroups()
        // Synchronizing, preserving the existing groups
        syncForward(
            from = items,
            to = existing
        ) {
            equality { a, b -> a.name == b.name }
            onCreation { item ->
                logger.info("Creating account group: ${item.name}")
                accountService.createGroup(item)
            }
            onModification { item, _ ->
                logger.info("Updating account group: ${item.name}")
                val existingGroup = accountService.findAccountGroupByName(item.name)
                if (existingGroup != null) {
                    accountService.updateGroup(existingGroup.id, item)
                }
            }
            onDeletion { existing ->
                logger.info("Preserving existing group: ${existing.name}")
            }
        }
    }

    override fun render(): JsonNode = existingGroups().asJson()

    private fun existingGroups() = accountService.accountGroups.map { group ->
        AccountGroupInput(
            name = group.name,
            description = group.description,
        )
    }
}