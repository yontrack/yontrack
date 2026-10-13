package net.nemerosa.ontrack.model.support

import java.util.function.Function

/**
 * The stored fields of one settings category, read in one query by
 * [SettingsRepository.values], which a `SettingsProvider` reads its settings from.
 *
 * As for the [SettingsRepository], a blank or missing value gives the default value.
 *
 * @param category Class of the settings, the only category these values can be read for
 * @param values Stored values, indexed by name
 */
class SettingsValues(
    private val category: Class<*>,
    private val values: Map<String, String?>,
) : SettingsReader {

    override fun getBoolean(category: Class<*>, name: String, defaultValue: Boolean): Boolean =
        getValue(category, name, defaultValue) { it.toBoolean() }

    override fun getInt(category: Class<*>, name: String, defaultValue: Int): Int =
        getValue(category, name, defaultValue) { it.toInt(10) }

    override fun getLong(category: Class<*>, name: String, defaultValue: Long): Long =
        getValue(category, name, defaultValue) { it.toLong(10) }

    override fun getString(category: Class<*>, name: String, defaultValue: String?): String? =
        getValue(category, name, defaultValue) { it }

    override fun getPassword(
        category: Class<*>,
        name: String,
        defaultValue: String?,
        decryptService: Function<String, String>,
    ): String? =
        getValue(category, name, defaultValue) { decryptService.apply(it) }

    private fun <T> getValue(category: Class<*>, name: String, defaultValue: T, converter: (String) -> T): T {
        require(category == this.category) {
            "These values are those of ${this.category.name}, not of ${category.name}"
        }
        val value = values[name]
        return if (value.isNullOrBlank()) {
            defaultValue
        } else {
            converter(value)
        }
    }
}
