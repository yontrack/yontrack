package net.nemerosa.ontrack.model.structure

import com.fasterxml.jackson.annotation.JsonIgnore
import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.databind.annotation.JsonSerialize
import net.nemerosa.ontrack.model.support.IDJsonDeserializer
import net.nemerosa.ontrack.model.support.IDJsonSerializer
import java.io.Serializable

/**
 * Represents the numeric identified of an entity.
 *
 * Instances are never instantiated directory. Instead, use either of:
 *
 * * `ID.NONE` for an ID which is _not set_
 * * `ID.of(value)` when the value is known
 *
 * @property value Numeric value for this ID. Its value is `0` if not set, greater than `0` if set.
 */
@JsonSerialize(using = IDJsonSerializer::class)
@JsonDeserialize(using = IDJsonDeserializer::class)
data class ID(
        val value: Int
) : Serializable {

    init {
        check(value >= 0) {
            "Negative ID values are not allowed."
        }
    }

    companion object {

        /**
         * Undefined ID.
         *
         * Its integer value is `0` and a call to [isSet] returns `false`.
         */
        @JvmField
        val NONE: ID = ID(0)

        /**
         * Builds a _defined_ ID. The given _value_ must be an integer greater than 0.
         *
         * @param value The concrete ID value
         * @return A defined ID.
         * @throws IllegalArgumentException If [value] is less or equal than 0.
         */
        @JvmStatic
        fun of(value: Int) = if (value > 0) {
            ID(value)
        } else {
            throw IllegalArgumentException("ID value must be greater than 0.")
        }

        /**
         * Checks if the given [id] is a valid ID.
         */
        @JvmStatic
        fun isDefined(id: ID?) = id != null && id.isSet
    }

    /**
     * String representation is the numeric value itself.
     */
    override fun toString(): String = value.toString()

    /**
     * Gets the ID numeric value
     */
    fun get() = value

    /**
     * Checks if the ID is actually set.
     */
    @JsonIgnore
    val isSet: Boolean = value > 0

    /**
     * If the ID is not set, returns `null` otherwise calls the [transformation] function
     * on the ID numeric value and returns the corresponding value.
     */
    fun <T> ifSet(transformation: (id: Int) -> T): T? =
            if (value > 0) {
                transformation(value)
            } else {
                null
            }

}
