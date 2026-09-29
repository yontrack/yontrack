package net.nemerosa.ontrack.graphql.support

import graphql.Scalars.*
import graphql.schema.*
import net.nemerosa.ontrack.model.structure.Entity
import kotlin.reflect.KClass

// ============================================================================
// Single object field
// ============================================================================

inline fun <reified T : Any> objectField(
    name: String,
    description: String? = null,
    nullable: Boolean = true,
    noinline fetcher: ((DataFetchingEnvironment) -> T?)? = null
) = objectField(T::class, name, description, nullable, fetcher)

fun <T : Any> objectField(
    type: KClass<T>,
    name: String,
    description: String? = null,
    nullable: Boolean = true,
    fetcher: ((DataFetchingEnvironment) -> T?)? = null
) = objectField(
    type = type.toTypeRef(),
    name = name,
    description = description,
    nullable = nullable,
    fetcher = fetcher
)

fun <T> objectField(
    type: GraphQLOutputType,
    name: String,
    description: String? = null,
    nullable: Boolean = true,
    fetcher: ((DataFetchingEnvironment) -> T?)? = null
): GraphQLFieldDefinition = GraphQLFieldDefinition.newFieldDefinition()
    .name(name)
    .description(description)
    .type(nullableType(type, nullable))
    .apply {
        fetcher?.let { dataFetcher(it) }
    }
    .build()

// ============================================================================
// List of objects
// ============================================================================

inline fun <reified T : Any> listField(
    name: String,
    description: String,
    nullable: Boolean = false,
    noinline fetcher: (DataFetchingEnvironment) -> List<T>
) = listField(
    type = T::class,
    name = name,
    description = description,
    nullable = nullable,
    fetcher = fetcher
)

fun <T : Any> listField(
    type: KClass<T>,
    name: String,
    description: String,
    nullable: Boolean = false,
    fetcher: (DataFetchingEnvironment) -> List<T>
) = listField(
    type = type.toTypeRef(),
    name = name,
    description = description,
    nullable = nullable,
    fetcher = fetcher
)

fun <P, T : Any> typedListField(
    type: KClass<T>,
    name: String,
    description: String,
    nullable: Boolean = false,
    fetcher: (P) -> List<T>
) = listField(
    type = type,
    name = name,
    description = description,
    nullable = nullable
) { env ->
    val p: P = env.getSource()!!
    fetcher(p)
}

fun <T> listField(
    type: GraphQLOutputType,
    name: String,
    description: String,
    nullable: Boolean = false,
    fetcher: (DataFetchingEnvironment) -> List<T>
): GraphQLFieldDefinition = GraphQLFieldDefinition.newFieldDefinition()
    .name(name)
    .description(description)
    .type(listType(type, nullable))
    .dataFetcher(fetcher)
    .build()

/**
 * List type
 */
fun listType(itemType: GraphQLOutputType, nullable: Boolean = false, nullableItem: Boolean = false): GraphQLOutputType =
    nullableType(
        GraphQLList(nullableType(itemType, nullableItem)),
        nullable
    )

/**
 * List type using a type name
 */
fun listType(itemTypeName: String, nullable: Boolean = false, nullableItem: Boolean = false): GraphQLOutputType =
    nullableType(
        GraphQLList(nullableType(GraphQLTypeReference(itemTypeName), nullableItem)),
        nullable
    )

// ============================================================================
// Common fields
// ============================================================================


@Suppress("DEPRECATION")
fun idField(): GraphQLFieldDefinition =
    GraphQLFieldDefinition.newFieldDefinition()
        .name("id")
        .type(GraphQLID.toNotNull())
        .dataFetcher { environment: DataFetchingEnvironment ->
            val source = environment.getSource<Any>()
            if (source is Entity) {
                return@dataFetcher source.id.value
            } else {
                return@dataFetcher null
            }
        }
        .build()

fun nameField(description: String = ""): GraphQLFieldDefinition = GraphQLFieldDefinition.newFieldDefinition()
    .name("name")
    .description(description)
    .type(GraphQLString)
    .build()

fun descriptionField(): GraphQLFieldDefinition = GraphQLFieldDefinition.newFieldDefinition()
    .name("description")
    .type(GraphQLString)
    .build()

fun disabledField(): GraphQLFieldDefinition = GraphQLFieldDefinition.newFieldDefinition()
    .name("disabled")
    .type(GraphQLNonNull(GraphQLBoolean))
    .build()

// ============================================================================
// General utilities
// ============================================================================

/**
 * Adjust a type so that it becomes nullable or not according to the value
 * of [nullable].
 */
fun nullableType(type: GraphQLOutputType, nullable: Boolean): GraphQLOutputType =
    if (nullable) {
        type
    } else {
        GraphQLNonNull(type)
    }

/**
 * Adjust a type so that it becomes not nullable.
 */
fun notNullableType(type: GraphQLOutputType): GraphQLOutputType = nullableType(type, nullable = false)

/**
 * Extension function for non nullable types
 */
fun GraphQLOutputType.toNotNull() = notNullableType(this)

/**
 * Extension function for non-nullable types based on a flag
 */
fun GraphQLOutputType.nullable(nullable: Boolean) =
    if (nullable) {
        this
    } else {
        notNullableType(this)
    }
