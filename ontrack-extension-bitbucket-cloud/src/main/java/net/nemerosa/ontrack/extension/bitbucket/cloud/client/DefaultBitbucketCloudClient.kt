package net.nemerosa.ontrack.extension.bitbucket.cloud.client

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudAuthType
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfiguration
import net.nemerosa.ontrack.extension.bitbucket.cloud.model.*
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestTemplate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.reflect.KClass

class DefaultBitbucketCloudClient(
    private val configuration: BitbucketCloudConfiguration,
) : BitbucketCloudClient {

    companion object {
        const val ROOT_URI = "https://api.bitbucket.org"

        /**
         * `Authorization` header for a configuration: HTTP basic with the email for an API token,
         * Bearer for an access token.
         */
        fun authorizationHeader(configuration: BitbucketCloudConfiguration): String {
            val token = configuration.token?.takeIf { it.isNotBlank() }
                ?: error("Bitbucket Cloud configuration ${configuration.name} has no token.")
            return when (configuration.authType) {
                BitbucketCloudAuthType.API_TOKEN -> {
                    val email = configuration.email?.takeIf { it.isNotBlank() }
                        ?: error("Bitbucket Cloud configuration ${configuration.name} has no email for its API token.")
                    "Basic " + Base64.getEncoder().encodeToString("$email:$token".toByteArray(Charsets.UTF_8))
                }

                BitbucketCloudAuthType.ACCESS_TOKEN -> "Bearer $token"
            }
        }
    }

    override fun validate() {
        when (configuration.authType) {
            BitbucketCloudAuthType.API_TOKEN -> get<JsonNode>("/2.0/user")
            BitbucketCloudAuthType.ACCESS_TOKEN -> get<JsonNode>("/2.0/hook_events")
        }
    }

    override fun getRepositories(workspace: String): List<BitbucketCloudRepository> =
        paginate<BitbucketCloudRepository, BitbucketCloudRepositoryList> { page ->
            "/2.0/repositories/$workspace?page=$page"
        }

    override fun getRepositoryLastModified(repository: BitbucketCloudRepository): LocalDateTime? =
        LocalDateTime.parse(repository.updated_on, DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    override fun getRepositoryCreationDate(repository: BitbucketCloudRepository): LocalDateTime? =
        LocalDateTime.parse(repository.created_on, DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    override fun getRepository(workspace: String, repository: String): BitbucketCloudRepository =
        get("/2.0/repositories/$workspace/$repository")

    private inline fun <reified T, reified P : BitbucketCloudPaginatedList<T>> paginate(
        noinline path: (Int) -> String,
    ): List<T> = paginate(P::class, path)

    private fun <T, P : BitbucketCloudPaginatedList<T>> paginate(
        pageType: KClass<P>,
        path: (Int) -> String,
    ): List<T> {
        var page = 1
        val results = mutableListOf<T>()
        do {
            val list = get(pageType, path(page))
            results.addAll(list.values)
            page++
        } while (list.next != null)
        return results
    }

    private inline fun <reified T : Any> get(path: String): T =
        get(T::class, path)

    private fun <T : Any> get(responseType: KClass<T>, path: String): T =
        template.getForObject(path, responseType.java) ?: throw BitbucketCloudNoResponseException(path)

    internal val template: RestTemplate by lazy {
        RestTemplateBuilder()
            .rootUri(ROOT_URI)
            .defaultHeader(HttpHeaders.AUTHORIZATION, authorizationHeader(configuration))
            .build()
    }
}
