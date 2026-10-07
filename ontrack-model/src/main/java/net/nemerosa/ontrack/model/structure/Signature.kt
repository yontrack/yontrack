package net.nemerosa.ontrack.model.structure

import com.fasterxml.jackson.annotation.JsonInclude
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.common.truncate
import java.time.LocalDateTime

/**
 * Association of a [User] and a timestamp, and of the [actor] when it is an agent.
 *
 * @property time When the action was taken
 * @property user Who signed it: the account's identifier, `<slug>[agent]` for an agent
 * @property actor The agent behind the signature, null for a person - and then absent from the JSON
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Signature(
        val time: LocalDateTime,
        val user: User,
        val actor: SignatureActor? = null,
) {
    fun withTime(dateTime: LocalDateTime?): Signature = Signature(dateTime ?: Time.now(), user, actor)

    /**
     * Same signature, with another actor.
     */
    fun withActor(actor: SignatureActor?): Signature = Signature(time, user, actor)

    /**
     * Keeps at most 4 first digits for the nano seconds.
     *
     * @see [Time.store]
     * @see [Time.fromStorage]
     */
    fun truncate() = Signature(
            time.truncate(),
            user,
            actor,
    )

    /**
     * Equality is based on the first 4 digits of the nano seconds
     */
    override fun equals(other: Any?): Boolean = if (other is Signature) {
        this.user == other.user && this.time.truncate() == other.time.truncate() && this.actor == other.actor
    } else {
        false
    }

    override fun hashCode(): Int {
        var result = time.truncate().hashCode()
        result = 31 * result + user.hashCode()
        result = 31 * result + (actor?.hashCode() ?: 0)
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
