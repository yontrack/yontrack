package net.nemerosa.ontrack.graphql.schema

import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.graphql.support.booleanField
import net.nemerosa.ontrack.graphql.support.dateField
import net.nemerosa.ontrack.graphql.support.stringField
import net.nemerosa.ontrack.model.structure.Token
import org.springframework.stereotype.Component

/**
 * Type for a [token][Token].
 */
@Component
class GQLTypeToken : GQLType {

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description("Authentication token")
            .dateField("creation", "Token creation date.")
            .dateField("validUntil", "Date until the end of validity.")
            .dateField("lastUsed", "Date when the token was last used.")
            .booleanField("valid", "Validity flag, computed in regard to current time.")
            .stringField("name", "Name of the token")
            .stringField("value", "Value of the token")
            .booleanField(
                "readOnly",
                "A read-only token reads what its account can read, and changes nothing. Chosen when the token " +
                        "is generated, and never changed."
            )
            .build()

    override fun getTypeName(): String = Token::class.java.simpleName
}