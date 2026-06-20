package net.nemerosa.ontrack.graphql.schema

import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.exceptions.PromotionRunFieldRequiredException
import net.nemerosa.ontrack.model.exceptions.PromotionRunFieldTypeException
import net.nemerosa.ontrack.model.structure.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PromotionLevelFieldsIT : AbstractQLKTITSupport() {

    @Test
    fun `Setting and querying promotion level fields via GraphQL`() {
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    // Set fields via mutation
                    run("""
                        mutation {
                            setPromotionLevelFields(input: {
                                promotionLevelId: ${pl.id()},
                                fields: [
                                    { name: "ticket", displayName: "Ticket", type: TEXT, required: true },
                                    { name: "approved", displayName: "Approved", type: BOOLEAN, required: false }
                                ]
                            }) {
                                errors { message }
                            }
                        }
                    """) { data ->
                        assertTrue(
                            data.path("setPromotionLevelFields").path("errors").isEmpty,
                            "No errors expected"
                        )
                    }
                    // Query fields
                    run("""
                        {
                            promotionLevel(id: ${pl.id()}) {
                                fields {
                                    name
                                    displayName
                                    type
                                    required
                                }
                            }
                        }
                    """) { data ->
                        val fields = data.path("promotionLevel").path("fields")
                        assertEquals(2, fields.size())
                        assertEquals("ticket", fields[0].path("name").asText())
                        assertEquals("Ticket", fields[0].path("displayName").asText())
                        assertEquals("TEXT", fields[0].path("type").asText())
                        assertEquals(true, fields[0].path("required").asBoolean())
                        assertEquals("approved", fields[1].path("name").asText())
                        assertEquals("BOOLEAN", fields[1].path("type").asText())
                        assertEquals(false, fields[1].path("required").asBoolean())
                    }
                }
            }
        }
    }

    @Test
    fun `Creating a promotion run without a required field throws an error`() {
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    structureService.setPromotionLevelFields(
                        pl.id,
                        listOf(
                            PromotionLevelField(0, "ticket", "Ticket", null, PromotionLevelFieldType.TEXT, required = true, emptyList(), 0),
                        )
                    )
                    build {
                        assertFailsWith<PromotionRunFieldRequiredException> {
                            structureService.newPromotionRun(
                                PromotionRun.of(
                                    build = this,
                                    promotionLevel = structureService.getPromotionLevel(pl.id),
                                    signature = Signature.anonymous(),
                                    description = null,
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Creating a promotion run with wrong field type throws an error`() {
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    structureService.setPromotionLevelFields(
                        pl.id,
                        listOf(
                            PromotionLevelField(0, "count", "Count", null, PromotionLevelFieldType.NUMBER, required = true, emptyList(), 0),
                        )
                    )
                    build {
                        assertFailsWith<PromotionRunFieldTypeException> {
                            structureService.newPromotionRun(
                                PromotionRun.of(
                                    build = this,
                                    promotionLevel = structureService.getPromotionLevel(pl.id),
                                    signature = Signature.anonymous(),
                                    description = null,
                                ).withFieldValues(listOf(PromotionRunFieldValue("count", "not-a-number".asJson())))
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Field values for a promotion run are persisted and loaded`() {
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    structureService.setPromotionLevelFields(
                        pl.id,
                        listOf(
                            PromotionLevelField(0, "env", "Environment", null, PromotionLevelFieldType.CHOICE, required = false, listOf("prod", "staging"), 0),
                        )
                    )
                    build {
                        val createdRun = structureService.newPromotionRun(
                            PromotionRun.of(
                                build = this,
                                promotionLevel = structureService.getPromotionLevel(pl.id),
                                signature = Signature.anonymous(),
                                description = null,
                            ).withFieldValues(listOf(PromotionRunFieldValue("env", "prod".asJson())))
                        )
                        // Reload and verify
                        val loaded = structureService.getPromotionRun(createdRun.id)
                        val fv = loaded.fieldValues.firstOrNull { it.name == "env" }
                        assertNotNull(fv, "env field value should be present")
                        assertEquals("prod", fv.value?.asText())
                    }
                }
            }
        }
    }

    @Test
    fun `LINK field value is persisted and returned correctly`() {
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    structureService.setPromotionLevelFields(
                        pl.id,
                        listOf(
                            PromotionLevelField(0, "ref", "Reference", null, PromotionLevelFieldType.LINK, required = true, emptyList(), 0),
                        )
                    )
                    build {
                        val createdRun = structureService.newPromotionRun(
                            PromotionRun.of(
                                build = this,
                                promotionLevel = structureService.getPromotionLevel(pl.id),
                                signature = Signature.anonymous(),
                                description = null,
                            ).withFieldValues(listOf(PromotionRunFieldValue("ref", "https://example.com/ticket/42".asJson())))
                        )
                        val loaded = structureService.getPromotionRun(createdRun.id)
                        val fv = loaded.fieldValues.firstOrNull { it.name == "ref" }
                        assertNotNull(fv, "ref field value should be present")
                        assertEquals("https://example.com/ticket/42", fv.value?.asText())
                    }
                }
            }
        }
    }

    @Test
    fun `Creating a promotion run with non-string value for LINK field throws an error`() {
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    structureService.setPromotionLevelFields(
                        pl.id,
                        listOf(
                            PromotionLevelField(0, "ref", "Reference", null, PromotionLevelFieldType.LINK, required = true, emptyList(), 0),
                        )
                    )
                    build {
                        assertFailsWith<PromotionRunFieldTypeException> {
                            structureService.newPromotionRun(
                                PromotionRun.of(
                                    build = this,
                                    promotionLevel = structureService.getPromotionLevel(pl.id),
                                    signature = Signature.anonymous(),
                                    description = null,
                                ).withFieldValues(listOf(PromotionRunFieldValue("ref", 123.asJson())))
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Field values are accessible via GraphQL on the promotion run`() {
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    structureService.setPromotionLevelFields(
                        pl.id,
                        listOf(
                            PromotionLevelField(0, "ticket", "Ticket", null, PromotionLevelFieldType.TEXT, required = true, emptyList(), 0),
                        )
                    )
                    build {
                        val run = structureService.newPromotionRun(
                            PromotionRun.of(
                                build = this,
                                promotionLevel = structureService.getPromotionLevel(pl.id),
                                signature = Signature.anonymous(),
                                description = null,
                            ).withFieldValues(listOf(PromotionRunFieldValue("ticket", "PROJ-42".asJson())))
                        )
                        run("""
                            {
                                promotionRuns(id: ${run.id()}) {
                                    fieldValues {
                                        name
                                        value
                                    }
                                }
                            }
                        """) { data ->
                            val fv = data.path("promotionRuns").first().path("fieldValues")
                            assertEquals(1, fv.size())
                            assertEquals("ticket", fv[0].path("name").asText())
                            assertEquals("PROJ-42", fv[0].path("value").asText())
                        }
                    }
                }
            }
        }
    }
}
