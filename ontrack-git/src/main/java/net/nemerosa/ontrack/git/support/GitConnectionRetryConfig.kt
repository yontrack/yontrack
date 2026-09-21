package net.nemerosa.ontrack.git.support

import com.fasterxml.jackson.annotation.JsonIgnore
import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.databind.annotation.JsonSerialize
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.annotations.APIIgnore
import net.nemerosa.ontrack.model.json.DurationDeserializer
import net.nemerosa.ontrack.model.json.DurationSerializer
import org.springframework.web.client.HttpStatusCodeException
import java.io.InterruptedIOException
import java.net.SocketException
import java.time.Duration

/**
 * Configurations for retrying Git connections.
 */
data class GitConnectionConfig(
    @APIDescription("List of retry rules")
    val retries: List<GitConnectionRetryConfig> = emptyList(),
) {
    companion object {
        val default = GitConnectionConfig(
            retries = listOf(
                GitConnectionRetryConfig(
                    httpCode = "5[\\d]{2}",
                    connectionError = true,
                ),
            )
        )
    }
}

/**
 * Single configuration for retrying Git connections.
 */
data class GitConnectionRetryConfig(
    @APIDescription("HTTP code to match (regular expression). If empty, no match on the HTTP code")
    val httpCode: String = "",
    @APIDescription("Error message to match (regular expression).")
    val errorMessage: String = ".*",
    @APIDescription("Specific retry limit. If empty, uses the default retry limit")
    val retryLimit: Int? = null,
    @APIDescription("Retry interval. If empty, uses the default retry interval")
    @JsonDeserialize(using = DurationDeserializer::class)
    @JsonSerialize(using = DurationSerializer::class)
    val retryInterval: Duration? = null,
    @APIDescription("If true, matches connection errors")
    val connectionError: Boolean = false,
) {

    @JsonIgnore
    @APIIgnore
    private val httpCodeRegex = httpCode.toRegex()

    @JsonIgnore
    @APIIgnore
    private val errorMessageRegex = errorMessage.toRegex()

    fun match(e: Throwable): Boolean {
        if (connectionError && (e is SocketException || e is InterruptedIOException)) {
            return true
        }
        if (httpCode.isNotBlank() && e is HttpStatusCodeException) {
            val code: String = e.statusCode.value().toString()
            return httpCodeRegex.matches(code) && (e.message?.let { errorMessageRegex.containsMatchIn(it) }
                ?: (errorMessage == ".*"))
        }
        return false
    }

    override fun toString(): String {
        val parts = mutableListOf<String>()

        if (connectionError) {
            parts.add("connection errors")
        }

        if (httpCode.isNotEmpty()) {
            parts.add("HTTP code matching '$httpCode'")
        }

        if (errorMessage != ".*") {
            parts.add("message matching '$errorMessage'")
        }

        val conditions = if (parts.isEmpty()) "any error" else parts.joinToString(" and ")

        val retry = buildString {
            retryLimit?.let { append("limit=$it") }
            retryInterval?.let {
                if (isNotEmpty()) append(", ")
                append("interval=$it")
            }
            if (isEmpty()) append("default")
        }

        return "GitConnectionRetryConfig(match: $conditions, retry: $retry)"
    }

}
