package net.nemerosa.ontrack.extension.agents.assisted

import net.nemerosa.ontrack.extension.agents.license.AgentsLicensedFeatureProvider.Companion.FEATURE_AGENTS
import net.nemerosa.ontrack.extension.general.AutoPromotionProperty
import net.nemerosa.ontrack.extension.general.AutoPromotionPropertyType
import net.nemerosa.ontrack.extension.license.DevLicenseService
import net.nemerosa.ontrack.extension.scm.changelog.assistants.AssistedChangeBasis
import net.nemerosa.ontrack.extension.scm.changelog.assistants.AssistedChangeProperty
import net.nemerosa.ontrack.extension.scm.changelog.assistants.AssistedChangePropertyType
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.model.readiness.ReadinessItem
import net.nemerosa.ontrack.model.readiness.ReadinessKind
import net.nemerosa.ontrack.model.readiness.ReadinessService
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * *Assisted builds require* on a promotion level (#2029): if the build is assisted, the listed stamps
 * must pass before it is promoted - while the licence allows the agent governance.
 */
class AssistedBuildsRequireIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var devLicenseService: DevLicenseService

    @Autowired
    private lateinit var readinessService: ReadinessService

    private fun <T> withoutAgentsLicence(code: () -> T): T {
        devLicenseService.setFeatureEnabled(FEATURE_AGENTS, false)
        return try {
            code()
        } finally {
            devLicenseService.setFeatureEnabled(FEATURE_AGENTS, true)
        }
    }

    private fun PromotionLevel.requireOfAssistedBuilds(vararg stamps: String) {
        setProperty(this, AssistedBuildsRequirePropertyType::class.java, AssistedBuildsRequireProperty(stamps.toList()))
    }

    private fun Build.assisted() {
        setProperty(
            this, AssistedChangePropertyType::class.java, AssistedChangeProperty(
                basis = AssistedChangeBasis.COMPUTED,
                assistants = listOf("Claude Code"),
                assistedCommits = 1,
                totalCommits = 2,
            )
        )
    }

    private fun Build.notAssisted() {
        setProperty(
            this, AssistedChangePropertyType::class.java, AssistedChangeProperty(
                basis = AssistedChangeBasis.COMPUTED,
                assistedCommits = 0,
                totalCommits = 2,
            )
        )
    }

    private fun Build.isPromoted(pl: PromotionLevel) =
        structureService.getPromotionRunsForBuildAndPromotionLevel(this, pl).isNotEmpty()

    @Test
    fun `Licence off - an assisted build without the stamp is promoted`() {
        asAdmin {
            project {
                branch {
                    validationStamp("REVIEW")
                    val gold = promotionLevel("GOLD")
                    gold.requireOfAssistedBuilds("REVIEW")
                    build {
                        assisted()
                        withoutAgentsLicence {
                            assertTrue(readinessService.getPromotionLevelReadiness(this, gold).missing.none {
                                it.kind == ReadinessKind.CHECK
                            }, "Nothing missing for the check")
                            promote(gold)
                        }
                        assertTrue(isPromoted(gold), "Promoted without the licence")
                    }
                }
            }
        }
    }

    @Test
    fun `An assisted build without the stamp is refused, and promoted with it`() {
        asAdmin {
            project {
                branch {
                    val review = validationStamp("REVIEW")
                    val gold = promotionLevel("GOLD")
                    gold.requireOfAssistedBuilds("REVIEW")
                    build {
                        assisted()
                        val ex = assertFailsWith<AssistedBuildsRequireException> {
                            promote(gold)
                        }
                        assertEquals("Assisted build: REVIEW must pass first.", ex.message)
                        assertTrue(!isPromoted(gold), "Not promoted")
                        // A failed review is not enough
                        validate(review, ValidationRunStatusID.STATUS_FAILED)
                        assertFailsWith<AssistedBuildsRequireException> {
                            promote(gold)
                        }
                        // A passed review is
                        validate(review)
                        promote(gold)
                        assertTrue(isPromoted(gold), "Promoted")
                    }
                }
            }
        }
    }

    @Test
    fun `The last run of the stamp counts`() {
        asAdmin {
            project {
                branch {
                    val review = validationStamp("REVIEW")
                    val gold = promotionLevel("GOLD")
                    gold.requireOfAssistedBuilds("REVIEW")
                    build {
                        assisted()
                        validate(review)
                        validate(review, ValidationRunStatusID.STATUS_FAILED)
                        assertFailsWith<AssistedBuildsRequireException> {
                            promote(gold)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `A build which is computed as not assisted is promoted without the stamp`() {
        asAdmin {
            project {
                branch {
                    validationStamp("REVIEW")
                    val gold = promotionLevel("GOLD")
                    gold.requireOfAssistedBuilds("REVIEW")
                    build {
                        notAssisted()
                        promote(gold)
                        assertTrue(isPromoted(gold), "Promoted")
                    }
                }
            }
        }
    }

    @Test
    fun `Fail closed - a build whose assisted change is absent is treated as assisted`() {
        asAdmin {
            project {
                branch {
                    validationStamp("REVIEW")
                    val gold = promotionLevel("GOLD")
                    gold.requireOfAssistedBuilds("REVIEW")
                    build {
                        assertNull(getProperty(this, AssistedChangePropertyType::class.java), "Not computed")
                        assertFailsWith<AssistedBuildsRequireException> {
                            promote(gold)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Fail closed - a build whose assisted change is unknown is treated as assisted`() {
        asAdmin {
            project {
                branch {
                    val review = validationStamp("REVIEW")
                    val gold = promotionLevel("GOLD")
                    gold.requireOfAssistedBuilds("REVIEW")
                    build {
                        setProperty(
                            this,
                            AssistedChangePropertyType::class.java,
                            AssistedChangeProperty.unknown(AssistedChangeProperty.REASON_NO_SCM)
                        )
                        assertFailsWith<AssistedBuildsRequireException> {
                            promote(gold)
                        }
                        validate(review)
                        promote(gold)
                        assertTrue(isPromoted(gold), "Promoted")
                    }
                }
            }
        }
    }

    @Test
    fun `A level without the property is not concerned`() {
        asAdmin {
            project {
                branch {
                    validationStamp("REVIEW")
                    val gold = promotionLevel("GOLD")
                    build {
                        assisted()
                        promote(gold)
                        assertTrue(isPromoted(gold), "Promoted")
                    }
                }
            }
        }
    }

    @Test
    fun `Readiness lists every stamp the assisted build has not passed`() {
        asAdmin {
            project {
                branch {
                    val review = validationStamp("REVIEW")
                    validationStamp("SCAN")
                    val gold = promotionLevel("GOLD")
                    gold.requireOfAssistedBuilds("REVIEW", "SCAN")
                    build {
                        assisted()
                        val checks = readinessService.getPromotionLevelReadiness(this, gold)
                            .missing.filter { it.kind == ReadinessKind.CHECK }
                        assertEquals(
                            listOf(
                                ReadinessItem(ReadinessKind.CHECK, "Assisted builds require", "Assisted build: REVIEW must pass first."),
                                ReadinessItem(ReadinessKind.CHECK, "Assisted builds require", "Assisted build: SCAN must pass first."),
                            ),
                            checks
                        )
                        validate(review)
                        assertEquals(
                            listOf("Assisted build: SCAN must pass first."),
                            readinessService.getPromotionLevelReadiness(this, gold)
                                .missing.filter { it.kind == ReadinessKind.CHECK }.map { it.message }
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Auto-promotion waits for the stamp required of assisted builds`() {
        asAdmin {
            project {
                branch {
                    val ci = validationStamp("CI")
                    val review = validationStamp("REVIEW")
                    val gold = promotionLevel("GOLD")
                    setProperty(
                        gold,
                        AutoPromotionPropertyType::class.java,
                        AutoPromotionProperty(listOf(ci), "", "", emptyList())
                    )
                    gold.requireOfAssistedBuilds("REVIEW")
                    build {
                        assisted()
                        // The auto-promotion conditions are met, but not the review
                        validate(ci)
                        assertTrue(!isPromoted(gold), "Not promoted before the review")
                        // Review passed
                        validate(review)
                        assertTrue(isPromoted(gold), "Promoted once reviewed")
                    }
                }
            }
        }
    }

    @Test
    fun `Setting and deleting the property through GraphQL`() {
        asAdmin {
            project {
                branch {
                    val gold = promotionLevel("GOLD")
                    run(
                        """
                            mutation {
                                setPromotionLevelAssistedBuildsRequirePropertyById(input: {
                                    id: ${gold.id},
                                    validationStamps: [" REVIEW ", "SCAN", "REVIEW", ""]
                                }) {
                                    errors { message }
                                }
                            }
                        """
                    ) { data ->
                        checkGraphQLUserErrors(data, "setPromotionLevelAssistedBuildsRequirePropertyById")
                    }
                    assertNotNull(getProperty(gold, AssistedBuildsRequirePropertyType::class.java)) {
                        assertEquals(listOf("REVIEW", "SCAN"), it.validationStamps)
                    }
                    run(
                        """
                            mutation {
                                deletePromotionLevelAssistedBuildsRequirePropertyById(input: {id: ${gold.id}}) {
                                    errors { message }
                                }
                            }
                        """
                    ) { data ->
                        checkGraphQLUserErrors(data, "deletePromotionLevelAssistedBuildsRequirePropertyById")
                    }
                    assertNull(getProperty(gold, AssistedBuildsRequirePropertyType::class.java))
                }
            }
        }
    }
}
