package net.nemerosa.ontrack.extension.recordings

import tools.jackson.databind.JsonNode
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.model.extension.Extension
import kotlin.reflect.KClass

/**
 * Extension to define a schema for recording messages.
 *
 * This will provide, for the client extension:
 *
 * * some storage
 * * some services (recording, querying, cleaning)
 * * some settings
 * * some GraphQL types, queries and mutations
 * * a UI user menu extension
 * * a UI directive for a page showing all the records
 *
 * @param R Type of the recordings
 * @param F Type of filter
 */
interface RecordingsExtension<R : Recording, F : Any> : Extension {

    /**
     * Unique ID for the extension (used for storage keys and other elements)
     */
    val id: String

    /**
     * Prefix used for names of GraphQL elements (the first letter will be transformed to lower or upper case
     * depending on the context)
     */
    val graphQLPrefix: String

    /**
     * Short display name
     */
    val displayName: String

    /**
     * Creates the JSON representation of the recording so that it can be stored.
     */
    fun toJson(recording: R): JsonNode

    /**
     * Additional types needed for the extension
     */
    val graphQLContributions: Set<GraphQLType> get() = emptySet()

    /**
     * Gets the list of fields to set in the record.
     *
     * Each field will get an instance of the record [R] as a source.
     */
    fun graphQLRecordFields(cache: GQLTypeCache): List<GraphQLFieldDefinition>

    /**
     * Type of the filter
     */
    val filterType: KClass<F>

    /**
     * Given a stored JSON, parses it into the extension data.
     */
    fun fromJson(data: JsonNode): R

    /**
     * Gets the JSON queries to run for the filter
     */
    fun filterQuery(filter: F, queryVariables: MutableMap<String, Any?>): List<String>

}
