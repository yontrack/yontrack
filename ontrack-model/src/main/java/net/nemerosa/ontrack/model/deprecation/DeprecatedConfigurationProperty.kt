package net.nemerosa.ontrack.model.deprecation

/**
 * Deprecated `ontrack.*` configuration property.
 *
 * @property name Canonical name of the property, in kebab-case (e.g. `ontrack.config.search.index.immediate`)
 * @property message Deprecation marker: `Removed in V<N>. Use X instead. See #NNNN`
 */
data class DeprecatedConfigurationProperty(
    val name: String,
    val message: String,
)

/**
 * Declares deprecated configuration properties. Each of them which is set is reported once at startup.
 */
interface DeprecatedConfigurationPropertiesProvider {
    val deprecatedConfigurationProperties: List<DeprecatedConfigurationProperty>
}
