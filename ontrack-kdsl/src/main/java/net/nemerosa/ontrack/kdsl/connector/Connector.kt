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
     * Headers sent with every call, the token included
     */
    val headers: Map<String, String>
        get() = token?.let { mapOf("X-Ontrack-Token" to it) } ?: emptyMap()

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
     * Deletes a resource at a relative URL.
     *
     * @return Response, with the body the server answered, if any
     */
    fun delete(
        path: String,
        headers: Map<String, String> = emptyMap()
    ): ConnectorResponse

    /**
     * Uploading a single file using REST, as a `multipart/form-data` request.
     *
     * @param path Relative URL
     * @param headers Additional headers
     * @param file File to upload, sent in the part named after [FileContent.name]
     * @param fields Text fields sent along the file, each in its own part
     * @return Response of the upload
     */
    fun uploadFile(
        path: String,
        headers: Map<String, String> = emptyMap(),
        file: FileContent,
        fields: Map<String, String> = emptyMap(),
    ): ConnectorResponse

}