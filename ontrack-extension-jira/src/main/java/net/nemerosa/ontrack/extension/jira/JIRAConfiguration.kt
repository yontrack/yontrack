package net.nemerosa.ontrack.extension.jira

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.issues.model.IssueServiceConfiguration
import net.nemerosa.ontrack.model.annotations.APIIgnore
import net.nemerosa.ontrack.model.annotations.APILabel
import net.nemerosa.ontrack.model.support.UserPasswordConfiguration
import org.apache.commons.lang3.StringUtils

/**
 * JIRA configuration.
 *
 * @param name Name for this configuration
 * @param url URL to the JIRA server
 * @param apiUrl Optional alternative URL for the JIRA API
 * @param user Username for the connection to the JIRA server
 * @param password Password or token for the connection to the JIRA server
 * @param include List of regular expressions for the accepted JIRA projects (default = empty = all of them)
 * @param exclude List of regular expressions for the JIRA projects to not accept (default = empty = none of them)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
open class JIRAConfiguration(
    name: String,
    val url: String,
    @APIDescription("Optional alternative URL for the JIRA API")
    @APILabel("API URL")
    val apiUrl: String? = null,
    user: String?,
    password: String?,
    val include: List<String> = emptyList(),
    val exclude: List<String> = emptyList(),
) : UserPasswordConfiguration<JIRAConfiguration>(name, user, password), IssueServiceConfiguration {

    @APIIgnore
    private val includeRegexes: List<Regex> by lazy {
        include.map { adjustRegexForProject(it).toRegex() }
    }

    @APIIgnore
    private val excludeRegexes: List<Regex> by lazy {
        exclude.map { adjustRegexForProject(it).toRegex() }
    }

    private fun adjustRegexForProject(regex: String): String {
        var result = regex
        if (!regex.startsWith("^")) {
            result = "^${result}"
        }
        if (!regex.endsWith("$")) {
            result += "$"
        }
        return result
    }

    override fun obfuscate(): JIRAConfiguration {
        return JIRAConfiguration(
            name = name,
            url = url,
            apiUrl = apiUrl,
            user = user,
            password = "",
            include = include,
            exclude = exclude,
        )
    }

    override fun withPassword(password: String?): JIRAConfiguration {
        return JIRAConfiguration(
            name = name,
            url = url,
            apiUrl = apiUrl,
            user = user,
            password = password,
            include = include,
            exclude = exclude,
        )
    }

    @APIIgnore
    override val serviceId: String = JIRAServiceExtension.SERVICE

    fun getIssueURL(key: String): String {
        val base = url
        return if (StringUtils.isNotBlank(base)) {
            String.format("%s/browse/%s", base, key)
        } else {
            key
        }
    }

    fun isValidIssueKey(token: String): Boolean {
        if (token.isNotBlank() && token.matches(ISSUE_PATTERN_REGEX)) {
            // Project name
            val project = token.substringBefore("-")
            // Included?
            if (include.isNotEmpty() && includeRegexes.none { it.matches(project) }) {
                return false
            }
            // Excluded?
            if (exclude.isNotEmpty() && excludeRegexes.any { it.matches(project) }) {
                return false
            }
            // OK
            return true
        } else {
            return false
        }
    }

    companion object {

        /**
         * The delimiters around a key are lookarounds, not consumed, so that two keys separated
         * by a single character - `ABC-1,ABC-2` - are both found.
         */
        val ISSUE_PATTERN_REGEX = "(?<![A-Z0-9])([A-Z][A-Z0-9]+-\\d+)(?![0-9])".toRegex()

    }
}
