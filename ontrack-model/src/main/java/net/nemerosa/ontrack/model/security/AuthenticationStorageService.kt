package net.nemerosa.ontrack.model.security

import kotlin.random.Random

/**
 * Service used to store and restore an authentication across boundaries (like queuing)
 */
interface AuthenticationStorageService {

    /**
     * Gets the ID of the current authentication so that it can be restored later on.
     *
     * Fails if no authentication is available.
     */
    fun getAccountId(): String

    /**
     * Gets the actor of the current authentication so that it can be restored later on.
     *
     * Fails if no authentication is available.
     */
    fun getActor(): Actor

    /**
     * Given an account ID, executes some code with the associated account ID.
     *
     * @param accountId Account ID, as given by [getAccountId]
     * @param actor Actor, as given by [getActor]. When null, or when it is not the actor of this
     * account, the code runs as the account acting as system ([Actor.degraded]), or as the system
     * for a run-as administrator.
     * @param code Code to run
     */
    fun withAccountId(accountId: String, actor: Actor?, code: () -> Unit)

    companion object {
        /**
         * Known ID of the run-as admin
         */
        val RUN_AS_ADMINISTRATOR_ACCOUNT_ID = Random.nextInt(1_000_000).toString(10)
    }
}