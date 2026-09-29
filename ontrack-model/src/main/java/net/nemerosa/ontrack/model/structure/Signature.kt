package net.nemerosa.ontrack.model.structure

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.common.truncate
import java.time.LocalDateTime

/**
 * Association of a [User] and a timestamp.
 */
data class Signature(
        val time: LocalDateTime,
        val user: User
) {
    fun withTime(dateTime: LocalDateTime?): Signature = Signature(dateTime ?: Time.now(), user)

    /**
     * Keeps at most 4 first digits for the nano seconds.
     *
     * @see [Time.store]
     * @see [Time.fromStorage]
     */
    fun truncate() = Signature(
            time.truncate(),
            user
    )

    /**
     * Equality is based on the first 4 digits of the nano seconds
     */
    override fun equals(other: Any?): Boolean = if (other is Signature) {
        this.user == other.user && this.time.truncate() == other.time.truncate()
    } else {
        false
    }

    override fun hashCode(): Int {
        var result = time.truncate().hashCode()
        result = 31 * result + user.hashCode()
        return result
    }

    companion object {

        /**
         * Builder from a user name, for current time
         */
        @JvmStatic
        fun of(name: String): Signature = of(Time.now(), name)

        /**
         * Builder from a user name and a given time
         */
        @JvmStatic
        fun of(dateTime: LocalDateTime, name: String) = Signature(
                dateTime,
                User.of(name)
        )

        /**
         * Anonymous signature
         */
        @JvmStatic
        fun anonymous(): Signature {
            return Signature(
                    Time.now(),
                    User.anonymous()
            )
        }

    }
}