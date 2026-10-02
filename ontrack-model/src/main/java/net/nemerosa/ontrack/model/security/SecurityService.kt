package net.nemerosa.ontrack.model.security

import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.Signature

interface SecurityService {

    /**
     * Checks that the current user is authenticated
     */
    fun checkAuthenticated()

    fun checkGlobalFunction(fn: Class<out GlobalFunction>)
    fun isGlobalFunctionGranted(fn: Class<out GlobalFunction>): Boolean

    fun checkProjectFunction(projectId: Int, fn: Class<out ProjectFunction>)
    fun checkProjectFunction(entity: ProjectEntity, fn: Class<out ProjectFunction>) {
        checkProjectFunction(entity.projectId(), fn)
    }

    fun isProjectFunctionGranted(projectId: Int, fn: Class<out ProjectFunction>): Boolean
    fun isProjectFunctionGranted(entity: ProjectEntity, fn: Class<out ProjectFunction>): Boolean {
        return isProjectFunctionGranted(entity.projectId(), fn)
    }

    /**
     * Returns the current logged user or null if none is logged.
     */
    val currentUser: AuthenticatedUser?

    /**
     * Is the current user logged?
     */
    val isLogged: Boolean
        get() = currentUser != null

    val currentSignature: Signature

    /**
     * Who is acting in the current context and through which channel, or null when nobody is
     * authenticated.
     *
     * An authentication which does not say how its account got in gives the account acting as
     * system, see [Actor.degraded].
     */
    val currentActor: Actor?

    /**
     * Performs a call as admin. This is mainly by internal code to access parts of the application
     * which are usually protected from the current context, but that need to be accessed locally.
     *
     * @param supplier Call to perform in a protected context
     * @param <T>      Type of data to get back
     * @return A new supplier running in a new security context
    </T> */
    fun <T> runAsAdmin(supplier: () -> T): () -> T = runAsAdmin(null, supplier)

    /**
     * Performs a call as admin, for a given reason.
     *
     * The [actor][currentActor] of the call is the system acting for this [reason], on behalf of
     * the current actor, see [Actor.runAs].
     *
     * @param reason Why the system acts as admin (like `auto-promotion`), null if not known
     * @param supplier Call to perform in a protected context
     * @return A new supplier running in a new security context
     */
    fun <T> runAsAdmin(reason: String?, supplier: () -> T): () -> T

    fun <T> asAdmin(supplier: () -> T): T = runAsAdmin(supplier)()

    /**
     * Performs a call as admin, for a given reason.
     *
     * @param reason Why the system acts as admin (like `auto-promotion`)
     * @param supplier Call to perform in a protected context
     * @see runAsAdmin
     */
    fun <T> asAdmin(reason: String, supplier: () -> T): T = runAsAdmin(reason, supplier)()


    /**
     * In some asynchronous operations, we need to run a task with the same credentials that initiated the operation.
     * This method creates a wrapping supplier that holds the initial security context.
     *
     * @param fn  Call to perform in a protected context
     * @param <T> Type of data to get back
     * @return A new function running in a new security context
     */
    fun <T, R> runner(fn: (T) -> R): (T) -> R
}