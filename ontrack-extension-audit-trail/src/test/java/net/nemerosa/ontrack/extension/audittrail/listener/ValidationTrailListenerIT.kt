package net.nemerosa.ontrack.extension.audittrail.listener

import net.nemerosa.ontrack.extension.api.support.TestValidationData
import net.nemerosa.ontrack.extension.api.support.TestValidationDataType
import net.nemerosa.ontrack.extension.audittrail.model.TrailEntryTypes
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.Signature
import net.nemerosa.ontrack.model.structure.ValidationRunData
import net.nemerosa.ontrack.model.structure.config
import net.nemerosa.ontrack.model.structure.ValidationRunRequest
import net.nemerosa.ontrack.model.structure.ValidationRunService
import net.nemerosa.ontrack.model.structure.ValidationRunStatus
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime

/**
 * Entries of the validations of a build.
 */
class ValidationTrailListenerIT : AbstractTrailListenerITSupport() {

    @Autowired
    private lateinit var testValidationDataType: TestValidationDataType

    @Autowired
    private lateinit var validationRunService: ValidationRunService

    @Test
    fun `Validating a build writes validation run with the SHA-256 of its data, and its signature claimed`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp("security-scan", testValidationDataType.config(null))
                    build {
                        val claimedTime = LocalDateTime.of(2026, 9, 30, 10, 0, 0, 5_000_000)
                        val (run, actor) = asCi {
                            structureService.newValidationRun(
                                this,
                                ValidationRunRequest(
                                    validationStampName = vs.name,
                                    dataTypeId = testValidationDataType.descriptor.id,
                                    data = TestValidationData(critical = 1, high = 2, medium = 3),
                                    signature = Signature.of(claimedTime, "scanner"),
                                )
                            )
                        }
                        trailAfter(1).assertSingleEntry(
                            TrailEntryTypes.VALIDATION_RUN,
                            mapOf(
                                "validationStamp" to mapOf("id" to vs.id(), "name" to "security-scan"),
                                "validationRun" to mapOf("id" to run.id(), "order" to run.runOrder),
                                "status" to "FAILED",
                                "data" to mapOf(
                                    "type" to "net.nemerosa.ontrack.extension.api.support.TestValidationDataType",
                                    // shasum -a 256 of {"critical":1,"high":2,"medium":3}
                                    "sha256" to "dc35426725e92f989e065f705b5a43eabc06a6df95e8b0aaff6d0b66957d93a9",
                                ),
                                "claimed" to claimed(claimedTime, "scanner"),
                            ),
                            actor,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Changing the status of a validation run writes validation status`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp("unit-tests")
                    build {
                        val run = validate(vs, ValidationRunStatusID.STATUS_FAILED)
                        val seq = lastSeq()
                        val claimedTime = LocalDateTime.of(2026, 9, 30, 11, 30, 0, 0)
                        val (_, actor) = asCi {
                            structureService.newValidationRunStatus(
                                run,
                                ValidationRunStatus(
                                    ID.NONE,
                                    Signature.of(claimedTime, "reviewer"),
                                    ValidationRunStatusID.STATUS_DEFECTIVE,
                                    "Known issue",
                                )
                            )
                        }
                        trailAfter(seq).assertSingleEntry(
                            TrailEntryTypes.VALIDATION_STATUS,
                            mapOf(
                                "validationStamp" to mapOf("id" to vs.id(), "name" to "unit-tests"),
                                "validationRun" to mapOf("id" to run.id(), "order" to run.runOrder),
                                "status" to "DEFECTIVE",
                                "description" to "Known issue",
                                "claimed" to claimed(claimedTime, "reviewer"),
                            ),
                            actor,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Editing the comment of a validation run status writes validation comment`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp("unit-tests")
                    build {
                        val run = validate(vs, ValidationRunStatusID.STATUS_FAILED, description = "Flaky")
                        val seq = lastSeq()
                        val statusId = run.lastStatus.id
                        val (_, actor) = asCi {
                            structureService.saveValidationRunStatusComment(run, statusId, "Not flaky, broken")
                        }
                        trailAfter(seq).assertSingleEntry(
                            TrailEntryTypes.VALIDATION_COMMENT,
                            mapOf(
                                "validationStamp" to mapOf("id" to vs.id(), "name" to "unit-tests"),
                                "validationRun" to mapOf("id" to run.id(), "order" to run.runOrder),
                                "validationRunStatusId" to statusId.get(),
                                "comment" to "Not flaky, broken",
                            ),
                            actor,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Replacing the data of a validation run writes validation data with its new hash, and without it when removed`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp("security-scan", testValidationDataType.config(null))
                    build {
                        val run = validateWithData(
                            validationStamp = vs,
                            validationDataTypeId = testValidationDataType.descriptor.id,
                            validationRunData = TestValidationData(critical = 1, high = 2, medium = 3),
                        )
                        val seq = lastSeq()
                        val (_, actor) = asCi {
                            validationRunService.updateValidationRunData(
                                run,
                                ValidationRunData(testValidationDataType.descriptor, TestValidationData(medium = 5))
                            )
                            validationRunService.updateValidationRunData(run, null)
                        }
                        val ref = mapOf(
                            "validationStamp" to mapOf("id" to vs.id(), "name" to "security-scan"),
                            "validationRun" to mapOf("id" to run.id(), "order" to run.runOrder),
                        )
                        val (replaced, removed) = trailAfter(seq)
                        replaced.assertEntry(
                            TrailEntryTypes.VALIDATION_DATA,
                            ref + mapOf(
                                "data" to mapOf(
                                    "type" to "net.nemerosa.ontrack.extension.api.support.TestValidationDataType",
                                    // shasum -a 256 of {"critical":0,"high":0,"medium":5}
                                    "sha256" to "e2f0bdc4deb80e579164b3a1f61c1bb8f8c816f2df0371dc675493be82e88fd4",
                                ),
                            ),
                            actor,
                        )
                        removed.assertEntry(TrailEntryTypes.VALIDATION_DATA, ref, actor)
                    }
                }
            }
        }
    }

    @Test
    fun `Deleting a validation run writes validation deleted with its last status`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp("unit-tests")
                    build {
                        val run = validate(vs, ValidationRunStatusID.STATUS_PASSED)
                        val seq = lastSeq()
                        val (_, actor) = asCi {
                            structureService.deleteValidationRun(run)
                        }
                        trailAfter(seq).assertSingleEntry(
                            TrailEntryTypes.VALIDATION_DELETED,
                            mapOf(
                                "validationStamp" to mapOf("id" to vs.id(), "name" to "unit-tests"),
                                "validationRun" to mapOf("id" to run.id(), "order" to run.runOrder),
                                "status" to "PASSED",
                            ),
                            actor,
                        )
                    }
                }
            }
        }
    }
}
