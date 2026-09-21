package net.nemerosa.ontrack.extension.sonarqube.client

import net.nemerosa.ontrack.extension.sonarqube.client.model.Analysis
import net.nemerosa.ontrack.extension.sonarqube.client.model.MeasureSearchHistory
import net.nemerosa.ontrack.extension.sonarqube.client.model.PagedResult
import net.nemerosa.ontrack.extension.sonarqube.client.model.ProjectAnalysisSearch
import net.nemerosa.ontrack.extension.sonarqube.configuration.SonarQubeConfiguration
import net.nemerosa.ontrack.json.asJson
import org.slf4j.LoggerFactory
import net.nemerosa.ontrack.extension.support.client.restTemplateBuilder
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.DefaultUriBuilderFactory

class SonarQubeClientImpl(
    configuration: SonarQubeConfiguration
) : SonarQubeClient {

    private val logger = LoggerFactory.getLogger(SonarQubeClient::class.java)

    override val serverVersion: String
        get() = restTemplate.getForObject("/api/server/version", String::class.java)!!

    override fun getMeasuresForVersion(
        key: String,
        branch: String,
        version: String,
        metrics: List<String>
    ): Map<String, Double?>? {

        if (logger.isDebugEnabled) {
            logger.debug("Getting measures,key=$key,branch=$branch,version=$version,metrics=$metrics")
        }

        val analysis: Analysis? = paginateUntil(
            ProjectAnalysisSearch::class.java,
            uri = { page ->
                "/api/project_analyses/search?project={project}&branch={branch}&category=VERSION&p={page}" to mapOf(
                    "project" to key,
                    "branch" to branch,
                    "page" to page
                )
            },
            search = { result ->
                result.analyses.find { analysis ->
                    analysis.events.any { event -> event.name == version }
                }
            }
        )

        if (analysis != null) {
            if (logger.isDebugEnabled) {
                logger.debug("Getting analysis,key=$key,branch=$branch,version=$version,metrics=$metrics,analysis=${analysis.asJson()}")
            }
            // Timestamp of the version
            val timestamp = analysis.date
            // History measures
            val measures: MeasureSearchHistory = restTemplate.getForObject(
                "/api/measures/search_history?component={component}&branch={branch}&metrics={metrics}&from={timestamp}&to={timestamp}",
                MeasureSearchHistory::class.java,
                mapOf(
                    "component" to key,
                    "branch" to branch,
                    "metrics" to metrics.joinToString(","),
                    "timestamp" to timestamp
                )
            )!!
            if (logger.isDebugEnabled) {
                logger.debug("Getting measures,key=$key,branch=$branch,version=$version,metrics=$metrics,measures=${measures.measures.asJson()}")
            }
            // Converts to measures
            val finalMeasures = measures.measures.associate { measure ->
                val name = measure.metric
                val value = measure.history.firstOrNull()?.value
                if (value != null) {
                    try {
                        name to value.toDouble()
                    } catch (ex: NumberFormatException) {
                        name to null
                    }
                } else {
                    name to null
                }
            }
            if (logger.isDebugEnabled) {
                logger.debug("Final measures,key=$key,branch=$branch,version=$version,metrics=$metrics,measures=${finalMeasures.asJson()}")
            }
            return finalMeasures
        } else {
            return null
        }

    }

    internal val restTemplate: RestTemplate = restTemplateBuilder()
        .rootUri(configuration.url)
        // SonarQube requires a strict encoding per value (esp. for "+" characters which are no longer encoded with Spring 5)
        // See https://github.com/spring-projects/spring-framework/issues/20750
        .uriTemplateHandler(DefaultUriBuilderFactory().apply {
            encodingMode = DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY
        })
        .basicAuthentication(requireNotNull(configuration.password) { "Username must not be null" }, "") // See https://docs.sonarqube.org/latest/extend/web-api/
        .build()

    private fun <T, R : PagedResult> paginateUntil(
        resultType: Class<R>,
        uri: (page: Int) -> Pair<String, Map<String, Any>>,
        search: (result: R) -> T?
    ): T? {
        var page = 1
        var result: T? = null
        while (result == null) {
            // URI to call
            val url = uri(page++)
            // Getting the page
            val pageResult: R? = try {
                restTemplate.getForObject(url.first, resultType, url.second)
            } catch (ex: HttpClientErrorException) {
                if (ex.statusCode == HttpStatus.NOT_FOUND) {
                    null
                } else {
                    throw ex
                }
            }
            // Empty results?
            if (pageResult == null || pageResult.isEmpty) {
                return null
            }
            // Gets a result in there
            result = search(pageResult)
        }
        // Result
        return result
    }

}