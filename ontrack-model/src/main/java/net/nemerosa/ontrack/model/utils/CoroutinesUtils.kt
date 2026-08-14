package net.nemerosa.ontrack.model.utils

import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.coroutines.CoroutineContext

private val logger = LoggerFactory.getLogger("net.nemerosa.ontrack.model.utils.Coroutines")

/**
 * Last-resort handler for coroutines launched without a parent scope.
 *
 * Without it, an uncaught failure is dumped raw on the standard error by the default thread
 * handler, escaping the application logs altogether.
 */
private val loggingExceptionHandler = CoroutineExceptionHandler { context, throwable ->
    logger.error("Uncaught error in coroutine [$context]", throwable)
}

/**
 * Launches some code as a coroutine while preserving the Spring security context.
 *
 * @param context Context where to execute the coroutine. Using the [IO context][Dispatchers.IO] by default,
 * whose number of threads can be configured (see docs).
 * @param code Code to run asynchronously
 * @return Coroutine job which can be used to check the completion
 */
fun launchWithSecurityContext(
    context: CoroutineContext = Dispatchers.IO,
    code: suspend CoroutineScope.() -> Unit,
): Job {
    val securityContext = SecurityContextHolder.getContext()
    return try {
        CoroutineScope(context + loggingExceptionHandler).launch {
            SecurityContextHolder.setContext(securityContext)
            code()
        }
    } finally {
        SecurityContextHolder.setContext(securityContext)
    }
}

fun <T> launchAsyncWithSecurityContext(
    context: CoroutineContext = Dispatchers.IO,
    code: suspend CoroutineScope.() -> T,
): Deferred<T> {
    val securityContext = SecurityContextHolder.getContext()
    return try {
        CoroutineScope(context).async {
            SecurityContextHolder.setContext(securityContext)
            code()
        }
    } finally {
        SecurityContextHolder.setContext(securityContext)
    }
}
