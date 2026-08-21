package net.nemerosa.ontrack.model.structure

/**
 * Describes the property holding the display name of a build.
 *
 * This allows the display name of a build to be resolved directly by the database, without
 * the core having to know about the extension which actually provides the display name.
 *
 * @property propertyTypeName Fully qualified name of the property type holding the display name
 * @property jsonField Field of the property JSON value holding the display name
 */
data class BuildDisplayNameProperty(
    val propertyTypeName: String,
    val jsonField: String,
)
