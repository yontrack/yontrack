package net.nemerosa.ontrack.model.deprecation

/**
 * External contract through which a deprecated item is reached.
 *
 * @property tag Value of the `surface` tag of the [DeprecationMetrics.usage] counter
 */
enum class DeprecationSurface(val tag: String) {
    /** GraphQL field or argument */
    GRAPHQL("graphql"),
    /** REST endpoint */
    REST("rest"),
    /** `ontrack.*` configuration property */
    CONFIG("config"),
    /** Configuration as Code key */
    CASC("casc"),
    /** Environment variable */
    ENV("env"),
    /** Templating function, source or field */
    TEMPLATING("templating"),
    /** Global settings stored in Yontrack */
    SETTINGS("settings"),
    /** Property stored on a project entity */
    PROPERTY("property"),
    /** CI configuration (`.yontrack/ci.yaml`) */
    CI_CONFIG("ci-config"),
}
