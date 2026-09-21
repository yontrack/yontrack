package net.nemerosa.ontrack.graphql.support

import graphql.schema.*
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.schema.UpdateProjectInput
import net.nemerosa.ontrack.model.structure.NameDescriptionState
import net.nemerosa.ontrack.test.assertIs
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

class GraphQLBeanConverterTest {

    private lateinit var cache: GQLTypeCache

    @BeforeEach
    fun before() {
        cache = GQLTypeCache()
    }

    @Test
    fun `Recursive creation of input types`() {
        val dictionary = mutableSetOf<GraphQLType>()
        val entityRef = GraphQLBeanConverter.asInputType(EntityRef::class, dictionary)
        assertIs<GraphQLInputObjectType>(entityRef) { entityRefInput ->
            assertIs<GraphQLNonNull>(entityRefInput.getField("entity").type) { entityNotNull ->
                assertIs<GraphQLTypeReference>(entityNotNull.wrappedType) { entity ->
                    assertEquals("Entity", entity.name)
                }
            }
        }
        assertEquals(1, dictionary.size)
        val type = dictionary.first()
        assertIs<GraphQLInputObjectType>(type) {
            assertEquals("Entity", it.name)
        }
    }

    @Test
    fun `Enum type for an input`() {
        val fields = GraphQLBeanConverter.asInputFields(Entity::class, mutableSetOf())
        val indexedFields = fields.associate {
            it.name to typeName(it.type)
        }
        assertEquals(
            mapOf(
                "type" to "EntityType!",
                "id" to "Int!",
            ),
            indexedFields
        )
    }

    @Test
    fun `Scalar types`() {
        assertNotNull(GraphQLBeanConverter.getScalarType(String::class.java)) {
            assertEquals("String", it.name)
        }
        assertNotNull(GraphQLBeanConverter.getScalarType(Int::class.java)) {
            assertEquals("Int", it.name)
        }
        assertNotNull(GraphQLBeanConverter.getScalarType(Integer::class.java)) {
            assertEquals("Int", it.name)
        }
    }

    @Test
    fun `Simple type`() {
        val type = GraphQLBeanConverter.asObjectType(Person::class, cache)
        assertEquals("Person", type.name)
        val fields = fieldToTypeNames(type.fieldDefinitions)
        assertEquals(
            mapOf(
                "address" to "String!",
                "age" to "Int!",
                "name" to "String!",
                "developer" to "Boolean!",
                "experience" to "Int"
            ),
            fields
        )
    }

    @Test
    fun `Simple input type`() {
        val fields = GraphQLBeanConverter.asInputFields(Person::class, mutableSetOf())
        val indexedFields = fields.associate {
            it.name to typeName(it.type)
        }
        assertEquals(
            mapOf(
                "address" to "String!",
                "age" to "Int!",
                "name" to "String!",
                "developer" to "Boolean!",
                "experience" to "Int"
            ),
            indexedFields
        )
    }

    @Test
    fun `Description from annotation`() {
        val type = GraphQLBeanConverter.asObjectType(Person::class, cache)
        assertEquals("Person", type.name)
        assertNotNull(type.fieldDefinitions.find { it.name == "name" }) {
            assertEquals("Full name", it.description)
        }
        assertNotNull(type.fieldDefinitions.find { it.name == "address" }) {
            assertEquals("Full postal address", it.description)
        }
    }

    @Test
    fun `Composite type`() {
        val type = GraphQLBeanConverter.asObjectType(Account::class, cache)
        assertEquals("Account", type.name)
        val fields = fieldToTypeNames(type.fieldDefinitions)
        assertEquals(
            mapOf(
                "username" to "String!",
                "password" to "String!",
                "identity" to "Person!",
            ),
            fields
        )
    }

    @Test
    fun `List of strings`() {
        val type = GraphQLBeanConverter.asObjectType(StringListWrapper::class, cache)
        assertNotNull(type.fieldDefinitions.find { it.name == "list" }) { listField ->
            assertIs<GraphQLNonNull>(listField.type) { nonNullList ->
                assertIs<GraphQLList>(nonNullList.wrappedType) { list ->
                    assertIs<GraphQLNonNull>(list.wrappedType) { nonNullItem ->
                        assertIs<GraphQLScalarType>(nonNullItem.wrappedType) { scalar ->
                            assertEquals("String", scalar.name)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Composite type with three levels`() {
        val type = GraphQLBeanConverter.asObjectType(OnBehalf::class, cache)
        assertEquals("OnBehalf", type.name)
        val fields = fieldToTypeNames(type.fieldDefinitions)
        assertEquals(
            mapOf(
                "delegate" to "Account!",
                "account" to "Account!"
            ),
            fields
        )
    }

    @Test
    fun `Input fields for NameDescriptionState`() {
        val fields =
            GraphQLBeanConverter.asInputFields(NameDescriptionState::class, mutableSetOf()).associateBy { it.name }
        assertNotNull(fields["name"]) {
            assertEquals("String!", typeName(it.type))
            assertEquals("name field", it.description)
        }
        assertNotNull(fields["description"]) {
            assertEquals("String", typeName(it.type))
            assertEquals("description field", it.description)
        }
        assertNotNull(fields["disabled"]) {
            assertEquals("Boolean!", typeName(it.type))
            assertEquals("disabled field", it.description)
        }
    }

    @Test
    fun `Input fields for UpdateProjectInput`() {
        val fields =
            GraphQLBeanConverter.asInputFields(UpdateProjectInput::class, mutableSetOf()).associateBy { it.name }
        assertNotNull(fields["id"]) {
            assertEquals("Int!", typeName(it.type))
            assertEquals("Project ID", it.description)
        }
        assertNotNull(fields["name"]) {
            assertEquals("String", typeName(it.type))
            assertEquals("Project name (leave null to not change)", it.description)
        }
        assertNotNull(fields["description"]) {
            assertEquals("String", typeName(it.type))
            assertEquals("Project description (leave null to not change)", it.description)
        }
        assertNotNull(fields["disabled"]) {
            assertEquals("Boolean", typeName(it.type))
            assertEquals("Project state (leave null to not change)", it.description)
        }
    }

    @Test
    fun `Input list fields`() {
        val type = GraphQLBeanConverter.asInputType(Group::class, mutableSetOf()) as GraphQLInputObjectType
        val accounts = type.getField("accounts") ?: fail("Cannot find account field")
        assertIs<GraphQLNonNull>(accounts.type) { root ->
            assertIs<GraphQLList>(root.wrappedType) { listType ->
                assertIs<GraphQLNonNull>(listType.wrappedType) { elementType ->
                    assertIs<GraphQLNamedInputType>(elementType.wrappedType) { namedType ->
                        assertEquals("Account", namedType.name)
                    }
                }
            }
        }
    }

    companion object {

        fun fieldToTypeNames(fields: List<GraphQLFieldDefinition>): Map<String, String> =
            fields.associate {
                it.name to typeName(it.type)
            }

    }

}
