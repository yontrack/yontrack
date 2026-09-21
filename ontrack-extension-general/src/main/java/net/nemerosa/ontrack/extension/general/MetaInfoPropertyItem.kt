package net.nemerosa.ontrack.extension.general

import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.Strings
import java.util.regex.Pattern

data class MetaInfoPropertyItem(
    val name: String,
    val value: String?,
    val link: String?,
    val category: String?
) {

    /**
     * Does one of the items match the name-&gt;value? The value can be blank (meaning all values)
     * or contains wildcards (*).
     */
    fun matchNameValue(namePattern: String, valuePattern: String): Boolean {
        return Strings.CS.equals(this.name, namePattern) && (StringUtils.isBlank(valuePattern) ||
                Strings.CS.equals("*", valuePattern) ||
                Pattern.matches(Strings.CS.replace(valuePattern, "*", ".*"), this.value))
    }

    /**
     * Value used to search on this item
     */
    fun toSearchToken() = "${category ?: ""}/$name:${value ?: ""}"

    companion object {
        @JvmStatic
        fun of(name: String, value: String): MetaInfoPropertyItem {
            return MetaInfoPropertyItem(name, value, null, null)
        }

        /**
         * Helper function to parse a token into an optional category, a name and an optional value.
         */
        fun parse(text: String): MetaInfoPropertyItem {
            val name: String
            val category: String?
            val value: String?
            val token: String
            val iValue = text.indexOf(":")
            if (iValue >= 0) {
                token = text.substring(0, iValue).trim()
                value = text.substring(iValue + 1).trim()
            } else {
                token = text.trim()
                value = null
            }
            val iCategory = token.indexOf("/")
            if (iCategory >= 0) {
                category = token.substring(0, iCategory).trim()
                name = token.substring(iCategory + 1).trim()
            } else {
                category = null
                name = token.trim()
            }
            return MetaInfoPropertyItem(
                name = name,
                category = category,
                link = null,
                value = value,
            )
        }
    }
}
