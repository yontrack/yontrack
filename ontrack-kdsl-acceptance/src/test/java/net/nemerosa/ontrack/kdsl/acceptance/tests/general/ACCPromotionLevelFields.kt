package net.nemerosa.ontrack.kdsl.acceptance.tests.general

import com.apollographql.apollo.api.Optional
import com.fasterxml.jackson.databind.node.TextNode
import net.nemerosa.ontrack.kdsl.acceptance.tests.AbstractACCDSLTestSupport
import net.nemerosa.ontrack.kdsl.connector.graphql.GraphQLClientException
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.PromotionLevelFieldInput
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.PromotionLevelFieldType
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.PromotionRunFieldValueInput
import net.nemerosa.ontrack.kdsl.spec.getFieldValues
import net.nemerosa.ontrack.kdsl.spec.getFields
import net.nemerosa.ontrack.kdsl.spec.setFields
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import net.nemerosa.ontrack.kdsl.spec.PromotionLevelFieldType as KDSLPromotionLevelFieldType

class ACCPromotionLevelFields : AbstractACCDSLTestSupport() {

    @Test
    fun `Setting and reading field definitions on a promotion level`() {
        project {
            branch {
                val pl = promotion()
                pl.setFields(
                    listOf(
                        PromotionLevelFieldInput(
                            name = "ticket",
                            displayName = "Ticket",
                            type = PromotionLevelFieldType.TEXT,
                            required = true,
                        ),
                        PromotionLevelFieldInput(
                            name = "approved",
                            displayName = "Approved",
                            type = PromotionLevelFieldType.BOOLEAN,
                            required = false,
                        ),
                    )
                )
                val fields = pl.getFields()
                assertEquals(2, fields.size)
                assertEquals("ticket", fields[0].name)
                assertEquals(KDSLPromotionLevelFieldType.TEXT, fields[0].type)
                assertEquals(true, fields[0].required)
                assertEquals("approved", fields[1].name)
                assertEquals(KDSLPromotionLevelFieldType.BOOLEAN, fields[1].type)
                assertEquals(false, fields[1].required)
            }
        }
    }

    @Test
    fun `Promoting a build with field values persists and returns them`() {
        project {
            branch {
                val pl = promotion()
                pl.setFields(
                    listOf(
                        PromotionLevelFieldInput(
                            name = "ticket",
                            displayName = "Ticket",
                            type = PromotionLevelFieldType.TEXT,
                            required = true,
                        ),
                    )
                )
                build {
                    val run = promote(
                        pl.name,
                        fieldValues = listOf(
                            PromotionRunFieldValueInput(
                                name = "ticket",
                                value = Optional.present(TextNode("PROJ-42")),
                            )
                        )
                    )
                    val fieldValues = run.getFieldValues()
                    assertEquals(1, fieldValues.size)
                    assertEquals("ticket", fieldValues[0].name)
                    assertEquals("PROJ-42", fieldValues[0].value?.asText())
                }
            }
        }
    }

    @Test
    fun `Promoting without a required field throws an error`() {
        project {
            branch {
                val pl = promotion()
                pl.setFields(
                    listOf(
                        PromotionLevelFieldInput(
                            name = "ticket",
                            displayName = "Ticket",
                            type = PromotionLevelFieldType.TEXT,
                            required = true,
                        ),
                    )
                )
                build {
                    assertFailsWith<GraphQLClientException> {
                        promote(pl.name)
                    }
                }
            }
        }
    }

    @Test
    fun `Promoting a build with a LINK field value persists and returns the URL`() {
        project {
            branch {
                val pl = promotion()
                pl.setFields(
                    listOf(
                        PromotionLevelFieldInput(
                            name = "ref",
                            displayName = "Reference",
                            type = PromotionLevelFieldType.LINK,
                            required = true,
                        ),
                    )
                )
                build {
                    val run = promote(
                        pl.name,
                        fieldValues = listOf(
                            PromotionRunFieldValueInput(
                                name = "ref",
                                value = Optional.present(TextNode("https://example.com/ticket/42")),
                            )
                        )
                    )
                    val fieldValues = run.getFieldValues()
                    assertEquals(1, fieldValues.size)
                    assertEquals("ref", fieldValues[0].name)
                    assertEquals("https://example.com/ticket/42", fieldValues[0].value?.asText())
                }
            }
        }
    }

    @Test
    fun `Promoting with a CHOICE value outside allowed options throws an error`() {
        project {
            branch {
                val pl = promotion()
                pl.setFields(
                    listOf(
                        PromotionLevelFieldInput(
                            name = "env",
                            displayName = "Environment",
                            type = PromotionLevelFieldType.CHOICE,
                            required = true,
                            options = Optional.present(listOf("prod", "staging")),
                        ),
                    )
                )
                build {
                    assertFailsWith<GraphQLClientException> {
                        promote(
                            pl.name,
                            fieldValues = listOf(
                                PromotionRunFieldValueInput(
                                    name = "env",
                                    value = Optional.present(TextNode("dev")),
                                )
                            )
                        )
                    }
                }
            }
        }
    }

}
