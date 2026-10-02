package net.nemerosa.ontrack.extension.audittrail.listener

import net.nemerosa.ontrack.extension.audittrail.model.TrailEntryTypes
import net.nemerosa.ontrack.extension.general.AutoPromotionProperty
import net.nemerosa.ontrack.extension.general.AutoPromotionPropertyType
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.ValidationRunRequest
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How the entries are written from the events, whatever their type.
 */
class AuditTrailEventListenerIT : AbstractTrailListenerITSupport() {

    @Test
    fun `A multi-step GraphQL mutation writes one entry per step`() {
        asAdmin {
            project {
                branch {
                    val (data, actor) = asCi {
                        run(
                            """
                                mutation {
                                    createBuild(input: {
                                        projectName: "${project.name}",
                                        branchName: "$name",
                                        name: "1.0.0",
                                        runInfo: {
                                            sourceType: "github",
                                            runTime: 30
                                        }
                                    }) {
                                        build { id }
                                        errors { message }
                                    }
                                }
                            """
                        )
                    }
                    val build = structureService.getBuild(
                        ID.of(data.path("createBuild").path("build").path("id").asInt())
                    )
                    val (created, runInfo) = build.trail().apply { assertEquals(2, size) }
                    created.assertEntry(
                        TrailEntryTypes.BUILD_CREATED,
                        mapOf(
                            "build" to build.ref(),
                            "claimed" to claimed(build.signature.time, build.signature.user.name),
                        ),
                        actor,
                    )
                    runInfo.assertEntry(
                        TrailEntryTypes.RUN_INFO_SET,
                        mapOf(
                            "runnable" to mapOf("type" to "build"),
                            "runInfo" to mapOf("sourceType" to "github", "runTime" to 30),
                        ),
                        actor,
                    )
                }
            }
        }
    }

    @Test
    fun `Nothing is written while the licence is off`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    val pl = promotionLevel()
                    val build = withoutTrail {
                        build {
                            validate(vs, ValidationRunStatusID.STATUS_PASSED)
                            promote(pl)
                            structureService.saveBuild(withDescription("Changed"))
                        }
                    }
                    assertTrue(build.trail().isEmpty(), "Nothing written without the licence")
                }
            }
        }
    }

    @Test
    fun `The first change recorded on a build which predates its trail opens a partial trail`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    val build: Build = withoutTrail { build() }
                    val (_, actor) = asCi {
                        structureService.newValidationRun(
                            build,
                            ValidationRunRequest(
                                validationStampName = vs.name,
                                validationRunStatusId = ValidationRunStatusID.STATUS_PASSED,
                            )
                        )
                    }
                    val (opened, validated) = build.trail().apply { assertEquals(2, size) }
                    opened.assertEntry(
                        TrailEntryTypes.TRAIL_OPENED,
                        mapOf(
                            "build" to build.ref(),
                            "buildCreatedAt" to claimed(build.signature.time, "")["time"],
                            "partial" to true,
                        ),
                        actor,
                    )
                    assertEquals(TrailEntryTypes.VALIDATION_RUN, validated.type)
                }
            }
        }
    }

    @Test
    fun `A change is recorded before what it sets off, which the system records on behalf of the actor`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp("unit-tests")
                    val pl = promotionLevel("BRONZE")
                    propertyService.editProperty(
                        pl,
                        AutoPromotionPropertyType::class.java,
                        AutoPromotionProperty(
                            validationStamps = listOf(vs),
                            include = "",
                            exclude = "",
                            promotionLevels = emptyList(),
                        )
                    )
                    build {
                        val (_, actor) = asCi {
                            structureService.newValidationRun(
                                this,
                                ValidationRunRequest(
                                    validationStampName = vs.name,
                                    validationRunStatusId = ValidationRunStatusID.STATUS_PASSED,
                                )
                            )
                        }
                        val (validated, promoted) = trailAfter(1).apply { assertEquals(2, size) }
                        assertEquals(TrailEntryTypes.VALIDATION_RUN, validated.type)
                        assertEquals(actor, validated.actor)
                        assertEquals(TrailEntryTypes.PROMOTION_ADDED, promoted.type)
                        assertEquals(
                            mapOf(
                                "account" to "system",
                                "via" to "system",
                                "system" to "auto-promotion",
                                "onBehalfOf" to actor,
                            ).asJson(),
                            promoted.actor,
                            "Auto-promotion by the system, on behalf of the CI"
                        )
                    }
                }
            }
        }
    }
}
