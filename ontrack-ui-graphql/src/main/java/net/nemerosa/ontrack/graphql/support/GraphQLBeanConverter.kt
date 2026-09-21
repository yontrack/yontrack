package net.nemerosa.ontrack.graphql.support

import tools.jackson.databind.JsonNode
import graphql.Scalars.*
import graphql.schema.*
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.schema.listInputType
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.annotations.getAPITypeName
import net.nemerosa.ontrack.model.annotations.getPropertyDescription
import net.nemerosa.ontrack.model.annotations.getPropertyName
import org.apache.commons.lang3.reflect.FieldUtils
import org.springframework.beans.BeanUtils
import java.beans.PropertyDescriptor
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.time.LocalDateTime
import java.util.*
import kotlin.jvm.internal.Reflection
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaType

object GraphQLBeanConverter {

    private val DEFAULT_EXCLUSIONS = setOf(
        "class"
    )

    fun asInputFields(type: KClass<*>, dictionary: MutableSet<GraphQLType>): List<GraphQLInputObjectField> {
        val fields = mutableListOf<GraphQLInputObjectField>()
        // Gets the properties for the type
        type.memberProperties.forEach { property ->
            val ignoreRef = property.findAnnotation<IgnoreRef>()
            if (ignoreRef == null) {
                val name = getPropertyName(property)
                val description = getPropertyDescription(property)
                val scalarType = getScalarType(property.returnType)
                if (scalarType != null) {
                    val actualType: GraphQLInputType = if (property.returnType.isMarkedNullable) {
                        scalarType as GraphQLInputType
                    } else {
                        GraphQLNonNull(scalarType)
                    }
                    fields += GraphQLInputObjectField.newInputObjectField()
                        .name(name)
                        .description(description)
                        .type(actualType)
                        .build()
                } else if (property.returnType.javaType is Class<*> && (property.returnType.javaType as Class<*>).isEnum) {
                    // For an Enum, we assume this has been declared elsewhere as a GraphQL type
                    // with its name equal to the simple Java type name
                    val enumType = GraphQLTypeReference((property.returnType.javaType as Class<*>).simpleName)
                    val actualType = nullableInputType(enumType, property.returnType.isMarkedNullable)
                    fields += GraphQLInputObjectField.newInputObjectField()
                        .name(name)
                        .description(description)
                        .type(actualType)
                        .build()
                } else {
                    val typeRef = property.findAnnotation<TypeRef>()
                    if (typeRef != null) {
                        val javaType = property.returnType.javaType
                        if (javaType is Class<*>) {
                            val rootType: GraphQLInputType = if (typeRef.embedded) {
                                getOrCreateEmbeddedInputType(javaType, typeRef.suffix, dictionary)
                            } else {
                                GraphQLTypeReference(javaType.simpleName + typeRef.suffix)
                            }
                            val actualType = if (property.returnType.isMarkedNullable) {
                                rootType
                            } else {
                                GraphQLNonNull(rootType)
                            }
                            fields += GraphQLInputObjectField.newInputObjectField()
                                .name(name)
                                .description(description)
                                .type(actualType)
                                .build()
                        } else {
                            throw IllegalStateException("Unsupported type for input type: $property")
                        }
                    } else {
                        val listRef = property.findAnnotation<ListRef>()
                        if (listRef != null) {
                            val listArguments = property.returnType.arguments
                            if (listArguments.size == 1) {
                                val elementType = listArguments.first().type?.javaType
                                if (elementType is Class<*>) {
                                    val rootType = (getScalarType(elementType) as? GraphQLInputType?)
                                        ?: if (listRef.embedded) {
                                            getOrCreateEmbeddedInputType(elementType, listRef.suffix, dictionary)
                                        } else if (elementType.isEnum) {
                                            // For an Enum, we assume this has been declared elsewhere as a GraphQL type
                                            // with its name equal to the simple Java type name
                                            val enumType = GraphQLTypeReference(elementType.simpleName)
                                            enumType
                                        } else {
                                            val refName = if (listRef.suffix.isNotBlank()) {
                                                "${elementType.simpleName}${listRef.suffix}"
                                            } else {
                                                elementType.simpleName
                                            }
                                            GraphQLTypeReference(refName)
                                        }
                                    fields += GraphQLInputObjectField.newInputObjectField()
                                        .name(name)
                                        .description(description)
                                        .type(listInputType(rootType, nullable = property.returnType.isMarkedNullable))
                                        .build()
                                } else {
                                    throw IllegalStateException("Only list elements being Java classes are supported")
                                }
                            } else {
                                throw IllegalStateException("List is supported only if it has a type")
                            }
                        } else {
                            throw IllegalStateException("Cannot create an input field out of $property since its type is not scalar and the property is not annotated with @TypeRef or @ListRef")
                        }
                    }
                }
            }
        }
        // OK
        return fields
    }

