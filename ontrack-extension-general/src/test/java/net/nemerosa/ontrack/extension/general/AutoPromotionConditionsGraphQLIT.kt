package net.nemerosa.ontrack.extension.general

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.PromotionRun
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@AsAdminTest
class AutoPromotionConditionsGraphQLIT : AbstractQLKTITSupport() {

    private fun PromotionLevel.autoPromote(property: AutoPromotionProperty) {
        setProperty(this, AutoPromotionPropertyType::class.java, property)
    }

    private fun levelConditions(pl: PromotionLevel): JsonNode =
        run(
            """
                {
                    promotionLevel(id: ${pl.id}) {
                        autoPromotionConditions {
                            include
                            exclude
                            autoRevoke
                            validationStamps { name }
                            promotionLevels { name }
                        }
                    }
                }
            """
        ).path("promotionLevel").path("autoPromotionConditions")

    private fun runConditions(run: PromotionRun): JsonNode =
        run(
            """
                {
                    promotionRuns(id: ${run.id}) {
                        autoPromotionConditions {
                            include
                            exclude
                            autoRevoke
                            validationStamps {
                                validationStamp { name }
                                lastRun { id }
                                passed
                            }
                            promotionLevels {
                                promotionLevel { name }
                                promotionRun { id }
                            }
                        }
                    }
                }
            """
        ).path("promotionRuns").path(0).path("autoPromotionConditions")

    private fun JsonNode.names(field: String, path: String? = null) =
        path(field).map { item -> (if (path != null) item.path(path) else item).path("name").asText() }

    private fun JsonNode.stamp(name: String): JsonNode =
        path("validationStamps").first { it.path("validationStamp").path("name").asText() == name }

    private fun JsonNode.level(name: String): JsonNode =
        path("promotionLevels").first { it.path("promotionLevel").path("name").asText() == name }

    @Test
    fun `No property gives no conditions`() {
        project {
            branch {
                val pl = promotionLevel()
                build {
                    val run = promote(pl)
                    assertTrue(levelConditions(pl).isNull)
                    assertTrue(runConditions(run).isNull)
                }
            }
        }
    }

    @Test
    fun `Empty property gives no conditions`() {
        project {
            branch {
                validationStamp()
                val pl = promotionLevel()
                pl.autoPromote(AutoPromotionProperty(emptyList(), "", "", emptyList()))
                build {
                    val run = promote(pl)
                    assertTrue(levelConditions(pl).isNull)
                    assertTrue(runConditions(run).isNull)
                }
            }
        }
    }

    @Test
    fun `Promotion level conditions resolve include and exclude in branch order`() {
        project {
            branch {
                val build = validationStamp("BUILD")
                validationStamp("UNIT.TESTS")
                validationStamp("INTEGRATION.TESTS")
                validationStamp("SECURITY.SCAN")
                validationStamp("SLOW.TESTS")
                val bronze = promotionLevel("BRONZE")
                val silver = promotionLevel("SILVER")
                silver.autoPromote(
                    AutoPromotionProperty(
                        validationStamps = listOf(build),
                        include = ".*TESTS",
                        exclude = "SLOW.*",
                        promotionLevels = listOf(bronze),
                        autoRevoke = true,
                    )
                )
                val conditions = levelConditions(silver)
                assertEquals(".*TESTS", conditions.path("include").asText())
                assertEquals("SLOW.*", conditions.path("exclude").asText())
                assertEquals(true, conditions.path("autoRevoke").asBoolean())
                assertEquals(
                    listOf("BUILD", "UNIT.TESTS", "INTEGRATION.TESTS"),
                    conditions.names("validationStamps")
                )
                assertEquals(listOf("BRONZE"), conditions.names("promotionLevels"))
            }
        }
    }

    @Test
    fun `Promotion run conditions give the state of each prerequisite for the build`() {
        project {
            branch {
                val notRun = validationStamp("BUILD")
                val failed = validationStamp("UNIT.TESTS")
                val fixed = validationStamp("INTEGRATION.TESTS")
                val passed = validationStamp("ACCEPTANCE.TESTS")
                validationStamp("SLOW.TESTS")
                val bronze = promotionLevel("BRONZE")
                val iron = promotionLevel("IRON")
                val silver = promotionLevel("SILVER")
                silver.autoPromote(
                    AutoPromotionProperty(
                        validationStamps = listOf(notRun),
                        include = ".*TESTS",
                        exclude = "SLOW.*",
                        promotionLevels = listOf(bronze, iron),
                    )
                )
                build {
                    // Passed first, then failed: only the latest run counts
                    validate(failed, ValidationRunStatusID.STATUS_PASSED)
                    val failedRun = validate(failed, ValidationRunStatusID.STATUS_FAILED)
                    val fixedRun = validate(fixed, ValidationRunStatusID.STATUS_FAILED)
                        .validationStatus(ValidationRunStatusID.STATUS_INVESTIGATING, "Investigating")
                        .validationStatus(ValidationRunStatusID.STATUS_FIXED, "Fixed")
                    val passedRun = validate(passed)
                    val bronzeRun = promote(bronze)
                    // Manually granted
                    val silverRun = promote(silver)

                    val conditions = runConditions(silverRun)
                    assertEquals(".*TESTS", conditions.path("include").asText())
                    assertEquals("SLOW.*", conditions.path("exclude").asText())
                    assertEquals(false, conditions.path("autoRevoke").asBoolean())

                    assertEquals(
                        listOf("BUILD", "UNIT.TESTS", "INTEGRATION.TESTS", "ACCEPTANCE.TESTS"),
                        conditions.names("validationStamps", "validationStamp")
                    )
                    conditions.stamp("BUILD").let {
                        assertTrue(it.path("lastRun").isNull, "Not run")
                        assertEquals(false, it.path("passed").asBoolean())
                    }
                    conditions.stamp("UNIT.TESTS").let {
                        assertEquals(failedRun.id(), it.path("lastRun").path("id").asInt())
                        assertEquals(false, it.path("passed").asBoolean())
                    }
                    conditions.stamp("INTEGRATION.TESTS").let {
                        assertEquals(fixedRun.id(), it.path("lastRun").path("id").asInt())
                        assertEquals(true, it.path("passed").asBoolean())
                    }
                    conditions.stamp("ACCEPTANCE.TESTS").let {
                        assertEquals(passedRun.id(), it.path("lastRun").path("id").asInt())
                        assertEquals(true, it.path("passed").asBoolean())
                    }

                    assertEquals(
                        listOf("BRONZE", "IRON"),
                        conditions.names("promotionLevels", "promotionLevel")
                    )
                    assertEquals(bronzeRun.id(), conditions.level("BRONZE").path("promotionRun").path("id").asInt())
                    assertTrue(conditions.level("IRON").path("promotionRun").isNull, "Not granted")
                }
            }
        }
    }

    @Test
    fun `Promotion run conditions point to the latest run of a required promotion`() {
        project {
            branch {
                val bronze = promotionLevel("BRONZE")
                val silver = promotionLevel("SILVER")
                silver.autoPromote(AutoPromotionProperty(emptyList(), "", "", listOf(bronze)))
                build {
                    promote(bronze)
                    // Auto promotion grants SILVER on BRONZE
                    val latestBronze = promote(bronze)
                    val silverRun = structureService.getLastPromotionRunForBuildAndPromotionLevel(this, silver)
                        .orElseThrow()
                    val conditions = runConditions(silverRun)
                    assertEquals(latestBronze.id(), conditions.level("BRONZE").path("promotionRun").path("id").asInt())
                }
            }
        }
    }
}
