package net.nemerosa.ontrack.graphql.schema

import graphql.Scalars.GraphQLString
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.model.structure.SignatureActor
import org.springframework.stereotype.Component

/**
 * The agent behind a [signature][GQLTypeCreation], null for a person.
 */
@Component
class GQLTypeSignatureActor : GQLType {

    override fun getTypeName(): String = SIGNATURE_ACTOR

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(SIGNATURE_ACTOR)
            .description("The agent behind a signature. A person's signature has none.")
            .field {
                it.name("kind")
                    .description("Kind of actor - always `agent`, since a person's signature has no actor")
                    .type(GraphQLNonNull(GraphQLString))
                    .dataFetcher { env -> env.getSource<SignatureActor>()!!.kind }
            }
            .field {
                it.name("agent")
                    .description("Identifier of the agent, `<slug>[agent]`")
                    .type(GraphQLNonNull(GraphQLString))
                    .dataFetcher { env -> env.getSource<SignatureActor>()!!.agent }
            }
            .field {
                it.name("displayName")
                    .description("Display name of the agent, as it was when it acted")
                    .type(GraphQLNonNull(GraphQLString))
                    .dataFetcher { env -> env.getSource<SignatureActor>()!!.displayName }
            }
            .field {
                it.name("tool")
                    .description("Tool behind the agent (Claude Code, Codex, ...), as it was when it acted")
                    .type(GraphQLString)
                    .dataFetcher { env -> env.getSource<SignatureActor>()!!.tool }
            }
            .field {
                it.name("owner")
                    .description("Email of the person accountable for the agent when it acted")
                    .type(GraphQLNonNull(GraphQLString))
                    .dataFetcher { env -> env.getSource<SignatureActor>()!!.owner }
            }
            .field {
                it.name("sessionId")
                    .description("Opaque identifier of the agent session behind the action, if the agent gave one")
                    .type(GraphQLString)
                    .dataFetcher { env -> env.getSource<SignatureActor>()!!.session?.id }
            }
            .field {
                it.name("sessionLink")
                    .description("Link to the agent session behind the action, if the agent gave one")
                    .type(GraphQLString)
                    .dataFetcher { env -> env.getSource<SignatureActor>()!!.session?.link }
            }
            .build()

    companion object {
        const val SIGNATURE_ACTOR = "SignatureActor"
    }
}