    private fun getOrCreateEmbeddedInputType(
        type: Class<*>,
        suffix: String,
        dictionary: MutableSet<GraphQLType>,
    ): GraphQLInputType {
        val typeName = type.simpleName + suffix
        val existingType = dictionary.find { it is GraphQLInputObjectType && it.name == typeName }
        return if (existingType != null) {
            GraphQLTypeReference(typeName)
        } else {
            val kotlinClass = Reflection.createKotlinClass(type)
            val gqlType = asInputType(kotlinClass, suffix, dictionary) as GraphQLInputObjectType
            dictionary.add(gqlType)
            GraphQLTypeReference(gqlType.name)
        }
    }

    fun asInputType(type: KClass<*>, dictionary: MutableSet<GraphQLType>): GraphQLInputType =
        asInputType(type, "", dictionary)

    fun asInputType(type: KClass<*>, suffix: String, dictionary: MutableSet<GraphQLType>): GraphQLInputType =
        GraphQLInputObjectType.newInputObject()
            .name(type.simpleName + suffix)
            .fields(asInputFields(type, dictionary))
            .build()

    fun asObjectType(
        type: KClass<*>,
        cache: GQLTypeCache,
        code: GraphQLObjectType.Builder.() -> Unit = {},
    ): GraphQLObjectType =
        asObjectTypeBuilder(type, cache)
            .apply { code() }
            .build()

    fun asObjectType(
        type: KClass<*>,
        cache: GQLTypeCache,
    ): GraphQLObjectType =
        asObjectTypeBuilder(type, cache).build()

    fun asObjectTypeBuilder(type: KClass<*>, cache: GQLTypeCache): GraphQLObjectType.Builder =
        GraphQLObjectType.newObject()
            .name(getAPITypeName(type))
            .description(getTypeDescription(type))
            .fields(asObjectFields(type, cache))

    fun asObjectFields(type: KClass<*>, cache: GQLTypeCache): List<GraphQLFieldDefinition> {
        val fields = mutableListOf<GraphQLFieldDefinition>()
        type.memberProperties.forEach { property ->
            val ignoreRef = property.findAnnotation<IgnoreRef>()
            if (ignoreRef == null) {
                val name = getPropertyName(property)
                val description = getPropertyDescription(property)
                val nullable = property.returnType.isMarkedNullable
                val javaType = property.returnType.javaType
                val scalarType = getScalarType(property.returnType)
                // Field builder
                val field = GraphQLFieldDefinition.newFieldDefinition()
                    .name(name)
                    .description(description)
                // Deprecation
                property.findAnnotation<Deprecated>()?.let {
                    field.deprecate(it.message)
                }
                // Type
                val fieldType: GraphQLOutputType = if (scalarType != null) {
                    scalarType as GraphQLOutputType
                } else if (javaType is Class<*> && javaType.isEnum) {
                    // For an Enum, we assume this has been declared elsewhere as a GraphQL type
                    // with its name equal to the simple Java type name
                    GraphQLTypeReference(javaType.simpleName)
                } else {
                    val jsonType = property.findAnnotation<JSONType>()
                    val typeRef = property.findAnnotation<TypeRef>()
                    if (jsonType != null) {
                        field.dataFetcher { env ->
                            val source = env.getSource<Any>()!!
                            @Suppress("UNCHECKED_CAST")
                            val value = (property as KProperty1<Any,Any?>).get(source)
                            value?.asJson()
                        }
                        // JSON type
                        GQLScalarJSON.INSTANCE
                    } else if (typeRef != null) {
                        if (javaType is Class<*>) {
                            if (typeRef.embedded) {
                                cache.getOrCreate(javaType.simpleName) {
                                    val kotlinClass = Reflection.createKotlinClass(javaType)
                                    asObjectType(kotlinClass, cache)
                                }
                            } else {
                                GraphQLTypeReference(javaType.simpleName)
                            }
                        } else {
                            throw IllegalStateException("Unsupported type for output type: $property")
                        }
                    } else {
                        val listRef = property.findAnnotation<ListRef>()
                        if (listRef != null) {
                            val listArguments = property.returnType.arguments
                            if (listArguments.size == 1) {
                                val elementType = listArguments.first().type?.javaType
                                if (elementType is Class<*>) {
                                    val itemType = if (listRef.embedded) {
                                        cache.getOrCreate(elementType.simpleName) {
                                            val kotlinClass = Reflection.createKotlinClass(elementType)
                                            asObjectType(kotlinClass, cache)
                                        }
                                    } else {
                                        GraphQLTypeReference(elementType.simpleName)
                                    }
                                    GraphQLList(itemType.toNotNull())
                                } else {
                                    throw IllegalStateException("Only list elements being Java classes are supported")
                                }
                            } else {
                                throw IllegalStateException("List is supported only if it has one and only one type")
                            }
                        } else {
                            if (javaType is ParameterizedType && javaType.typeName.startsWith("java.util.List<")) {
                                val listArguments = javaType.actualTypeArguments
                                if (listArguments.size == 1) {
                                    val elementType = listArguments.first()
                                    if (elementType is Class<*>) {
                                        val elementKClass = Reflection.createKotlinClass(elementType)
                                        val elementScalarType = getScalarType(elementKClass.java) as? GraphQLOutputType?
                                        val elementGraphQLType = elementScalarType
                                            ?: cache.getOrCreate(
                                                getAPITypeName(elementKClass)
                                            ) { asObjectType(elementKClass, cache) }
                                        GraphQLList(elementGraphQLType.toNotNull())
                                    } else {
                                        throw IllegalStateException("Only list elements being Java classes are supported")
                                    }
                                } else {
                                    throw IllegalStateException("List is supported only if it has one and only one type")
                                }
                            } else {
                                if (javaType is Class<*>) {
                                    // Property type as Kotlin
                                    val propertyKClass = Reflection.createKotlinClass(javaType)
                                    // Tries to convert to an object type
                                    cache.getOrCreate(
                                        getAPITypeName(propertyKClass)
                                    ) { asObjectType(propertyKClass, cache) }
                                } else {
                                    throw IllegalStateException("Only Java classes are supported: $javaType")
                                }
                            }
                        }
                    }
                }
                field.type(
                    if (nullable) {
                        fieldType
                    } else {
                        fieldType.toNotNull()
                    }
                )
                fields += field.build()
            }
        }
        return fields
    }

