package net.nemerosa.ontrack.kdsl.connector

/**
 * Raw HTTP connection.
 */
interface Connector {

    /**
     * Associated URL
     */
    val url: String

    /**
     * Associated token, if any
     */
    val token: String?

    /**
     * Gets some content from a relative URL.
     */
    fun get(
        path: String,
        query: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        noAuth: Boolean = false,
    ): ConnectorResponse

    /**
     * Post a payload to a relative URL.
     */
    fun post(
        path: String,
        headers: Map<String, String> = emptyMap(),
        body: Any? = null,
    ): ConnectorResponse

    /**
     * Puts a payload to a relative URL.
     */
    fun put(
        path: String,
        headers: Map<String, String> = emptyMap(),
        body: Any? = null,
    )

    /**
     * Deletes a payload to a relative URL.
     */
    fun delete(
        path: String,
        headers: Map<String, String> = emptyMap()
    )

    /**
     * Uploading a single file using REST
     */
    fun uploadFile(
        path: String,
        headers: Map<String, String> = emptyMap(),
        file: FileContent,
    )

}