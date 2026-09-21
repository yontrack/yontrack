package net.nemerosa.ontrack.repository

import net.nemerosa.ontrack.common.syncForward
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.springframework.stereotype.Repository
import javax.sql.DataSource

@Repository
class AccountIdpGroupJdbcRepository(dataSource: DataSource) : AbstractJdbcRepository(dataSource),
    AccountIdpGroupRepository {

    override fun syncGroups(accountId: Int, idpGroups: List<String>) {
        val existingGroups = getAccountIdpGroups(accountId)
        syncForward(
            from = idpGroups,
            to = existingGroups
        ) {
            equality { a, b -> a == b }
            onCreation { name ->
                val nameMaxLength = 255
                val actualName = name.take(nameMaxLength)
                namedParameterJdbcTemplate!!.update(
                    """
                        INSERT INTO ACCOUNT_IDP_GROUPS(ACCOUNT_ID, NAME) 
                        VALUES (:accountId, :name)
                    """.trimIndent(),
                    mapOf(
                        "accountId" to accountId,
                        "name" to actualName,
                    )
                )
            }
            onDeletion { existingName ->
                namedParameterJdbcTemplate!!.update(
                    """
                        DELETE FROM ACCOUNT_IDP_GROUPS
                        WHERE ACCOUNT_ID = :accountId
                        AND NAME = :name
                    """.trimIndent(),
                    mapOf(
                        "accountId" to accountId,
                        "name" to existingName
                    )
                )
            }
        }
    }

    override fun getAccountIdpGroups(accountId: Int): List<String> {
        return namedParameterJdbcTemplate!!.queryForList(
            """
                SELECT NAME
                FROM ACCOUNT_IDP_GROUPS
                WHERE ACCOUNT_ID = :accountId
                ORDER BY NAME
            """.trimIndent(),
            mapOf("accountId" to accountId),
            String::class.java
        ).filterNotNull()
    }
}