package net.nemerosa.ontrack.graphql.schema

import tools.jackson.databind.JsonNode
import graphql.Scalars.GraphQLInt
import graphql.Scalars.GraphQLString
import graphql.schema.*
import net.nemerosa.ontrack.common.BaseException
import net.nemerosa.ontrack.graphql.support.GQLScalarJSON
import net.nemerosa.ontrack.graphql.support.TypedMutationProvider
import net.nemerosa.ontrack.graphql.support.getMutationInputField
import net.nemerosa.ontrack.graphql.support.getRequiredMutationInputField
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Component

/**
 * Mutations for the edition of propertie.
 */
@Component
class PropertiesMutations(
    private val structureService: StructureService,
    private val propertyService: PropertyService,
    private val propertyMutationProviders: List<PropertyMutationProvider<*>>
) : TypedMutationProvider() {

    private val genericMutations: List<Mutation>
        get() = ProjectEntityType.values().flatMap { projectEntityType ->
            listOf(
                createGenericMutationById(projectEntityType),
                createGenericMutationByName(projectEntityType)
            )
        }

    private val specificMutations: List<Mutation>
        get() {
            // Get the list of all property types
            val propertyTypes = propertyService.propertyTypes
            // For each property type
            return propertyTypes.flatMap { propertyType ->
                createSpecificPropertyMutations(propertyType)
            }
        }

    override val mutations: List<Mutation>
        get() = listOf(genericMutation, genericDeleteMutation) + genericMutations + specificMutations

    private val genericMutation: Mutation
        get() = simpleMutation(
            name = "setGenericProperty",
            description = "Generic update for a property and an entity",
            input = SetGenericPropertyInput::class,
            outputName = "property",
            outputDescription = "Updated property",
            outputType = Property::class
        ) { input ->
            // Gets the entity
            val entity = structureService.findEntity<ProjectEntity>(input.entityType, input.entityId)
                ?: throw EntityNotFoundByIdException(input.entityType, input.entityId)
            // Saving the property
            propertyService.editProperty(
                entity = entity,
                propertyTypeName = input.type,
                data = input.value,
            )
            // Returning the property
            propertyService.getProperty<Any>(
                entity = entity,
                propertyTypeName = input.type,
            )
        }

    private val genericDeleteMutation: Mutation
        get() = unitMutation(
            name = "deleteGenericProperty",
            description = "Generic deletion for a property and an entity",
            input = DeleteGenericPropertyInput::class,
        ) { input ->
            // Gets the entity
            val entity = structureService.findEntity<ProjectEntity>(input.entityType, input.entityId)
                ?: throw EntityNotFoundByIdException(input.entityType, input.entityId)
            // Deleting the property
            propertyService.deleteProperty(
                entity = entity,
                propertyTypeName = input.type,
            )
        }

    private fun createGenericMutationById(type: ProjectEntityType) = object : Mutation {

        override val name: String = "set${type.typeName}PropertyById"

        override val description: String = "Sets a property on a ${type.displayName} identified by ID"

        override fun inputFields(dictionary: MutableSet<GraphQLType>): List<GraphQLInputObjectField> = listOf(
            id(type),
            propertyType(),
            propertyValue()
        )

        override val outputFields: List<GraphQLFieldDefinition> = listOf(
            projectEntityTypeField(type)
        )

        override fun fetch(env: DataFetchingEnvironment): Map<String, ProjectEntity> {
            val id: Int = getRequiredMutationInputField(env, ARG_ID)
            val propertyName: String = getRequiredMutationInputField(env, ARG_PROPERTY_TYPE)
            val propertyValue: JsonNode? = getMutationInputField<Any>(env, ARG_PROPERTY_VALUE)?.asJson()
            // Loads the entity
            val entity: ProjectEntity = type.getEntityFn(structureService).apply(ID.of(id))
            // Edition
            val result = editProperty(entity, propertyName, propertyValue)
            // Result
            return mapOf(
                type.varName to result
            )
        }
    }

    private fun <T> createSpecificPropertyMutations(propertyType: PropertyType<T>): List<Mutation> {
        // Providers for this property
        val providers = propertyMutationProviders.filter { it.propertyType.java.name == propertyType::class.java.name }
        when {
            providers.size > 1 -> {
                throw MultiplePropertyMutationProviderException(propertyType)
            }

            providers.size == 1 -> {
                @Suppress("UNCHECKED_CAST")
                val provider = providers.first() as PropertyMutationProvider<T>
                // For each supported entity
                return propertyType.supportedEntityTypes.flatMap { type ->
                    listOf(
                        createSpecificPropertyMutationById(propertyType, provider, type),
                        createSpecificPropertyMutationByName(propertyType, provider, type),
                        createSpecificPropertyDeletionById(propertyType, provider, type),
                        createSpecificPropertyDeletionByName(propertyType, provider, type)
                    )
                }
            }

            else -> {
                return emptyList()
            }
        }
    }

    private fun <T> createSpecificPropertyMutationById(
        propertyType: PropertyType<T>,
        provider: PropertyMutationProvider<T>,
        type: ProjectEntityType
    ): Mutation {
        return object : Mutation {
            override val name: String = "set${type.typeName}${provider.mutationNameFragment}PropertyById"
            override val description: String =
                "Set the ${propertyType.name.replaceFirstChar { it.lowercase() }} property on a ${type.displayName}."

            override fun inputFields(dictionary: MutableSet<GraphQLType>): List<GraphQLInputObjectField> = listOf(
                id(type)
            ) + provider.inputFields

            override val outputFields: List<GraphQLFieldDefinition> = listOf(
                projectEntityTypeField(type)
            )

            override fun fetch(env: DataFetchingEnvironment): Any {
                // Loads the entity
                val id: Int = getRequiredMutationInputField(env, ARG_ID)
                val entity: ProjectEntity = type.getEntityFn(structureService).apply(ID.of(id))
                // Reads the property
                val value: T = provider.readInput(entity, EnvMutationInput(env))
                // Sets the property
                propertyService.editProperty(entity, propertyType::class.java, value)
                // OK
                return mapOf(
                    type.varName to entity
                )
            }
        }
    }

    private fun <T> createSpecificPropertyMutationByName(
        propertyType: PropertyType<T>,
        provider: PropertyMutationProvider<T>,
        type: ProjectEntityType
    ): Mutation {
        return object : Mutation {
            override val name: String = "set${type.typeName}${provider.mutationNameFragment}Property"
            override val description: String =
                "Set the ${propertyType.name.replaceFirstChar { it.lowercase() }} property on a ${type.displayName} identified by name."

            override fun inputFields(dictionary: MutableSet<GraphQLType>): List<GraphQLInputObjectField> =
                type.names.map {
                    name(it)
                } + provider.inputFields

            override val outputFields: List<GraphQLFieldDefinition> = listOf(
                projectEntityTypeField(type)
            )

            override fun fetch(env: DataFetchingEnvironment): Any {
                // Loads the entity
                val names = type.names.associateWith { name ->
                    getRequiredMutationInputField<String>(env, name)
                }
                val entity: ProjectEntity = type.loadByNames(structureService, names)
                    ?: throw EntityNotFoundByNameException(type, names)
                // Reads the property
                val value: T = provider.readInput(entity, EnvMutationInput(env))
                // Sets the property
                propertyService.editProperty(entity, propertyType::class.java, value)
                // OK
                return mapOf(
                    type.varName to entity
                )
            }
        }
    }

    private fun <T> createSpecificPropertyDeletionByName(
        propertyType: PropertyType<T>,
        provider: PropertyMutationProvider<T>,
        type: ProjectEntityType
    ): Mutation {
        return object : Mutation {
            override val name: String = "delete${type.typeName}${provider.mutationNameFragment}Property"
            override val description: String =
                "Deletes the ${propertyType.name.replaceFirstChar { it.lowercase() }} property on a ${type.displayName} identified by name."

            override fun inputFields(dictionary: MutableSet<GraphQLType>): List<GraphQLInputObjectField> =
                type.names.map {
                    name(it)
                }

            override val outputFields: List<GraphQLFieldDefinition> = listOf(
                projectEntityTypeField(type)
            )

            override fun fetch(env: DataFetchingEnvironment): Any {
                val names = type.names.associateWith { name ->
                    getRequiredMutationInputField<String>(env, name)
                }
                // Loads the entity
                val entity: ProjectEntity = type.loadByNames(structureService, names)
                    ?: throw EntityNotFoundByNameException(type, names)
                // Deletes the property
                propertyService.deleteProperty(entity, propertyType::class.java)
                // OK
                return mapOf(
                    type.varName to entity
                )
            }
        }
    }

    private fun <T> createSpecificPropertyDeletionById(
        propertyType: PropertyType<T>,
        provider: PropertyMutationProvider<T>,
        type: ProjectEntityType
    ): Mutation {
        return object : Mutation {
            override val name: String = "delete${type.typeName}${provider.mutationNameFragment}PropertyById"
            override val description: String =
                "Deletes the ${propertyType.name.replaceFirstChar { it.lowercase() }} property on a ${type.displayName}."

            // Only the ID is needed
            override fun inputFields(dictionary: MutableSet<GraphQLType>): List<GraphQLInputObjectField> = listOf(
                id(type)
            )

            override val outputFields: List<GraphQLFieldDefinition> = listOf(
                projectEntityTypeField(type)
            )

            override fun fetch(env: DataFetchingEnvironment): Any {
                val id: Int = getRequiredMutationInputField(env, ARG_ID)
                // Loads the entity
                val entity: ProjectEntity = type.getEntityFn(structureService).apply(ID.of(id))
                // Deletes the property
                propertyService.deleteProperty(entity, propertyType::class.java)
                // OK
                return mapOf(
                    type.varName to entity
                )
            }
        }
    }

    private fun editProperty(entity: ProjectEntity, propertyName: String, propertyValue: JsonNode?): ProjectEntity {
        // Property deletion
        if (propertyValue == null) {
            propertyService.deleteProperty(entity, propertyName)
        }
        // Property edition
        else {
            propertyService.editProperty(entity, propertyName, propertyValue)
        }
        // OK
        return entity
    }

    private fun createGenericMutationByName(type: ProjectEntityType) = object : Mutation {

        override val name: String = "set${type.typeName}Property"

        override val description: String = "Sets a property on a ${type.displayName} identified by name"

        override fun inputFields(dictionary: MutableSet<GraphQLType>): List<GraphQLInputObjectField> = type.names.map {
            name(it)
        } + listOf(
            propertyType(),
            propertyValue()
        )

        override val outputFields: List<GraphQLFieldDefinition> = listOf(
            projectEntityTypeField(type)
        )

        override fun fetch(env: DataFetchingEnvironment): Map<String, ProjectEntity> {
            val names = type.names.associateWith { name ->
                getRequiredMutationInputField<String>(env, name)
            }
            val propertyName: String = getRequiredMutationInputField(env, ARG_PROPERTY_TYPE)
            val propertyValue: JsonNode? = getMutationInputField<Any>(env, ARG_PROPERTY_VALUE)?.asJson()
            // Loads the entity
            val entity: ProjectEntity = type.loadByNames(structureService, names)
                ?: throw EntityNotFoundByNameException(type, names)
            // Edition
            val result = editProperty(entity, propertyName, propertyValue)
            // Result
            return mapOf(
                type.varName to result
            )
        }
    }

    class MultiplePropertyMutationProviderException(propertyType: PropertyType<*>) : BaseException(
        """Found multiple implementations of mutation providers for ${propertyType::class.java.name}. This is not supported."""
    )

    private fun name(name: String): GraphQLInputObjectField = GraphQLInputObjectField.newInputObjectField()
        .name(name)
        .description("${name.replaceFirstChar { it.uppercase() }} name")
        .type(GraphQLNonNull(GraphQLString))
        .build()

    private fun id(type: ProjectEntityType): GraphQLInputObjectField = GraphQLInputObjectField.newInputObjectField()
        .name(ARG_ID)
        .description("ID of the ${type.displayName}")
        .type(GraphQLNonNull(GraphQLInt))
        .build()

    private fun propertyType(): GraphQLInputObjectField = GraphQLInputObjectField.newInputObjectField()
        .name(ARG_PROPERTY_TYPE)
        .description("FQCN of the property to set")
        .type(GraphQLNonNull(GraphQLString))
        .build()

    private fun propertyValue(): GraphQLInputObjectField = GraphQLInputObjectField.newInputObjectField()
        .name(ARG_PROPERTY_VALUE)
        .description("JSON for the property value or null to delete the property")
        .type(GQLScalarJSON.INSTANCE)
        .build()

    private fun projectEntityTypeField(type: ProjectEntityType): GraphQLFieldDefinition =
        GraphQLFieldDefinition.newFieldDefinition()
            .name(type.varName)
            .description("${type.displayName.replaceFirstChar { it.uppercase() }} updated")
            .type(GraphQLTypeReference(type.typeName))
            .build()

    data class SetGenericPropertyInput(
        val entityType: ProjectEntityType,
        val entityId: Int,
        val type: String,
        val value: JsonNode,
    )

    data class DeleteGenericPropertyInput(
        val entityType: ProjectEntityType,
        val entityId: Int,
        val type: String,
    )

    companion object {
        const val ARG_ID = "id"
        const val ARG_PROPERTY_TYPE = "property"
        const val ARG_PROPERTY_VALUE = "value"
    }
}