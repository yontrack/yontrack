package net.nemerosa.ontrack.model.structure

/**
 * Property value, associated with its type.
 *
 * @param type     Type for this property
 * @param value    Value for this property
 * @param editable Editable status
 * @param error    When the stored value of this property cannot be read, the reason why. The [value] is
 *                 then `null`. Never contains the stored value itself, which may hold secrets.
 */
data class Property<T>(
    val type: PropertyType<T>,
    val value: T?,
    val editable: Boolean = false,
    val error: String? = null,
) {

    /**
     * Descriptor for the property type
     */
    val typeDescriptor: PropertyTypeDescriptor = PropertyTypeDescriptor.of(type)

    /**
     * Is the stored value of this property unreadable?
     */
    val hasError: Boolean get() = error != null

    /**
     * Editable property
     */
    fun editable(editable: Boolean): Property<T> = copy(editable = editable)

    companion object {

        fun <T> empty(type: PropertyType<T>): Property<T> {
            return Property(type, null, false)
        }

        fun <T> of(type: PropertyType<T>, value: T?): Property<T> {
            return Property(type, value, false)
        }

        /**
         * Property whose stored value cannot be read.
         *
         * @param type  Type of the property
         * @param error Reason why the stored value cannot be read
         */
        fun <T> error(type: PropertyType<T>, error: String): Property<T> {
            return Property(type, null, false, error)
        }
    }
}
