package net.nemerosa.ontrack.kdsl.acceptance.tests.agents

import net.nemerosa.ontrack.kdsl.acceptance.tests.AbstractACCDSLTestSupport
import net.nemerosa.ontrack.kdsl.connector.graphql.GraphQLClientException
import net.nemerosa.ontrack.kdsl.spec.ReadinessItem
import net.nemerosa.ontrack.kdsl.spec.ReadinessKind
import net.nemerosa.ontrack.kdsl.spec.extension.agents.FEATURE_AGENTS
import net.nemerosa.ontrack.kdsl.spec.extension.agents.assistedBuildsRequire
import net.nemerosa.ontrack.kdsl.spec.extension.general.AutoPromotionProperty
import net.nemerosa.ontrack.kdsl.spec.extension.general.autoPromotion
import net.nemerosa.ontrack.kdsl.spec.extension.license.devLicense
import net.nemerosa.ontrack.kdsl.spec.extension.license.isLicensedFeatureEnabled
import net.nemerosa.ontrack.kdsl.spec.extension.scm.AssistedChange
import net.nemerosa.ontrack.kdsl.spec.extension.scm.AssistedChangeBasis
import net.nemerosa.ontrack.kdsl.spec.extension.scm.assistedChange
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * *Assisted builds require* on a promotion level (#2029), with the licence enabling the agent
 * governance: an assisted build must pass the listed stamps before being promoted.
 */
class ACCAssistedBuildsRequire : AbstractACCDSLTestSupport() {

    private val assisted = AssistedChange(
        basis = AssistedChangeBasis.SET_BY_CI,
        assistants = listOf("Claude Code"),
        assistedCommits = 1,
        totalCommits = 1,
    )

    @Test
    fun `An assisted build must pass the review before being promoted, and readiness says so`() {
        assertTrue(ontrack.isLicensedFeatureEnabled(FEATURE_AGENTS), "The licence enables the agent governance")
        project {
            branch {
                val review = validationStamp(name = "REVIEW")
                val gold = promotion(name = "GOLD")
                gold.assistedBuildsRequire = listOf("REVIEW")
                assertEquals(listOf("REVIEW"), gold.assistedBuildsRequire)
                build {
                    assistedChange = assisted
                    // Readiness lists the missing review
                    assertEquals(
                        listOf(
                            ReadinessItem(
                                ReadinessKind.CHECK,
                                "Assisted builds require",
                                "Assisted build: REVIEW must pass first."
                            )
                        ),
                        readiness(promotionLevel = gold.name).missing.filter { it.kind == ReadinessKind.CHECK }
                    )
                    // Refused
                    val ex = assertFailsWith<GraphQLClientException> {
                        promote(gold.name)
                    }
                    assertTrue(
                        ex.message?.contains("Assisted build: REVIEW must pass first.") == true,
                        "Refused with the reason: ${ex.message}"
                    )
                    // Reviewed, then promoted
                    validate(review.name, status = "PASSED")
                    promote(gold.name)
                    assertTrue(readiness(promotionLevel = gold.name).ready, "Promoted")
                }
                // A build which is not assisted needs no review
                build {
                    assistedChange = AssistedChange(basis = AssistedChangeBasis.SET_BY_CI, totalCommits = 1)
                    promote(gold.name)
                }
            }
        }
    }

    @Test
    fun `Auto-promotion waits for the review of an assisted build`() {
        project {
            branch {
                val ci = validationStamp(name = "CI")
                val review = validationStamp(name = "REVIEW")
                val gold = promotion(name = "GOLD")
                gold.autoPromotion = AutoPromotionProperty(validationStamps = listOf(ci.id))
                gold.assistedBuildsRequire = listOf("REVIEW")
                build {
                    assistedChange = assisted
                    validate(ci.name, status = "PASSED")
                    assertTrue(!readiness(promotionLevel = gold.name).ready, "Waiting for the review")
                    validate(review.name, status = "PASSED")
                    assertTrue(readiness(promotionLevel = gold.name).ready, "Promoted once reviewed")
                }
            }
        }
    }

    @Test
    fun `Without the licence, the property does nothing`() {
        project {
            branch {
                validationStamp(name = "REVIEW")
                val gold = promotion(name = "GOLD")
                gold.assistedBuildsRequire = listOf("REVIEW")
                build {
                    assistedChange = assisted
                    ontrack.devLicense.withoutFeature(FEATURE_AGENTS) {
                        promote(gold.name)
                    }
                    assertTrue(readiness(promotionLevel = gold.name).ready, "Promoted without the licence")
                }
            }
        }
    }
}
