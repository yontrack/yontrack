package net.nemerosa.ontrack.model.support

import tools.jackson.databind.JsonNode

interface ConfigurationRepository {
    /**
     * Gets the list of items for this configuration class
     */
    fun <T : Configuration<T>> list(configurationClass: Class<T>): List<T>

    /**
     * Gets a configuration using its name
     */
    fun <T : Configuration<T>> find(configurationClass: Class<T>, name: String): T?

    /**
     * Saves or creates a configuration
     */
    fun <T : Configuration<T>> save(configuration: T): T

    /**
     * Deletes a configuration
     */
    fun <T : Configuration<T>> delete(configurationClass: Class<T>, name: String)

    /**
     * Method used to migrate existing configurations using their JSON representation as a source
     * and rewrites them before saving them.
     *
     * @param configurationClass Type of the configuration
     * @param migration This code takes the raw JSON representation and converts it into a new format just before
     * being saved. If it returns null, the configuration does not have to change.
     */
    fun <T : Configuration<T>> migrate(
        configurationClass: Class<T>,
        migration: (raw: JsonNode) -> T?
    )
}