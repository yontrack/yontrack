package net.nemerosa.ontrack.extension.notifications.webhooks

import graphql.Scalars.GraphQLInt
import graphql.Scalars.GraphQLString
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.booleanField
import net.nemerosa.ontrack.graphql.support.getTypeDescription
import net.nemerosa.ontrack.graphql.support.jsonFieldGetter
import net.nemerosa.ontrack.graphql.support.pagination.GQLPaginatedListFactory
import net.nemerosa.ontrack.graphql.support.stringField
import net.nemerosa.ontrack.model.annotations.getPropertyDescription
import org.springframework.stereotype.Component

@Component
class GQLTypeWebhook(
    private val gqlInputWebhookExchangeFilter: GQLInputWebhookExchangeFilter,
    private val gqlTypeWebhookExchange: GQLTypeWebhookExchange,
    private val gqlPaginatedListFactory: GQLPaginatedListFactory,
    private val webhookExchangeService: WebhookExchangeService,
    private val webhookAuthenticatorRegistry: WebhookAuthenticatorRegistry,
) : GQLType {

    override fun getTypeName(): String = Webhook::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description(getTypeDescription(Webhook::class))
            .stringField(Webhook::name)
            .booleanField(Webhook::enabled)
            .stringField(Webhook::url)
            .field {
                it.name("timeoutSeconds")
                    .description(getPropertyDescription(Webhook::timeout))
                    .type(GraphQLInt)
                    .dataFetcher { env ->
                        val webhook: Webhook = env.getSource()!!
                        webhook.timeout.toSeconds()
                    }
            }
            .field {
                it.name("authenticationType")
                    .description("Webhook authentication")
                    .type(GraphQLString)
                    .dataFetcher { env ->
                        val webhook: Webhook = env.getSource()!!
                        webhook.authentication.type
                    }
            }
            .jsonFieldGetter<Webhook>(
                "authenticationConfig",
                "Configuration for the webhook authentication"
            ) { webhook, _ ->
                webhookAuthenticatorRegistry.findWebhookAuthenticator(webhook.authentication.type)
                    ?: throw WebhookAuthenticatorNotFoundException(webhook.authentication.type)
                webhook.authentication.obfuscate().config
            }
            .field(
                gqlPaginatedListFactory.createPaginatedField<Webhook, WebhookExchange>(
                    cache = cache,
                    fieldName = "exchanges",
                    fieldDescription = "Exchanges for this webhook",
                    itemType = gqlTypeWebhookExchange.typeName,
                    arguments = listOf(
                        GraphQLArgument.newArgument()
                            .name(ARG_EXCHANGES_FILTER)
                            .description("Filter for the exchanges")
                            .type(gqlInputWebhookExchangeFilter.typeRef)
                            .build()
                    ),
                    itemPaginatedListProvider = { env, source, offset, size ->
                        val argFilter = gqlInputWebhookExchangeFilter.convert(env.getArgument(ARG_EXCHANGES_FILTER))
                            ?: WebhookExchangeFilter()
                        val filter = argFilter.withPagination(offset, size).withWebhook(source.name)
                        webhookExchangeService.exchanges(filter)
                    }
                )
            )
            .build()

    companion object {
        private const val ARG_EXCHANGES_FILTER = "filter"
    }
}