package net.nemerosa.ontrack.graphql.schema

import tools.jackson.databind.JsonNode
import graphql.Scalars.*
import graphql.schema.*
import net.nemerosa.ontrack.graphql.support.GQLScalarJSON
import net.nemerosa.ontrack.graphql.support.GQLScalarLocalDateTime
import net.nemerosa.ontrack.graphql.support.nullableInputType
import net.nemerosa.ontrack.model.annotations.getPropertyDescription
import java.time.LocalDateTime
import kotlin.reflect.KProperty

fun requiredStringInputField(
    name: String,
    description: String,
): GraphQLInputObjectField = GraphQLInputObjectField.newInputObjectField()
    .name(name)
    .description(description)
    .type(GraphQLNonNull(GraphQLString))
    .build()

fun requiredStringListInputField(
    name: String,
    description: String,
): GraphQLInputObjectField = GraphQLInputObjectField.newInputObjectField()
    .name(name)
    .description(description)
    .type(GraphQLNonNull(GraphQLList(GraphQLNonNull(GraphQLString))))
    .build()

fun optionalStringInputField(
    name: String,
    description: String,
): GraphQLInputObjectField = GraphQLInputObjectField.newInputObjectField()
    .name(name)
    .description(description)
    .type(GraphQLString)
    .build()

fun optionalIntInputField(
    name: String,
    description: String,
): GraphQLInputObjectField = GraphQLInputObjectField.newInputObjectField()
    .name(name)
    .description(description)
    .type(GraphQLInt)
    .build()


fun requiredFloatInputField(
    name: String,
    description: String,
): GraphQLInputObjectField = GraphQLInputObjectField.newInputObjectField()
    .name(name)
    .description(description)
    .type(GraphQLNonNull(GraphQLFloat))
    .build()

fun requiredIntInputField(
    name: String,
    description: String,
): GraphQLInputObjectField = GraphQLInputObjectField.newInputObjectField()
    .name(name)
    .description(description)
    .type(GraphQLNonNull(GraphQLInt))
    .build()

fun optionalBooleanInputField(
    name: String,
    description: String,
): GraphQLInputObjectField = GraphQLInputObjectField.newInputObjectField()
    .name(name)
    .description(description)
    .type(GraphQLBoolean)
    .build()

fun requiredBooleanInputField(
    name: String,
    description: String,
): GraphQLInputObjectField = GraphQLInputObjectField.newInputObjectField()
    .name(name)
    .description(description)
    .type(GraphQLNonNull(GraphQLBoolean))
    .build()

fun requiredRefInputField(
    name: String,
    description: String,
    typeRef: GraphQLTypeReference,
): GraphQLInputObjectField = GraphQLInputObjectField.newInputObjectField()
    .name(name)
    .description(description)
    .type(GraphQLNonNull(typeRef))
    .build()


fun optionalRefInputField(
    name: String,
    description: String,
    typeRef: GraphQLTypeReference,
): GraphQLInputObjectField = GraphQLInputObjectField.newInputObjectField()
    .name(name)
    .description(description)
    .type(typeRef)
    .build()

fun optionalStringListInputField(
    name: String,
    description: String,
): GraphQLInputObjectField = GraphQLInputObjectField.newInputObjectField()
    .name(name)
    .description(description)
    .type(GraphQLList(GraphQLNonNull(GraphQLString)))
    .build()

fun listInputType(
    type: GraphQLInputType,
    nullable: Boolean = false,
): GraphQLInputType = nullableInputType(
    GraphQLList(GraphQLNonNull(type)),
    nullable = nullable,
)

// ================================================================================================
// Typed fields
// ================================================================================================

fun stringInputField(
    property: KProperty<String?>,
    description: String? = null,
    nullable: Boolean? = null
): GraphQLInputObjectField =
    inputField(property, GraphQLString, description, nullable)

inline fun <reified E : Enum<*>> enumInputField(
    property: KProperty<E?>,
    description: String? = null,
    nullable: Boolean? = null
): GraphQLInputObjectField =
    inputField(property, GraphQLTypeReference(E::class.java.simpleName), description, nullable)

fun booleanInputField(
    property: KProperty<Boolean?>,
    description: String? = null,
    nullable: Boolean? = null
): GraphQLInputObjectField =
    inputField(property, GraphQLBoolean, description, nullable)

fun dateTimeInputField(
    property: KProperty<LocalDateTime?>,
    description: String? = null,
    nullable: Boolean? = null
): GraphQLInputObjectField =
    inputField(property, GQLScalarLocalDateTime.INSTANCE, description, nullable)

fun intInputField(
    property: KProperty<Int?>,
    description: String? = null,
    nullable: Boolean? = null
): GraphQLInputObjectField =
    inputField(property, GraphQLInt, description, nullable)

fun stringListInputField(property: KProperty<List<String>?>, nullable: Boolean? = null): GraphQLInputObjectField =
    inputField(
        property,
        type = GraphQLList(GraphQLNonNull(GraphQLString)),
        nullable = nullable,
    )

fun jsonInputField(property: KProperty<JsonNode?>): GraphQLInputObjectField =
    inputField(property, type = GQLScalarJSON.INSTANCE)

fun inputField(
    property: KProperty<*>,
    type: GraphQLInputType,
    description: String? = null,
    nullable: Boolean? = null
): GraphQLInputObjectField {
    val actualDescription = getPropertyDescription(property, description)
    return GraphQLInputObjectField.newInputObjectField()
        .name(property.name)
        .description(actualDescription)
        .type(nullableInputType(type, nullable = nullable ?: property.returnType.isMarkedNullable))
        .build()
}