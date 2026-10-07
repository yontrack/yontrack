package net.nemerosa.ontrack.kdsl.spec

import net.nemerosa.ontrack.kdsl.connector.graphql.schema.BuildSignatureQuery
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector

/**
 * Signature of an entity.
 *
 * @property user Who signed: the account's identifier, `<slug>[agent]` for an agent
 * @property time ISO timestamp
 * @property actor The agent behind the signature, null for a person
 */
data class Signature(
    val user: String?,
    val time: String?,
    val actor: SignatureActor?,
)

/**
 * The agent behind a [Signature].
 *
 * @property kind Always `agent`
 * @property agent Identifier of the agent, `<slug>[agent]`
 * @property displayName Display name of the agent when it acted
 * @property tool Tool behind the agent when it acted
 * @property owner Email of the person accountable for the agent when it acted
 * @property sessionId Identifier of the agent session, if given
 * @property sessionLink Link to the agent session, if given
 */
data class SignatureActor(
    val kind: String,
    val agent: String,
    val displayName: String,
    val tool: String?,
    val owner: String,
    val sessionId: String?,
    val sessionLink: String?,
)

/**
 * Signature of the build.
 */
fun Build.signature(): Signature =
    graphqlConnector.query(BuildSignatureQuery(id.toInt()))
        ?.build?.creation
        ?.let { creation ->
            Signature(
                user = creation.user,
                time = creation.time,
                actor = creation.actor?.let {
                    SignatureActor(
                        kind = it.kind,
                        agent = it.agent,
                        displayName = it.displayName,
                        tool = it.tool,
                        owner = it.owner,
                        sessionId = it.sessionId,
                        sessionLink = it.sessionLink,
                    )
                }
            )
        }
        ?: error("Build not found: $id")
