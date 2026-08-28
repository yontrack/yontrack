package net.nemerosa.ontrack.extension.av.versionrules

/**
 * Access to the available [AutoVersioningVersionRule] services.
 */
interface AutoVersioningVersionRuleRegistry {

    /**
     * Gets an [AutoVersioningVersionRule] using its ID
     *
     * @param id ID of the [AutoVersioningVersionRule] to find
     * @return `null` if not found
     */
    fun <T> findVersionRuleById(id: String): AutoVersioningVersionRule<T>?

    /**
     * Gets the list of all available [AutoVersioningVersionRule] services.
     */
    val allVersionRules: List<AutoVersioningVersionRule<*>>

}
