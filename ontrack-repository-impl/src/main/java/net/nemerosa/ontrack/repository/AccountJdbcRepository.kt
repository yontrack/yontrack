package net.nemerosa.ontrack.repository

import net.nemerosa.ontrack.model.Ack
import net.nemerosa.ontrack.model.exceptions.AccountNameAlreadyDefinedException
import net.nemerosa.ontrack.model.exceptions.AccountNotFoundException
import net.nemerosa.ontrack.model.security.Account
import net.nemerosa.ontrack.model.security.AccountGroup
import net.nemerosa.ontrack.model.security.AccountKind
import net.nemerosa.ontrack.model.security.SecurityRole
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.ID.Companion.of
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.apache.commons.lang3.StringUtils
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import javax.sql.DataSource

@Repository
class AccountJdbcRepository(
    dataSource: DataSource
) : AbstractJdbcRepository(dataSource), AccountRepository {

    companion object {
        /**
         * Selection of the accounts, with the name and email of the owner of an agent.
         */
        private const val SELECT = """
            SELECT A.*, O.FULLNAME AS OWNER_FULLNAME, O.EMAIL AS OWNER_EMAIL
            FROM ACCOUNTS A
            LEFT JOIN ACCOUNTS O ON O.ID = A.OWNER_ID
        """
    }

    private fun toAccount(rs: ResultSet): Account {
        val ownerId = rs.getInt("owner_id").takeIf { !rs.wasNull() }
        return Account(
            id = id(rs),
            fullName = rs.getString("fullName"),
            email = rs.getString("email"),
            // Only USER roles can be loaded from the database
            role = SecurityRole.USER,
            kind = AccountKind.valueOf(rs.getString("kind")),
            owner = ownerId?.let {
                Account(
                    id = of(it),
                    fullName = rs.getString("owner_fullName"),
                    email = rs.getString("owner_email"),
                    role = SecurityRole.USER,
                )
            },
            agentTool = rs.getString("agent_tool"),
            agentDescription = rs.getString("agent_description"),
        )
    }

    override fun findAll(): Collection<Account> {
        return jdbcTemplate!!.query(
            "$SELECT ORDER BY A.EMAIL"
        ) { rs: ResultSet, _ ->
            toAccount(rs)
        }.filterNotNull()
    }

    override fun newAccount(account: Account): Account {
        return try {
            val id = dbCreate(
                """
                    INSERT INTO ACCOUNTS (FULLNAME, EMAIL, KIND, OWNER_ID, AGENT_TOOL, AGENT_DESCRIPTION)
                    VALUES (:fullName, :email, :kind, :ownerId, :agentTool, :agentDescription)
                """,
                params("fullName", account.fullName)
                    .addValue("email", account.email)
                    .addValue("kind", account.kind.name)
                    .addValue("ownerId", account.owner?.id())
                    .addValue("agentTool", account.agentTool)
                    .addValue("agentDescription", account.agentDescription)
            )
            account.withId(of(id))
        } catch (_: DuplicateKeyException) {
            throw AccountNameAlreadyDefinedException(account.email)
        }
    }

    override fun saveAccount(account: Account) {
        try {
            namedParameterJdbcTemplate!!.update(
                """
                    UPDATE ACCOUNTS SET
                        FULLNAME = :fullName,
                        EMAIL = :email,
                        AGENT_TOOL = :agentTool,
                        AGENT_DESCRIPTION = :agentDescription
                    WHERE ID = :id
                """,
                params("id", account.id())
                    .addValue("fullName", account.fullName)
                    .addValue("email", account.email)
                    .addValue("agentTool", account.agentTool)
                    .addValue("agentDescription", account.agentDescription)
            )
        } catch (_: DuplicateKeyException) {
            throw AccountNameAlreadyDefinedException(account.email)
        }
    }

    override fun deleteAccount(accountId: ID): Ack {
        return Ack.one(
            namedParameterJdbcTemplate!!.update(
                "DELETE FROM ACCOUNTS WHERE ID = :id",
                params("id", accountId.value)
            )
        )
    }

    override fun getAccount(accountId: ID): Account {
        return getFirstItem(
            "$SELECT WHERE A.ID = :id",
            params("id", accountId.value)
        ) { rs: ResultSet, _ ->
            toAccount(rs)
        } ?: throw AccountNotFoundException(accountId.value)
    }

    override fun doesAccountIdExist(id: ID): Boolean {
        return getFirstItem(
            "SELECT ID FROM ACCOUNTS WHERE ID = :id",
            params("id", id.value),
            Int::class.java
        ) != null
    }

    override fun findByNameToken(token: String): List<Account> {
        return namedParameterJdbcTemplate!!.query(
            "$SELECT WHERE LOWER(A.EMAIL) LIKE :filter ORDER BY A.EMAIL",
            params("filter", String.format("%%%s%%", StringUtils.lowerCase(token)))
        ) { rs: ResultSet, _ ->
            toAccount(rs)
        }
    }

    override fun getAccountsForGroup(accountGroup: AccountGroup): List<Account> {
        return namedParameterJdbcTemplate!!.query(
            """
                $SELECT
                INNER JOIN ACCOUNT_GROUP_LINK L ON L.ACCOUNT = A.ID
                WHERE L.ACCOUNTGROUP = :accountGroupId
                ORDER BY A.EMAIL
            """,
            params("accountGroupId", accountGroup.id())
        ) { rs: ResultSet, _ ->
            toAccount(rs)
        }
    }

    override fun findAccountByName(email: String): Account? {
        return getFirstItem(
            "$SELECT WHERE A.EMAIL = :email",
            params("email", email)
        ) { rs, _ ->
            toAccount(rs)
        }
    }

    override fun findOrCreateAccount(account: Account): Account {
        val id = namedParameterJdbcTemplate!!.queryForObject(
            """
                INSERT INTO ACCOUNTS (FULLNAME, EMAIL)
                VALUES (:fullName, :email)
                ON CONFLICT (EMAIL)
                DO UPDATE SET EMAIL = EXCLUDED.EMAIL
                RETURNING ID
            """.trimIndent(),
            params("fullName", account.fullName)
                .addValue("email", account.email),
            Int::class.java
        ) ?: error("Cannot get or create account")
        return getAccount(of(id))
    }

    override fun findAgents(ownerId: ID?): List<Account> {
        val criteria = if (ownerId != null) " AND A.OWNER_ID = :ownerId" else ""
        return namedParameterJdbcTemplate!!.query(
            "$SELECT WHERE A.KIND = :kind$criteria ORDER BY A.EMAIL",
            params("kind", AccountKind.AGENT.name)
                .addValue("ownerId", ownerId?.value)
        ) { rs: ResultSet, _ ->
            toAccount(rs)
        }
    }

    override fun setOwner(agentId: ID, ownerId: ID) {
        namedParameterJdbcTemplate!!.update(
            "UPDATE ACCOUNTS SET OWNER_ID = :ownerId WHERE ID = :id AND KIND = :kind",
            params("id", agentId.value)
                .addValue("ownerId", ownerId.value)
                .addValue("kind", AccountKind.AGENT.name)
        )
    }
}
