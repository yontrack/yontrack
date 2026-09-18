package net.nemerosa.ontrack.extension.environments.ui

import net.nemerosa.ontrack.extension.environments.SlotAdmissionRuleTestFixtures
import net.nemerosa.ontrack.extension.environments.SlotTestSupport
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@AsAdminTest
class SlotAdmissionRuleConfigGraphQLIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var slotTestSupport: SlotTestSupport

    @Autowired
    private lateinit var slotService: SlotService

    @Test
    fun `Getting the list of admission rules`() {
        run(
            """
            {
                slotAdmissionRules {
                    id
                    name
                }
            }
        """.trimIndent()
        ) { data ->
            val rules = data.path("slotAdmissionRules")
            val rule = rules.find { it.path("id").asText() == "promotion" }
            assertNotNull(rule, "Promotion rule found") {
                assertEquals("Promotion", it.path("name").asText())
            }
        }
    }

    @Test
    fun `Getting the list of admission rules for a slot`() {
        slotTestSupport.withSlot { slot ->
            val config = SlotAdmissionRuleTestFixtures.testPromotionAdmissionRuleConfig(slot)
            slotService.addAdmissionRuleConfig(
                config = config,
            )
            run(
                """
                    {
                        slotById(id: "${slot.id}") {
                            admissionRules {
                                id
                                name
                                description
                                ruleId
                                ruleConfig
                            }
                        }
                    }
                """
            ) { data ->
                val rules = data.path("slotById").path("admissionRules")
                assertEquals(1, rules.size())
                val rule = rules.first()
                assertEquals(config.id, rule.path("id").asText())
                assertEquals(config.name, rule.path("name").asText())
                assertEquals(config.ruleId, rule.path("ruleId").asText())
                assertEquals(config.ruleConfig, rule.path("ruleConfig"))
            }
        }
    }

    @Test
    fun `Creating a new admission rule`() {
        slotTestSupport.withSlot { slot ->
            val name = uid("rule-")
            run(
                """
                    mutation {
                        saveSlotAdmissionRuleConfig(input: {
                            slotId: "${slot.id}",
                            name: "$name",
                            description: "Rule $name",
                            ruleId: "promotion",
                            ruleConfig: {
                                promotion: "GOLD"
                            },
                        }) {
                            errors {
                                message
                            }
                        }
                    }
                """.trimIndent()
            ) { data ->
                checkGraphQLUserErrors(data, "saveSlotAdmissionRuleConfig")
                val rules = slotService.getAdmissionRuleConfigs(slot)
                val rule = rules.find { it.name == name }
                assertNotNull(rule, "Rule found") {
                    assertEquals("Rule $name", it.description)
                    assertEquals("promotion", it.ruleId)
                    assertEquals("GOLD", it.ruleConfig.path("promotion").asText())
                }
            }
        }
    }

    /**
     * Editing an existing rule, rather than deleting it and adding it back (#1793).
     *
     * The slot is deliberately *not* passed: the rule's id already says which slot it belongs to,
     * and accepting both would make it possible to ask for a rule to be moved, which the storage
     * does not support and nothing wants.
     */
    @Test
    fun `Saving changes on an existing admission rule`() {
        slotTestSupport.withSlot { slot ->
            val config = SlotAdmissionRuleTestFixtures.testPromotionAdmissionRuleConfig(slot)
            slotService.addAdmissionRuleConfig(config = config)
            val newDescription = uid("desc-")
            run(
                """
                    mutation {
                        saveSlotAdmissionRuleConfig(input: {
                            id: "${config.id}",
                            name: "${config.name}",
                            description: "$newDescription",
                            ruleId: "promotion",
                            ruleConfig: {
                                promotion: "SILVER"
                            },
                        }) {
                            errors {
                                message
                            }
                        }
                    }
                """.trimIndent()
            ) { data ->
                checkGraphQLUserErrors(data, "saveSlotAdmissionRuleConfig")
                // The rule is *edited*, not replaced: same id, and still only one rule on the slot.
                val rules = slotService.getAdmissionRuleConfigs(slot)
                assertEquals(1, rules.size)
                val rule = rules.find { it.id == config.id }
                assertNotNull(rule, "Rule found") {
                    assertEquals(config.name, it.name)
                    assertEquals(newDescription, it.description)
                    assertEquals("promotion", it.ruleId)
                    assertEquals("SILVER", it.ruleConfig.path("promotion").asText())
                }
            }
        }
    }

    @Test
    fun `Saving changes on an admission rule which does not exist`() {
        val id = uid("x-")
        run(
            """
                mutation {
                    saveSlotAdmissionRuleConfig(input: {
                        id: "$id",
                        name: "promotion",
                        description: "",
                        ruleId: "promotion",
                        ruleConfig: {
                            promotion: "SILVER"
                        },
                    }) {
                        errors {
                            message
                        }
                    }
                }
            """.trimIndent()
        ) { data ->
            val errors = data.path("saveSlotAdmissionRuleConfig").path("errors")
            assertEquals(1, errors.size())
            assertEquals(
                "Admission rule config '$id' not found",
                errors.first().path("message").asText(),
            )
        }
    }

    /**
     * Deleting a rule which is not there is a no-op, as the mutation has always said it was.
     *
     * It used to blow up: `findAdmissionRuleConfigById` promised a nullable answer and its query
     * threw on an empty result instead, so the `if (config != null)` guard was never reached.
     */
    @Test
    fun `Deleting an admission rule which does not exist`() {
        run(
            """
                mutation {
                    deleteSlotAdmissionRuleConfig(input: {id: "${uid("x-")}"}) {
                        errors {
                            message
                        }
                    }
                }
            """.trimIndent()
        ) { data ->
            checkGraphQLUserErrors(data, "deleteSlotAdmissionRuleConfig")
        }
    }

    /**
     * Passing both is refused rather than silently resolved: the two could disagree, and the only
     * honest answer to "edit rule X of slot Y" when X does not belong to Y is to say so.
     */
    @Test
    fun `Saving an existing admission rule refuses a slot id as well`() {
        slotTestSupport.withSlot { slot ->
            val config = SlotAdmissionRuleTestFixtures.testPromotionAdmissionRuleConfig(slot)
            slotService.addAdmissionRuleConfig(config = config)
            run(
                """
                    mutation {
                        saveSlotAdmissionRuleConfig(input: {
                            id: "${config.id}",
                            slotId: "${slot.id}",
                            name: "${config.name}",
                            description: "",
                            ruleId: "promotion",
                            ruleConfig: {
                                promotion: "SILVER"
                            },
                        }) {
                            errors {
                                message
                            }
                        }
                    }
                """.trimIndent()
            ) { data ->
                val errors = data.path("saveSlotAdmissionRuleConfig").path("errors")
                assertEquals(1, errors.size())
                assertEquals(
                    "If ID is provided, the ID of slot is not needed.",
                    errors.first().path("message").asText(),
                )
            }
        }
    }

}