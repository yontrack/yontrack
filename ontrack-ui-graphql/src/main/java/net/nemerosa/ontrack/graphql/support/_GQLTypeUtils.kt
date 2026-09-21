package net.nemerosa.ontrack.graphql.support

import tools.jackson.databind.JsonNode
import graphql.Scalars.*
import graphql.schema.*
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.model.annotations.getPropertyDescription
import net.nemerosa.ontrack.model.annotations.getPropertyName
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.support.NameValue
import net.nemerosa.ontrack.model.support.toNameValues
import java.time.Duration
import java.time.LocalDateTime
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.javaType

typealias TypeBuilder = GraphQLObjectType.Builder

fun TypeBuilder.booleanField(name: String, description: String): GraphQLObjectType.Builder =
    field { it.name(name).description(description).type(GraphQLBoolean) }

fun <T> TypeBuilder.booleanFieldFunction(
    name: String,
    description: String,
    fn: (source: T) -> Boolean,
): GraphQLObjectType.Builder =
    field {
        it.name(name)
            .description(description)
            .type(GraphQLBoolean.toNotNull())
            .dataFetcher { env ->
                val source: T = env.getSource()!!
                fn(source)
            }
    }

fun TypeBuilder.intField(name: String, description: String): GraphQLObjectType.Builder =
    field { it.name(name).description(description).type(GraphQLInt) }

fun TypeBuilder.intField(property: KProperty<Int?>, description: String? = null): GraphQLObjectType.Builder =
    field { it.name(property.name).description(getPropertyDescription(property, description)).type(GraphQLInt) }

fun TypeBuilder.longField(property: KProperty<Long?>, description: String? = null): GraphQLObjectType.Builder =
    field { it.name(property.name).description(getPropertyDescription(property, description)).type(GraphQLInt) }

fun TypeBuilder.doubleField(property: KProperty<Double?>, description: String? = null): GraphQLObjectType.Builder =
    field { it.name(property.name).description(getPropertyDescription(property, description)).type(GraphQLFloat) }

fun TypeBuilder.stringField(name: String, description: String): GraphQLObjectType.Builder =
    field {
        it.name(name).description(description).type(GraphQLString)
    }

fun TypeBuilder.stringListField(
    property: KProperty<List<String>>,
    description: String? = null
): GraphQLObjectType.Builder =
    field {
        it.name(getPropertyName(property))
            .description(description ?: getPropertyDescription(property))
            .type(listType(GraphQLString))
    }

@Deprecated("Prefer using graphQLIDField")
fun TypeBuilder.idField(property: KProperty<ID>, description: String? = null): GraphQLObjectType.Builder =
    field {
        it.name(getPropertyName(property))
            .description(getPropertyDescription(property, description))
            .type(GraphQLInt)
            .dataFetcher { env ->
                val source = env.getSource<Any>()
                val id = property.call(source)
                id.get()
            }
    }

fun TypeBuilder.idFieldForInt(property: KProperty<Int>, description: String? = null): GraphQLObjectType.Builder =
    field {
        it.name(getPropertyName(property))
            .description(getPropertyDescription(property, description))
            .type(GraphQLID)
    }

fun TypeBuilder.idFieldForID(property: KProperty<ID>, description: String? = null): GraphQLObjectType.Builder =
    field {
        it.name(getPropertyName(property))
            .description(getPropertyDescription(property, description))
            .type(GraphQLID)
            .dataFetcher { env ->
                val source = env.getSource<Any>()
                val id = property.call(source)
                id.get()
            }
    }

fun TypeBuilder.idFieldForString(property: KProperty<String>, description: String? = null): GraphQLObjectType.Builder =
    field {
        it.name(property.name)
            .description(getPropertyDescription(property, description))
            .type(GraphQLID.toNotNull())
    }

fun TypeBuilder.dateField(name: String, description: String, nullable: Boolean = true): GraphQLObjectType.Builder =
    field {
        it.name(name)
            .description(description)
            .type(nullableOutputType(GQLScalarLocalDateTime.INSTANCE, nullable))
    }

fun TypeBuilder.localDateTimeField(
    property: KProperty<LocalDateTime?>,
    description: String? = null,
): GraphQLObjectType.Builder =
    field {
        it.name(getPropertyName(property))
            .description(getPropertyDescription(property, description))
            .type(nullableOutputType(GQLScalarLocalDateTime.INSTANCE, property.returnType.isMarkedNullable))
    }

fun TypeBuilder.durationField(
    property: KProperty<Duration?>,
    description: String? = null,
): GraphQLObjectType.Builder =
    field {
        it.name(getPropertyName(property))
            .description(getPropertyDescription(property, description))
            .type(nullableOutputType(GraphQLString, property.returnType.isMarkedNullable))
    }

fun TypeBuilder.jsonField(
    property: KProperty<Any?>,
    description: String? = null,
    deprecation: String? = null,
): GraphQLObjectType.Builder =
    field {
        it.name(getPropertyName(property))
            .description(getPropertyDescription(property, description))
            .apply {
                if (!deprecation.isNullOrBlank()) {
                    deprecate(deprecation)
                }
            }
            .type(GQLScalarJSON.INSTANCE)
    }

fun <P> TypeBuilder.jsonFieldGetter(
    name: String,
    description: String? = null,
    deprecation: String? = null,
    code: (source: P, env: DataFetchingEnvironment) -> JsonNode?
): GraphQLObjectType.Builder =
    field {
        it.name(name)
            .description(description)
            .apply {
                if (!deprecation.isNullOrBlank()) {
                    deprecate(deprecation)
                }
            }
            .type(GQLScalarJSON.INSTANCE)
            .dataFetcher { env ->
                val source: P = env.getSource()!!
                code(source, env)
            }

    }

