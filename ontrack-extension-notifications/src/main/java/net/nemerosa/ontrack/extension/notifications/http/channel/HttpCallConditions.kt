package net.nemerosa.ontrack.extension.notifications.http.channel

import net.nemerosa.ontrack.extension.notifications.http.settings.HttpNotificationSettings
import java.time.Duration
import java.time.Instant

/**
 * Outcome of the conditions of a call on one response (decision 17).
 */
enum class HttpCallConditionOutcome {
    /**
     * The success condition holds.
     */
    SUCCESS,

    /**
     * The failure condition holds.
     */
    FAILURE,

    /**
     * Neither condition holds: the call is still pending.
     */
    PENDING,
}

/**
 * Compiled conditions of a call.
 *
 * @property success Success condition, required to wait
 * @property failure Optional failure condition
 * @property statusUrl Optional status URL, for a `POST` or a `PUT`
 */
class HttpCallConditions(
    val success: HttpCallCelCondition,
    val failure: HttpCallCelCondition?,
    val statusUrl: HttpCallCelStatusUrl?,
) {

    /**
     * Evaluates the conditions on one response. The failure condition is evaluated first: when both
     * hold, the call fails.
     *
     * @throws HttpCallCelEvaluationException When a condition cannot be evaluated
     */
    fun evaluate(context: HttpCallCelContext): HttpCallConditionOutcome = when {
        failure != null && failure.evaluate(context) -> HttpCallConditionOutcome.FAILURE
        success.evaluate(context) -> HttpCallConditionOutcome.SUCCESS
        else -> HttpCallConditionOutcome.PENDING
    }

    companion object {

        const val SUCCESS_CONDITION = "Success condition"
        const val FAILURE_CONDITION = "Failure condition"

        /**
         * Compiles and checks the conditions of a call.
         *
         * @return Conditions, or `null` when there is no success condition: the call is not awaited beyond
         * its response
         * @throws HttpCallConfigException When an expression is not valid, or when the conditions do not fit
         * together
         */
        fun compile(config: HttpNotificationChannelConfig): HttpCallConditions? {
            val successCondition = config.successCondition.orNullIfBlank()
            val failureCondition = config.failureCondition.orNullIfBlank()
            val statusUrl = config.statusUrl.orNullIfBlank()

            if (config.intervalSeconds < HttpNotificationChannelConfig.MIN_INTERVAL_SECONDS) {
                throw HttpCallConfigException(
                    "The interval must be at least ${HttpNotificationChannelConfig.MIN_INTERVAL_SECONDS} seconds."
                )
            }
            if (config.timeoutSeconds < config.intervalSeconds) {
                throw HttpCallConfigException(
                    "The timeout (${config.timeoutSeconds} s) must be at least the interval (${config.intervalSeconds} s)."
                )
            }

            if (successCondition == null) {
                if (failureCondition != null) {
                    throw HttpCallConfigException("A failure condition requires a success condition.")
                }
                if (statusUrl != null) {
                    throw HttpCallConfigException("A status URL requires a success condition.")
                }
                return null
            }
            if (statusUrl != null && config.method == HttpCallMethod.GET) {
                throw HttpCallConfigException("A status URL is only used by POST and PUT: a GET polls its own URL.")
            }
            return HttpCallConditions(
                success = HttpCallCel.compileCondition(SUCCESS_CONDITION, successCondition),
                failure = failureCondition?.let { HttpCallCel.compileCondition(FAILURE_CONDITION, it) },
                statusUrl = statusUrl?.let { HttpCallCel.compileStatusUrl(it) },
            )
        }

        private fun String?.orNullIfBlank() = this?.takeIf { it.isNotBlank() }
    }
}

/**
 * Timeout of the wait of a call: the one of the subscription, capped by the maximum of the settings.
 */
fun HttpNotificationChannelConfig.effectiveTimeoutSeconds(settings: HttpNotificationSettings): Int =
    timeoutSeconds.coerceAtMost(settings.maxTimeoutSeconds)

/**
 * Time, as the polling of the `http` channel sees it: the tests replace it to poll without waiting.
 */
interface HttpCallPollingClock {

    fun now(): Instant

    fun sleep(duration: Duration)

    companion object {
        /**
         * Actual time.
         */
        val SYSTEM = object : HttpCallPollingClock {
            override fun now(): Instant = Instant.now()
            override fun sleep(duration: Duration) = Thread.sleep(duration.toMillis())
        }
    }
}
