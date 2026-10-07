package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.readiness.ReadinessService
import net.nemerosa.ontrack.model.security.GlobalSettings
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.graphql.execution.ErrorType
import org.springframework.security.access.AccessDeniedException
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `Build.readiness` for a promotion level (#2022).
 */
@AsAdminTest
class BuildReadinessGraphQLIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var readinessService: ReadinessService

    private val readinessQuery = """
        query Readiness(${'$'}buildId: Int!, ${'$'}promotionLevel: String, ${'$'}slotId: String) {
            build(id: ${'$'}buildId) {
                readiness(promotionLevel: ${'$'}promotionLevel, slotId: ${'$'}slotId) {
                    ready
                    missing {
                        kind
                        name
                        message
                    }
                }
            }
        }
    """

    private fun Build.readiness(promotionLevel: String?, slotId: String? = null): JsonNode =
        run(
            readinessQuery,
            mapOf("buildId" to id(), "promotionLevel" to promotionLevel, "slotId" to slotId)
        ).path("build").path("readiness")

    private fun JsonNode.items(): List<Triple<String, String, String>> =
        path("missing").toList().map {
            Triple(it.path("kind").asString(), it.path("name").asString(), it.path("message").asString())
        }

    private fun JsonNode.kindsAndNames(): List<Pair<String, String>> =
        items().map { (kind, name, _) -> kind to name }

    private fun PromotionLevel.autoPromote(property: AutoPromotionProperty) {
        setProperty(this, AutoPromotionPropertyType::class.java, property)
    }

    @Test
    fun `Auto promotion lists the stamps which have not passed, with their last status`() {
        project {
            branch {
                val passed = validationStamp("BUILD")
                val failed = validationStamp("UNIT.TESTS")
                val notRun = validationStamp("ACCEPTANCE.TESTS")
                val silver = promotionLevel("SILVER")
                silver.autoPromote(AutoPromotionProperty(listOf(passed, failed, notRun), "", "", emptyList()))
                build {
                    validate(passed)
                    validate(failed, ValidationRunStatusID.STATUS_FAILED)
                    val readiness = readiness("SILVER")
                    assertFalse(readiness.path("ready").asBoolean())
                    assertEquals(
                        listOf(
                            Triple("VALIDATION", "UNIT.TESTS", "Last status: FAILED"),
                            Triple("VALIDATION", "ACCEPTANCE.TESTS", "Not validated"),
                        ),
                        readiness.items()
                    )
                }
            }
        }
    }

    @Test
    fun `Auto promotion lists the required levels which are not reached`() {
        project {
            branch {
                val bronze = promotionLevel("BRONZE")
                val iron = promotionLevel("IRON")
                val silver = promotionLevel("SILVER")
                silver.autoPromote(AutoPromotionProperty(emptyList(), "", "", listOf(bronze, iron)))
                build {
                    promote(bronze)
                    val readiness = readiness("SILVER")
                    assertFalse(readiness.path("ready").asBoolean())
                    assertEquals(
                        listOf(Triple("PROMOTION", "IRON", "Not promoted to IRON")),
                        readiness.items()
                    )
                }
            }
        }
    }

    @Test
    fun `Previous promotion condition and promotion dependencies failing together are all listed`() {
        withPreviousPromotionGlobalCondition {
            project {
                branch {
                    val iron = promotionLevel("IRON")
                    promotionLevel("SILVER")
                    promotionLevel("GOLD")
                    val platinum = promotionLevel("PLATINUM")
                    setProperty(
                        platinum,
                        PromotionDependenciesPropertyType::class.java,
                        PromotionDependenciesProperty(listOf("GOLD", "SILVER"))
                    )
                    build {
                        promote(iron)
                        val readiness = readiness("PLATINUM")
                        assertFalse(readiness.path("ready").asBoolean())
                        assertEquals(
                            listOf(
                                "CHECK" to "Previous promotion condition",
                                "CHECK" to "Promotion dependencies",
                                "CHECK" to "Promotion dependencies",
                                "MANUAL" to "PLATINUM",
                            ),
                            readiness.kindsAndNames()
                        )
                        val messages = readiness.items().map { it.third }
                        assertTrue(messages[0].contains("GOLD"), "Previous promotion is GOLD")
                        assertFalse(messages[0].contains("\n"), "One line")
                        assertTrue(messages[1].contains("requires the GOLD promotion"))
                        assertTrue(messages[2].contains("requires the SILVER promotion"))
                    }
                }
            }
        }
    }

    @Test
    fun `A level without auto promotion is granted by a person`() {
        project {
            branch {
                promotionLevel("GOLD")
                build {
                    val readiness = readiness("GOLD")
                    assertFalse(readiness.path("ready").asBoolean())
                    val (kind, name, message) = readiness.items().single()
                    assertEquals("MANUAL", kind)
                    assertEquals("GOLD", name)
                    assertTrue(message.contains("granted by a person"), message)
                    assertTrue(message.contains("even when nothing else is missing"), message)
                }
            }
        }
    }

    @Test
    fun `An already promoted build is ready`() {
        project {
            branch {
                val vs = validationStamp("BUILD")
                val silver = promotionLevel("SILVER")
                silver.autoPromote(AutoPromotionProperty(listOf(vs), "", "", emptyList()))
                val gold = promotionLevel("GOLD")
                build {
                    // Manually granted, whatever the conditions
                    promote(silver)
                    promote(gold)
                    listOf("SILVER", "GOLD").forEach { level ->
                        val readiness = readiness(level)
                        assertTrue(readiness.path("ready").asBoolean(), "Ready for $level")
                        assertTrue(readiness.path("missing").isEmpty, "Nothing missing for $level")
                    }
                }
            }
        }
    }

    @Test
    fun `A build auto promoted by its validations is ready`() {
        project {
            branch {
                val vs = validationStamp("BUILD")
                val silver = promotionLevel("SILVER")
                silver.autoPromote(AutoPromotionProperty(listOf(vs), "", "", emptyList()))
                build {
                    assertEquals(listOf("VALIDATION" to "BUILD"), readiness("SILVER").kindsAndNames())
                    validate(vs)
                    assertTrue(readiness("SILVER").path("ready").asBoolean())
                }
            }
        }
    }

    @Test
    fun `Neither a promotion level nor a slot is an input error`() {
        project {
            branch {
                build {
                    runWithMatchingError(
                        readinessQuery,
                        mapOf("buildId" to id()),
                        errorClassification = ErrorType.BAD_REQUEST,
                        errorMessage = "Exactly one of promotionLevel and slotId must be given for the readiness of a build.",
                    )
                }
            }
        }
    }

    @Test
    fun `Both a promotion level and a slot is an input error`() {
        project {
            branch {
                promotionLevel("GOLD")
                build {
                    runWithMatchingError(
                        readinessQuery,
                        mapOf("buildId" to id(), "promotionLevel" to "GOLD", "slotId" to "any"),
                        errorClassification = ErrorType.BAD_REQUEST,
                        errorMessage = "Exactly one of promotionLevel and slotId must be given for the readiness of a build.",
                    )
                }
            }
        }
    }

    @Test
    fun `A promotion level which is not on the build's branch is an input error`() {
        project {
            branch {
                build {
                    runWithMatchingError(
                        readinessQuery,
                        mapOf("buildId" to id(), "promotionLevel" to "UNKNOWN"),
                        errorClassification = ErrorType.BAD_REQUEST,
                        errorMessage = "There is no promotion level named UNKNOWN on the branch ${branch.name} of the build.",
                    )
                }
            }
        }
    }

    @Test
    fun `Readiness needs to see the build`() {
        project {
            branch {
                promotionLevel("GOLD")
                build {
                    val build = this
                    withNoGrantViewToAll {
                        asUser().execute {
                            assertFailsWith<AccessDeniedException> {
                                readinessService.getReadiness(build, "GOLD", null)
                            }
                        }
                        asUserWithView(build).execute {
                            assertFalse(readinessService.getReadiness(build, "GOLD", null).ready)
                        }
                    }
                }
            }
        }
    }

    private fun withPreviousPromotionGlobalCondition(code: () -> Unit) {
        val previous = settingsService.getCachedSettings(PreviousPromotionConditionSettings::class.java)
        try {
            asUser().with(GlobalSettings::class.java).execute {
                settingsManagerService.saveSettings(PreviousPromotionConditionSettings(previousPromotionRequired = true))
            }
            code()
        } finally {
            asUser().with(GlobalSettings::class.java).execute {
                settingsManagerService.saveSettings(previous)
            }
        }
    }
}
