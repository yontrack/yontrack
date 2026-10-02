package net.nemerosa.ontrack.model.security

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import java.io.Serializable

/**
 * Who is acting in the current security context, and through which channel.
 *
 * Unlike the [user name of a signature][net.nemerosa.ontrack.model.structure.Signature], the actor
 * says *how* the account got in: through the UI, an API token (whose name, never its value, is
 * kept), a JWT (its issuer and subject), a webhook, or the system itself acting for a reason.
 *
 * Its JSON form leaves the absent fields out:
 *
 * ```json
 * {"account":"system","via":"system","system":"auto-promotion","onBehalfOf":{"account":"ci@example.com","via":"token","tokenName":"pipeline"}}
 * ```
 *
 * Every value is a string, so that the actor fits the canonical JSON the audit trail hashes.
 *
 * @property account Email of the account, or [SYSTEM_ACCOUNT] when the system acts
 * @property via Channel through which the account authenticated
 * @property tokenName Name of the API token, when [via] is [ActorVia.TOKEN] or [ActorVia.WEBHOOK]
 * @property jwt Issuer and subject of the JWT, when [via] is [ActorVia.UI] or [ActorVia.JWT]
 * @property system Reason why the system acts, when it acts as administrator
 * @property onBehalfOf Actor whose action led the system to act, if any
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Actor(
    val account: String,
    val via: ActorVia,
    val tokenName: String? = null,
    val jwt: ActorJwt? = null,
    val system: String? = null,
    val onBehalfOf: Actor? = null,
) : Serializable {

    /**
     * Is this the system acting as administrator?
     */
    @get:JsonIgnore
    val isSystem: Boolean
        get() = account == SYSTEM_ACCOUNT && via == ActorVia.SYSTEM

    /**
     * Actor of the system acting as administrator from this actor's context.
     *
     * - with no reason, a system actor stays as it is;
     * - a system actor with no reason takes this one;
     * - a system actor with another reason is kept as the one the new system actor acts for;
     * - any other actor becomes the one the system acts for.
     *
     * @param reason Why the system acts, if known
     */
    fun runAs(reason: String?): Actor = when {
        isSystem && (reason == null || reason == system) -> this
        isSystem && system == null -> copy(system = reason)
        else -> Companion.system(reason = reason, onBehalfOf = this)
    }

    companion object {

        /**
         * Account of the system acting as administrator.
         */
        const val SYSTEM_ACCOUNT = "system"

        /**
         * The system acting as administrator.
         *
         * @param reason Why the system acts, if known
         * @param onBehalfOf Actor whose action led the system to act, if any
         */
        fun system(reason: String?, onBehalfOf: Actor? = null) = Actor(
            account = SYSTEM_ACCOUNT,
            via = ActorVia.SYSTEM,
            system = reason,
            onBehalfOf = onBehalfOf,
        )

        /**
         * Actor of an account whose context was restored without knowing how it authenticated,
         * like a queued message which did not carry its actor.
         */
        fun degraded(account: String) = Actor(
            account = account,
            via = ActorVia.SYSTEM,
        )
    }
}

/**
 * Actor of the system acting as administrator from the context of this actor, or from no context at
 * all.
 *
 * @see Actor.runAs
 */
fun Actor?.runAs(reason: String?): Actor = this?.runAs(reason) ?: Actor.system(reason)

/**
 * Channel through which an [Actor] authenticated.
 */
enum class ActorVia {
    /**
     * The web UI, with the JWT of the user's session.
     */
    @JsonProperty("ui")
    UI,

    /**
     * An API token.
     */
    @JsonProperty("token")
    TOKEN,

    /**
     * A JWT which was not issued to the web UI.
     */
    @JsonProperty("jwt")
    JWT,

    /**
     * A webhook, authenticated by a token.
     */
    @JsonProperty("webhook")
    WEBHOOK,

    /**
     * The system itself.
     */
    @JsonProperty("system")
    SYSTEM,
}

/**
 * Identity of the JWT an [Actor] authenticated with.
 *
 * @property iss Issuer of the token
 * @property sub Subject of the token, within its issuer
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ActorJwt(
    val iss: String?,
    val sub: String?,
) : Serializable
