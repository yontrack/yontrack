package net.nemerosa.ontrack.kdsl.connector.support

import net.nemerosa.ontrack.kdsl.connector.Connector
import net.nemerosa.ontrack.kdsl.connector.ConnectorResponse
import net.nemerosa.ontrack.kdsl.connector.ConnectorResponseBody
import net.nemerosa.ontrack.kdsl.connector.FileContent
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForEntity
import org.springframework.web.client.postForEntity
import org.springframework.web.util.UriComponentsBuilder
import java.nio.charset.Charset


class DefaultConnector(
    override val url: String,
    private val defaultHeaders: Map<String, String> = emptyMap(),
) : Connector {

    constructor(
        url: String,
        token: String,
    ) : this(
        url = url,
        defaultHeaders = mapOf(X_ONTRACK_TOKEN to token),
    )

    companion object {
        const val X_ONTRACK_TOKEN = "X-Ontrack-Token"
    }

    override val token: String?
        get() = defaultHeaders[X_ONTRACK_TOKEN]

    @Suppress("VulnerableCodeUsages")
    override fun get(
        path: String,
        query: Map<String, String>,
        headers: Map<String, String>,
        noAuth: Boolean,
    ): ConnectorResponse {
        val uri = if (query.isNotEmpty()) {
            UriComponentsBuilder.fromPath(path)
                .apply {
                    query.forEach { (name, value) ->
                        queryParam(name, value)
                    }
                }
                .build()
                .toUriString()
        } else {
            path
        }
        val response = restTemplate(headers, noAuth).getForEntity<ByteArray>(uri)
        return RestTemplateConnectorResponse(response)
    }

    class RestTemplateConnectorResponse(
        private val response: ResponseEntity<ByteArray>,
    ) : ConnectorResponse {
        override val statusCode: Int
            get() = response.statusCode.value()
        override val body: ConnectorResponseBody = object : ConnectorResponseBody {
            override fun asTextOrNull(charset: Charset): String? =
                response.body?.toString(charset)

            override fun asText(charset: Charset): String =
                asTextOrNull(charset) ?: ""
        }
    }

    override fun post(path: String, headers: Map<String, String>, body: Any?): ConnectorResponse {
        val response = restTemplate(headers).postForEntity<ByteArray>(path, body)
        return RestTemplateConnectorResponse(response)
    }

    override fun put(path: String, headers: Map<String, String>, body: Any?) {
        restTemplate(headers).put(path, body)
    }

    override fun delete(path: String, headers: Map<String, String>) {
        restTemplate(headers).delete(path)
    }

    @Deprecated("Use uploadFile with the file content")
    override fun uploadFile(path: String, headers: Map<String, String>, file: Pair<String, ByteArray>) {
        val actualHeaders = headers.toMutableMap()
        actualHeaders["Content-Type"] = MediaType.MULTIPART_FORM_DATA.toString()

        val body: MultiValueMap<String, Any> = LinkedMultiValueMap()
        val (fileName, fileBytes) = file
        body.add(fileName, ByteArrayResource(fileBytes))

        val requestEntity: HttpEntity<MultiValueMap<String, Any>> = HttpEntity(body)

        restTemplate(actualHeaders).postForEntity(
            path,
            requestEntity,
            String::class.java
        )
    }

    override fun uploadFile(path: String, headers: Map<String, String>, file: FileContent) {
        val actualHeaders = headers.toMutableMap()
        actualHeaders["Content-Type"] = MediaType.MULTIPART_FORM_DATA.toString()

        val resource = object : ByteArrayResource(file.content) {
            override fun getFilename(): String = file.name
        }

        // File part
        val fileHeaders = HttpHeaders()
        fileHeaders.contentType = MediaType.parseMediaType(file.type)
        val fileEntity = HttpEntity<ByteArrayResource>(resource, fileHeaders)
        val body: MultiValueMap<String, Any> = LinkedMultiValueMap()
        body.add(file.name, fileEntity)

        val requestEntity: HttpEntity<MultiValueMap<String, Any>> = HttpEntity(body)

        restTemplate(actualHeaders).postForEntity(
            path,
            requestEntity,
            String::class.java
        )
    }

    private fun restTemplate(
        headers: Map<String, String>,
        noAuth: Boolean = false,
    ): RestTemplate {
        var builder = restTemplateBuilder().rootUri(url)
        defaultHeaders.forEach { (name, value) ->
            if ((name != "Authorization" && name != X_ONTRACK_TOKEN) || !noAuth) {
                builder = builder.defaultHeader(name, value)
            }
        }
        headers.forEach { (name, value) ->
            builder = builder.defaultHeader(name, value)
        }
        return builder.build()
    }
}