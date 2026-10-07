package net.nemerosa.ontrack.model.structure

import com.fasterxml.jackson.annotation.JsonInclude
import net.nemerosa.ontrack.model.security.Actor
import net.nemerosa.ontrack.model.security.ActorAgentSession

/**
 * The agent behind a [Signature]. A person's signature has none.
 *
 * It is stored as is in the `ACTOR` column of the signed tables, denormalised so that the names
 * survive the deletion of the agent:
 *
 * ```json
 * {"kind":"agent","agent":"claude[agent]","displayName":"Claude","tool":"Claude Code","owner":"damien@yontrack.test","session":{"id":"…","link":"https://…"}}
 * ```
 *
 * @property kind Kind of actor, always [KIND_AGENT]
 * @property agent Identifier of the agent, `<slug>[agent]`
 * @property displayName Display name of the agent
 * @property tool Tool behind the agent
 * @property owner Email of the person accountable for the agent
 * @property session Agent session behind the action, if the agent gave one
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SignatureActor(
    val kind: String = KIND_AGENT,
    val agent: String,
    val displayName: String,
    val tool: String? = null,
    val owner: String,
    val session: ActorAgentSession? = null,
) {

    companion object {

        /**
         * Kind of an agent actor.
         */
        const val KIND_AGENT = "agent"

        /**
         * The signature actor of a security [actor]: the agent it is, or the one the system acts
         * on behalf of. Null when no agent is involved.
         */
        fun of(actor: Actor?): SignatureActor? =
            actor?.agentActor?.let { agentActor ->
                val agent = agentActor.agent!!
                SignatureActor(
                    agent = agent.name,
                    displayName = agent.displayName,
                    tool = agent.tool,
                    owner = agent.owner,
                    session = agentActor.agentSession,
                )
            }
    }
}
