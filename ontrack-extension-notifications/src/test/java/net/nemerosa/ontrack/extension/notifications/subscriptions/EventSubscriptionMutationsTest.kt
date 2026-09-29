package net.nemerosa.ontrack.extension.notifications.subscriptions

import graphql.schema.*
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.GraphQLBeanConverter
import net.nemerosa.ontrack.model.structure.ProjectEntityID
import net.nemerosa.ontrack.test.assertIs
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class EventSubscriptionMutationsTest {

    @Test
    fun `SubscribeToEventsInput input type`() {
        val dictionary = mutableSetOf<GraphQLType>()
        val type = GraphQLBeanConverter.asInputType(SubscribeToEventsInput::class, dictionary)

        assertIs<GraphQLInputObjectType>(type) { objectType ->
            assertNotNull(objectType.getField("events")) { eventsField ->
                assertIs<GraphQLNonNull>(eventsField.type) { eventsNotNull ->
                    assertIs<GraphQLList>(eventsNotNull.wrappedType) { eventsList ->
                        assertIs<GraphQLNonNull>(eventsList.wrappedType) { eventsItemNotNull ->
                            assertIs<GraphQLScalarType>(eventsItemNotNull.wrappedType) { eventsItem ->
                                assertEquals("String", eventsItem.name)
                            }
                        }
                    }
                }
            }
        }

        assertNotNull(dictionary.find { it is GraphQLInputObjectType && it.name == "ProjectEntityIDInput" },
            "ProjectEntityIDInput input type has been created")
    }

    @Test
    fun `SubscribeToEventsInput name is required`() {
        val type = GraphQLBeanConverter.asInputType(SubscribeToEventsInput::class, mutableSetOf())
        assertIs<GraphQLInputObjectType>(type) { objectType ->
            assertNotNull(objectType.getField("name")) { nameField ->
                assertIs<GraphQLNonNull>(nameField.type) { nameNotNull ->
                    assertIs<GraphQLScalarType>(nameNotNull.wrappedType) { nameType ->
                        assertEquals("String", nameType.name)
                    }
                }
            }
        }
    }

    @Test
    fun `EventSubscriptionPayload has no id field`() {
        val type = GQLTypeEventSubscriptionPayload().createType(GQLTypeCache())
        assertNull(type.getFieldDefinition("id"), "The id field is removed in V6")
        assertNotNull(type.getFieldDefinition("name"), "The name field identifies the subscription")
    }

    @Test
    fun `ProjectEntityID input type`() {
        val type = GraphQLBeanConverter.asInputType(ProjectEntityID::class, mutableSetOf())
        assertIs<GraphQLInputObjectType>(type) { input ->
            assertNotNull(input.getField("type")) { typeField ->
                assertIs<GraphQLNonNull>(typeField.type) { typeNotNullType ->
                    assertIs<GraphQLTypeReference>(typeNotNullType.wrappedType) { typeType ->
                        assertEquals("ProjectEntityType", typeType.name)
                    }
                }
            }

        }
    }

}