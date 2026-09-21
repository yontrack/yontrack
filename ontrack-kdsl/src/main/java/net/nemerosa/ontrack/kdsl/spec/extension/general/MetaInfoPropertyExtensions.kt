package net.nemerosa.ontrack.kdsl.spec.extension.general

import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.kdsl.spec.Build
import net.nemerosa.ontrack.kdsl.spec.deleteProperty
import net.nemerosa.ontrack.kdsl.spec.getProperty
import net.nemerosa.ontrack.kdsl.spec.setProperty

/**
 * Sets a meta info property on a build using only one key
 */
fun Build.setMetaInfoProperty(name: String, value: String, category: String? = null) {
    metaInfoProperty = listOf(
        MetaInfoPropertyItem(
            name, value, category = category,
        )
    )
}

/**
 * Sets a meta info property on a build.
 */
var Build.metaInfoProperty: List<MetaInfoPropertyItem>?
    get() = getProperty(META_INFO_PROPERTY)?.path("items")?.values()?.map {
        it.parse<MetaInfoPropertyItem>()
    }
    set(value) {
        if (value != null) {
            setProperty(META_INFO_PROPERTY, mapOf("items" to value))
        } else {
            deleteProperty(META_INFO_PROPERTY)
        }
    }

data class MetaInfoPropertyItem(
    val name: String,
    val value: String?,
    val link: String? = null,
    val category: String? = null,
)

const val META_INFO_PROPERTY = "net.nemerosa.ontrack.extension.general.MetaInfoPropertyType"