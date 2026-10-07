package net.nemerosa.ontrack.repository

import net.nemerosa.ontrack.model.Ack
import net.nemerosa.ontrack.model.security.Account
import net.nemerosa.ontrack.model.security.AccountGroup
import net.nemerosa.ontrack.model.structure.ID

interface AccountRepository {

    /**
     * Gets the list of all accounts
     */
    fun findAll(): Collection<Account>

    /**
     * Creates a new account
     */
    fun newAccount(account: Account): Account

    /**
     * Edits an existing account: its full name, its email, and for an agent, its tool and its description.
     * Neither its kind nor its owner change.
     */
    fun saveAccount(account: Account)

    /**
     * Deletes an account
     */
    fun deleteAccount(accountId: ID): Ack

    /**
     * Loads an account by ID
     */
    fun getAccount(accountId: ID): Account

    /**
     * Looks for accounts based on some text.
     */
    fun findByNameToken(token: String): List<Account>

    /**
     * Gets the list of accounts associated with this account group.
     *
     * @param accountGroup                 Account group
     * @return List of accounts
     */
    fun getAccountsForGroup(accountGroup: AccountGroup): List<Account>

    /**
     * Finds an account using its name only.
     */
    fun findAccountByName(email: String): Account?

    /**
     * Checks if an account ID exists
     */
    fun doesAccountIdExist(id: ID): Boolean

    /**
     * Finds or create the account
     */
    fun findOrCreateAccount(account: Account): Account

    /**
     * Gets the agent accounts, ordered by identifier.
     *
     * @param ownerId Restricts the list to the agents of this owner
     */
    fun findAgents(ownerId: ID? = null): List<Account>

    /**
     * Sets the owner of an agent account.
     */
    fun setOwner(agentId: ID, ownerId: ID)
}