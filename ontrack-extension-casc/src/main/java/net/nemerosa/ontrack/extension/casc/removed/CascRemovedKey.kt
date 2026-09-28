package net.nemerosa.ontrack.extension.casc.removed

/**
 * A CasC key which Yontrack no longer supports but still tolerates: a CasC file carrying it is not
 * rejected, the key is ignored with a warning (see [CascRemovedKeys]).
 *
 * To register a removed key, declare it as a Spring bean, for example in a `@Configuration` class:
 *
 * ```kotlin
 * @Bean
 * fun myRemovedKey() = CascRemovedKey.settings("my-settings", removedIn = "6.0")
 * ```
 *
 * @property path Path of the key from the root of the CasC, for example
 * `ontrack`, `config`, `settings`, `my-settings`
 * @property removedIn Version in which the key was removed
 */
data class CascRemovedKey(
    val path: List<String>,
    val removedIn: String,
) {

    init {
        require(path.isNotEmpty()) { "The path of a removed CasC key cannot be empty." }
    }

    /**
     * Path of the key, as CasC errors display it
     */
    val display: String get() = path.joinToString("/")

    /**
     * Warning logged when the key is found in a CasC file
     */
    val warning: String get() = "CasC key $display is ignored: it was removed in $removedIn."

    companion object {

        /**
         * A removed key of the `ontrack.config.settings` section
         */
        fun settings(field: String, removedIn: String) = CascRemovedKey(
            path = listOf("ontrack", "config", "settings", field),
            removedIn = removedIn,
        )
    }
}