fun TypeBuilder.booleanField(property: KProperty<Boolean>, description: String? = null): GraphQLObjectType.Builder =
    field {
        it.name(getPropertyName(property))
            .description(getPropertyDescription(property, description))
            .type(GraphQLBoolean)
            .dataFetcher { env ->
                val source = env.getSource<Any>()
                property.call(source)
            }
    }

fun <T> TypeBuilder.field(
    property: KProperty<T?>,
    type: GQLType,
    description: String? = null,
): GraphQLObjectType.Builder =
    field {
        val outputType: GraphQLOutputType = if (property.returnType.isMarkedNullable) {
            type.typeRef
        } else {
            GraphQLNonNull(type.typeRef)
        }
        it.name(property.name)
            .description(getPropertyDescription(property, description))
            .type(outputType)
    }

fun <T> TypeBuilder.field(
    property: KProperty<T?>,
    typeName: String,
    description: String? = null,
): GraphQLObjectType.Builder =
    field(property, GraphQLTypeReference(typeName), description)

fun <T> TypeBuilder.field(
    property: KProperty<T?>,
    description: String? = null,
): GraphQLObjectType.Builder {
    val typeName = (property.returnType.javaType as Class<*>).simpleName
    return field {
        it.name(getPropertyName(property))
            .description(getPropertyDescription(property, description))
            .type(nullableOutputType(GraphQLTypeReference(typeName), property.returnType.isMarkedNullable))
    }
}

fun <T> TypeBuilder.field(
    property: KProperty<T?>,
    ref: GraphQLTypeReference,
    description: String? = null,
): GraphQLObjectType.Builder =
    field {
        val outputType: GraphQLOutputType = if (property.returnType.isMarkedNullable) {
            ref
        } else {
            GraphQLNonNull(ref)
        }
        it.name(property.name)
            .description(getPropertyDescription(property, description))
            .type(outputType)
    }

inline fun <P, reified E> TypeBuilder.fieldGetter(
    name: String,
    description: String,
    nullable: Boolean = false,
    arguments: List<GraphQLArgument>? = emptyList(),
    noinline code: (source: P, env: DataFetchingEnvironment) -> E?
): GraphQLObjectType.Builder = field {
    it.name(name)
        .description(description)
        .type(E::class.toTypeRef().nullable(nullable))
        .arguments(arguments)
        .dataFetcher { env ->
            val source: P = env.getSource()!!
            code(source, env)
        }
}

fun <T> TypeBuilder.listField(
    property: KProperty<List<T>>,
    description: String? = null,
): GraphQLObjectType.Builder =
    field {
        val itemTypeName =
            (property.returnType.arguments.firstOrNull()?.type?.classifier as? KClass<*>)?.java?.simpleName
        if (itemTypeName.isNullOrBlank()) error("Cannot get list item type for $property")
        it.name(property.name)
            .description(getPropertyDescription(property, description))
            .type(
                listType(
                    itemTypeName = itemTypeName,
                    nullable = property.returnType.isMarkedNullable,
                )
            )
    }

inline fun <P, reified E> TypeBuilder.listFieldGetter(
    name: String,
    description: String,
    noinline code: (source: P) -> List<E>
): GraphQLObjectType.Builder = field {
    it.name(name)
        .description(description)
        .type(listType(GraphQLTypeReference(E::class.java.simpleName)))
        .dataFetcher { env ->
            val source: P = env.getSource()!!
            code(source)
        }
}

fun <E : Enum<E>> TypeBuilder.enumAsStringField(
    property: KProperty<E?>,
    description: String? = null,
): GraphQLObjectType.Builder =
    field {
        it.name(property.name)
            .description(getPropertyDescription(property, description))
            .type(GraphQLString)
    }

inline fun <reified E : Enum<E>> TypeBuilder.enumField(
    property: KProperty<E?>,
    description: String? = null,
): GraphQLObjectType.Builder =
    field {
        it.name(property.name)
            .description(getPropertyDescription(property, description))
            .type(
                nullableOutputType(
                    GraphQLTypeReference(E::class.java.simpleName),
                    property.returnType.isMarkedNullable
                )
            )
    }

fun TypeBuilder.stringField(property: KProperty<String?>, description: String? = null): GraphQLObjectType.Builder =
    field {
        it.name(property.name)
            .description(getPropertyDescription(property, description))
            .type(nullableOutputType(GraphQLString, property.returnType.isMarkedNullable))
    }

fun TypeBuilder.nameValuesFromMapField(
    property: KProperty<Map<String, String>>,
    description: String? = null
): GraphQLObjectType.Builder =
    field {
        it.name(getPropertyName(property))
            .description(getPropertyDescription(property, description))
            .type(listType(GraphQLTypeReference(NameValue::class.java.simpleName)))
            .dataFetcher { env ->
                val source = env.getSource<Any>()
                val map = property.call(source)
                map.toNameValues()
            }
    }

fun TypeBuilder.classField(property: KProperty<Class<*>?>, description: String? = null): GraphQLObjectType.Builder =
    field {
        it.name(getPropertyName(property))
            .description(getPropertyDescription(property, description))
            .type(nullableOutputType(GraphQLString, property.returnType.isMarkedNullable))
            .dataFetcher { env ->
                val source = env.getSource<Any>()
                val cls = property.call(source)
                cls?.name
            }
    }

fun TypeBuilder.gqlTypeField(
    name: String,
    description: String,
    type: GQLType,
    nullable: Boolean = true,
): GraphQLObjectType.Builder =
    field {
        it.name(name).description(description).type(nullableType(type.typeRef, nullable))
    }

fun nullableOutputType(type: GraphQLOutputType, nullable: Boolean) =
    if (nullable) {
        type
    } else {
        GraphQLNonNull(type)
    }
