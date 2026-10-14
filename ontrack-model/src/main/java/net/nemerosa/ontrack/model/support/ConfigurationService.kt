package net.nemerosa.ontrack.model.support

/**
 * Defines what the management of configurations must be.
 *
 * @param T Type of configuration
 */
interface ConfigurationService<T : Configuration<T>> {

    /**
     * ID (type) of the configuration service.
     *
     * This is used for the generic API to manage the configurations.
     */
    val type: String

    /**
     * Gets the list of configurations.
     */
    val configurations: List<T>

    /**
     * Saves a _new_ configuration.
     *
     * @param configuration Configuration to save
     * @return Saved configuration
     */
    fun newConfiguration(configuration: T): T

    /**
     * Gets a configuration by its name and fails if not found.
     *
     * Note that the returned configuration is *not* obfuscated. It can be used internally safely
     * and will be obfuscated whenever sent to the client.
     *
     * @param name Name of the configuration to find
     * @return Found configuration
     * @throws ConfigurationNotFoundException If the configuration cannot be found
     */
    fun getConfiguration(name: String): T

    /**
     * Same than [getConfiguration] but returns null if not found.
     *
     * @param name Name of the configuration to find
     * @return The configuration or null if not found
     */
    fun findConfiguration(name: String): T?

    /**
     * Deletes a configuration
     *
     * @param name Name of the configuration to delete
     */
    fun deleteConfiguration(name: String)

    /**
     * Tests a configuration
     */
    fun test(configuration: T): ConnectionResult

    /**
     * Gets the former password if new password is blank for the same user. For a new user,
     * a blank password can be accepted.
     */
    fun updateConfiguration(name: String, configuration: T)

    /**
     * Type of configuration handled by this service
     */
    val configurationType: Class<T>

    /**
     * Given a configuration, optionally returns some extra data about it.
     *
     * @param config Configuration
     * @return Extra data (or null if there is none)
     */
    fun getConfigExtraData(config: T): Any? = null
}