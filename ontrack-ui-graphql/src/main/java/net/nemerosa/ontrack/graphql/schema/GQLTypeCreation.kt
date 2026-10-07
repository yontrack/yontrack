package net.nemerosa.ontrack.graphql.schema

import graphql.Scalars
import graphql.schema.DataFetcher
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.model.structure.Signature
import net.nemerosa.ontrack.model.structure.SignatureActor
import org.springframework.stereotype.Component

@Component
class GQLTypeCreation : GQLType {
    override fun getTypeName() = SIGNATURE

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
            GraphQLObjectType.newObject()
                    .name(SIGNATURE)
                    .field {
                        it.name("user")
                                .description("User name")
                                .type(Scalars.GraphQLString)
                    }
                    .field {
                        it.name("time")
                                .description("ISO timestamp")
                                .type(Scalars.GraphQLString)
                    }
                    .field {
                        it.name("actor")
                                .description("The agent behind the signature, null for a person. The user name of an agent's signature is the agent's identifier.")
                                .type(GraphQLTypeReference(GQLTypeSignatureActor.SIGNATURE_ACTOR))
                    }
                    .build()

    companion object {

        const val SIGNATURE = "Signature"

        @JvmStatic
        fun getCreationFromSignature(signature: Signature?): Creation {
            var result = Creation()
            if (signature != null) {
                val user = signature.user
                result = result.withUser(user.name)
                result = result.withTime(Time.store(signature.time))
                result = result.withActor(signature.actor)
            }
            return result
        }

        @JvmStatic
        inline fun <reified T> dataFetcher(noinline signatureGetter: (T) -> Signature?) =
                DataFetcher { environment ->
                    val source: Any = environment.getSource()!!
                    if (source is T) {
                        signatureGetter(source)?.let { getCreationFromSignature(it) }
                    } else {
                        throw IllegalStateException("Fetcher source not an ${T::class.qualifiedName}")
                    }
                }
    }

    data class Creation(
            val user: String? = null,
            val time: String? = null,
            val actor: SignatureActor? = null,
    ) {
        fun withUser(v: String) = copy(user = v)
        fun withTime(v: String) = copy(time = v)
        fun withActor(v: SignatureActor?) = copy(actor = v)
    }
}