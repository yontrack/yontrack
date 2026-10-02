package net.nemerosa.ontrack.extension.scorecard.casc

import net.nemerosa.ontrack.extension.casc.AbstractCascTestSupport
import net.nemerosa.ontrack.extension.findings.model.FindingKind
import net.nemerosa.ontrack.extension.license.DevLicenseService
import net.nemerosa.ontrack.extension.scorecard.estates.EstateEnvironmentMarker
import net.nemerosa.ontrack.extension.scorecard.estates.EstateInput
import net.nemerosa.ontrack.extension.scorecard.estates.EstatePromotionMarker
import net.nemerosa.ontrack.extension.scorecard.estates.EstateReadingConfig
import net.nemerosa.ontrack.extension.scorecard.estates.EstateSecurity
import net.nemerosa.ontrack.extension.scorecard.estates.EstateService
import net.nemerosa.ontrack.extension.scorecard.license.ScorecardLicensedFeatureProvider
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EstatesCascContextIT : AbstractCascTestSupport() {

    @Autowired
    private lateinit var estateService: EstateService

    @Autowired
    private lateinit var estatesCascContext: EstatesCascContext

    @Autowired
    private lateinit var devLicenseService: DevLicenseService

    @Test
    fun `Estates are created, updated and deleted as code`() {
        asAdmin {
            val a = label()
            val b = label(category = null)
            val prefix = uid("E")
            val obsolete = estateService.create(EstateInput(name = "$prefix-obsolete", labels = listOf(a.getDisplay())))

            val yaml = """
                ontrack:
                    config:
                        estates:
                            - name: $prefix-products
                              description: Products
                              labels:
                                - ${a.getDisplay()}
                                - ${b.getDisplay()}
                              marker:
                                kind: PROMOTION
                                levelName: GOLD
                              readings:
                                - key: delivery.leadTime
                                  windowDays: 30
                                  target: 86400
                                - key: delivery.frequency
                                  target: 5
                            - name: $prefix-production
                              labels:
                                - ${a.getDisplay()}
                              marker:
                                kind: ENVIRONMENT
                                environment: production
            """.trimIndent()
            assertValidYaml(yaml)
            casc(yaml)

            val products = estateService.findByName("$prefix-products")!!
            assertEquals("Products", products.description)
            assertEquals(setOf(a.id, b.id), products.labels.map { it.id }.toSet())
            assertEquals(EstatePromotionMarker("GOLD"), products.marker)
            assertEquals(
                listOf(
                    EstateReadingConfig("delivery.frequency", windowDays = null, target = 5.0),
                    EstateReadingConfig("delivery.leadTime", windowDays = 30, target = 86400.0),
                ),
                products.readingConfigs
            )
            val production = estateService.findByName("$prefix-production")!!
            assertEquals(EstateEnvironmentMarker("production", ""), production.marker)
            // Not listed, deleted
            assertNull(estateService.findByName(obsolete.name))

            // Rendering
            val rendered = estatesCascContext.render()
            val renderedProducts = rendered.values().single { it.path("name").asText() == products.name }
            assertEquals("GOLD", renderedProducts.path("marker").path("levelName").asText())
            assertEquals(2, renderedProducts.path("readings").size())

            // Update: the default marker, no reading configuration
            casc(
                """
                    ontrack:
                        config:
                            estates:
                                - name: $prefix-products
                                  labels:
                                    - ${a.getDisplay()}
                """.trimIndent()
            )
            val updated = estateService.findByName("$prefix-products")!!
            assertEquals(products.id, updated.id)
            assertNull(updated.description)
            assertNull(updated.marker)
            assertEquals(listOf(a.id), updated.labels.map { it.id })
            assertTrue(updated.readingConfigs.isEmpty())
            assertNull(estateService.findByName(production.name))
        }
    }

    @Test
    fun `What the estates expect of the security scans, as code`() {
        asAdmin {
            val a = label()
            val name = uid("E")
            val yaml = """
                ontrack:
                    config:
                        estates:
                            - name: $name
                              labels:
                                - ${a.getDisplay()}
                              security:
                                expectedKinds:
                                  - IMAGE
                                  - DEPENDENCIES
                                freshnessDays: 10
                                criticalTargetDays: 7
                                highTargetDays: 30
            """.trimIndent()
            assertValidYaml(yaml)
            casc(yaml)

            val estate = estateService.findByName(name)!!
            assertEquals(
                EstateSecurity(
                    expectedKinds = listOf(FindingKind.IMAGE, FindingKind.DEPENDENCIES),
                    freshnessDays = 10,
                    criticalTargetDays = 7,
                    highTargetDays = 30,
                ),
                estate.security
            )

            // Rendering
            val rendered = estatesCascContext.render().values().single { it.path("name").asText() == name }
            assertEquals(
                listOf("IMAGE", "DEPENDENCIES"),
                rendered.path("security").path("expectedKinds").values().map { it.asText() }
            )
            assertEquals(10, rendered.path("security").path("freshnessDays").asInt())
            assertEquals(7, rendered.path("security").path("criticalTargetDays").asInt())
            assertEquals(30, rendered.path("security").path("highTargetDays").asInt())

            // Omitted: no expectation
            casc(
                """
                    ontrack:
                        config:
                            estates:
                                - name: $name
                                  labels:
                                    - ${a.getDisplay()}
                """.trimIndent()
            )
            assertEquals(EstateSecurity(), estateService.findByName(name)!!.security)
        }
    }

    @Test
    fun `Without the licence, the estates as code are ignored`() {
        asAdmin {
            val a = label()
            val name = uid("E")
            devLicenseService.setFeatureEnabled(ScorecardLicensedFeatureProvider.FEATURE_SCORECARD, false)
            try {
                casc(
                    """
                        ontrack:
                            config:
                                estates:
                                    - name: $name
                                      labels:
                                        - ${a.getDisplay()}
                    """.trimIndent()
                )
                assertEquals(0, estatesCascContext.render().size())
            } finally {
                devLicenseService.setFeatureEnabled(ScorecardLicensedFeatureProvider.FEATURE_SCORECARD, true)
            }
            assertNull(estateService.findByName(name))
        }
    }
}