    internal fun getScalarType(type: Class<*>): GraphQLNamedType? {
        return when {
            // Raw Kotlin
            Int::class.java.isAssignableFrom(type) -> GraphQLInt
            Long::class.java.isAssignableFrom(type) -> GQLScalarLong.INSTANCE
            Double::class.java.isAssignableFrom(type) -> GraphQLFloat
            Float::class.java.isAssignableFrom(type) -> GraphQLFloat
            Boolean::class.java.isAssignableFrom(type) -> GraphQLBoolean
            String::class.java.isAssignableFrom(type) -> GraphQLString
            // Java equivalents
            java.lang.Integer::class.java.isAssignableFrom(type) -> GraphQLInt
            java.lang.Long::class.java.isAssignableFrom(type) -> GQLScalarLong.INSTANCE
            java.lang.Double::class.java.isAssignableFrom(type) -> GraphQLFloat
            java.lang.Float::class.java.isAssignableFrom(type) -> GraphQLFloat
            java.lang.Boolean::class.java.isAssignableFrom(type) -> GraphQLBoolean
            java.lang.String::class.java.isAssignableFrom(type) -> GraphQLString
            // Custom scalar types
            JsonNode::class.java.isAssignableFrom(type) -> GQLScalarJSON.INSTANCE
            LocalDateTime::class.java.isAssignableFrom(type) -> GQLScalarLocalDateTime.INSTANCE
            UUID::class.java.isAssignableFrom(type) -> GQLScalarUUID.INSTANCE
            else -> null
        }
    }

    private fun getScalarType(type: KType): GraphQLNamedType? {
        val javaType = type.javaType
        return if (javaType is Class<*>) {
            getScalarType(javaType)
        } else {
            null
        }
    }

    fun <T> asObject(argument: Any?, type: Class<T>): T? {
        when (argument) {
            null -> return null
            is Map<*, *> -> {
                val map = argument as Map<*, *>?
                val o = type.getDeclaredConstructor().newInstance()
                for (descriptor in BeanUtils.getPropertyDescriptors(type)) {
                    val writeMethod = descriptor.writeMethod
                    if (writeMethod != null) {
                        val arg = map!![descriptor.name]
                        if (arg != null) {
                            try {
                                writeMethod.invoke(o, arg)
                            } catch (e: IllegalAccessException) {
                                throw IllegalStateException("Cannot set property " + descriptor.name)
                            } catch (e: InvocationTargetException) {
                                throw IllegalStateException("Cannot set property " + descriptor.name)
                            }

                        }
                    }
                }
                return o
            }
            else -> throw IllegalArgumentException("Argument is expected to be a map")
        }
    }
}

private fun getTypeDescription(type: Class<*>, descriptor: PropertyDescriptor): String? {
    val readMethod: Method? = descriptor.readMethod
    if (readMethod != null) {
        val annotation: APIDescription? = readMethod.getAnnotation(APIDescription::class.java)
        if (annotation != null) {
            return annotation.value
        }
    }
    val field: Field? = FieldUtils.getField(type, descriptor.name, true)
    if (field != null) {
        val annotation = field.getAnnotation(APIDescription::class.java)
        if (annotation != null) {
            return annotation.value
        }
    }
    return descriptor.shortDescription
}
