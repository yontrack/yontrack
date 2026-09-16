package net.nemerosa.ontrack.extension.general.validation

import net.nemerosa.ontrack.extension.general.autoValidationStampProperty
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.config
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.jvm.optionals.getOrNull
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The two server behaviours the nightly rescan of `.github/workflows/security-rescan.yml` is
 * written around (#1751). Both are easy to get wrong in a workflow and impossible to see from
 * one, so they are pinned here.
 *
 *  1. A number sent as a bare JSON integer is *not* the data of a
 *     [ThresholdNumberValidationDataType] run. The Yontrack CLI's generic
 *     `--data-type ... --data <n>` form transmits the integer faithfully, but `fromForm` reads
 *     the number out of a `value` field and returns null for anything else, so the run arrives
 *     with no data at all and is rejected for having no status. `{"value": n}` is the form that
 *     works. See yontrack/yontrack-cli#69.
 *
 *  2. Validating a build on a stamp its branch does not declare does not fail cleanly. The
 *     project property `AutoProjectCIConfigExtension` sets on every CI configuration creates the
 *     stamp on demand - but *without a data type* - and a run carrying data and no explicit
 *     status is then rejected. A release branch whose `.yontrack/ci.yaml` predates a stamp is
 *     exactly that case, which is why the rescan declares the stamps on the target branch with
 *     `setupValidationStamp` before reporting anything.
 */
class SecurityRescanValidationIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var thresholdNumberValidationDataType: ThresholdNumberValidationDataType

    /**
     * The configuration `.yontrack/ci.yaml` declares for `SECURITY.SECRETS`: one open alert is a
     * failure, and more is worse rather than better.
     */
    private val secretsConfig
        get() = thresholdNumberValidationDataType.config(
            ThresholdConfig(
                warningThreshold = null,
                failureThreshold = 0,
                okIfGreater = false,
            )
        )

    @Test
    fun `A count sent as a value field is the data of the run`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp(
                        name = SECRETS,
                        validationDataTypeConfig = secretsConfig,
                    )
                    build {
                        createValidationRun(mapOf("value" to 0))
                        val run = structureService
                            .getValidationRunsForBuildAndValidationStamp(this, vs, 0, 1)
                            .first()
                        assertEquals("PASSED", run.lastStatus.statusID.id, "No open alert passes")
                        assertEquals(0, run.data?.data, "The count is the data of the run")
                    }
                }
            }
        }
    }

    @Test
    fun `A count above the failure threshold fails the validation`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp(
                        name = SECRETS,
                        validationDataTypeConfig = secretsConfig,
                    )
                    build {
                        createValidationRun(mapOf("value" to 2))
                        val run = structureService
                            .getValidationRunsForBuildAndValidationStamp(this, vs, 0, 1)
                            .first()
                        assertEquals("FAILED", run.lastStatus.statusID.id, "One open alert is one too many")
                    }
                }
            }
        }
    }

    @Test
    fun `A count sent as a bare number is rejected, not stored`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp(
                        name = SECRETS,
                        validationDataTypeConfig = secretsConfig,
                    )
                    build {
                        // No `value` field, so no data - and with no data and no status there is
                        // nothing for the run to be. The mutation reports it as a user error
                        // rather than as a GraphQL one, which is still a non-zero exit for the
                        // CLI.
                        run(CREATE_VALIDATION_RUN, createValidationRunVariables(0)) { data ->
                            assertUserError(
                                data,
                                "createValidationRun",
                                message = "Validation Run Status is required because no data is provided.",
                            )
                        }
                        assertEquals(
                            emptyList(),
                            structureService.getValidationRunsForBuildAndValidationStamp(this, vs, 0, 10),
                            "Nothing was recorded",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `A stamp the branch does not declare is created without a data type, and rejects the data`() {
        asAdmin {
            project {
                // What AutoProjectCIConfigExtension sets on every project it configures.
                autoValidationStampProperty(this, autoCreate = true, autoCreateIfNotPredefined = true)
                branch {
                    build {
                        run(CREATE_VALIDATION_RUN, createValidationRunVariables(mapOf("value" to 0))) { data ->
                            assertUserError(
                                data,
                                "createValidationRun",
                                message = "Validation Run Status is required because the validation stamp has no data type.",
                            )
                        }
                    }
                    // Created all the same, and with nothing to compute a status from: this is
                    // why the rescan cannot simply validate and hope.
                    val vs = structureService
                        .findValidationStampByName(project.name, name, SECRETS)
                        .getOrNull()
                    assertNotNull(vs, "The stamp was created on demand")
                    assertNull(vs.dataType, "... but with no data type")
                }
            }
        }
    }

    @Test
    fun `Setting the stamp up on the branch first is what makes the rescan work`() {
        asAdmin {
            project {
                autoValidationStampProperty(this, autoCreate = true, autoCreateIfNotPredefined = true)
                branch {
                    // What the rescan does before reporting anything, so that a release branch
                    // whose CI configuration predates the stamp still gets the thresholds.
                    run(
                        SETUP_VALIDATION_STAMP,
                        mapOf(
                            "project" to project.name,
                            "branch" to name,
                            "validation" to SECRETS,
                            "dataType" to ThresholdNumberValidationDataType::class.java.name,
                            "dataTypeConfig" to mapOf(
                                "failureThreshold" to 0,
                                "okIfGreater" to false,
                            ),
                        )
                    ) { data ->
                        assertNoUserError(data, "setupValidationStamp")
                    }

                    val vs = structureService
                        .findValidationStampByName(project.name, name, SECRETS)
                        .getOrNull()
                    assertNotNull(vs, "The stamp was created")
                    assertEquals(
                        ThresholdNumberValidationDataType::class.java.name,
                        vs.dataType?.descriptor?.id,
                        "... with the data type the CI configuration declares",
                    )

                    build {
                        createValidationRun(mapOf("value" to 3))
                        val run = structureService
                            .getValidationRunsForBuildAndValidationStamp(this, vs, 0, 1)
                            .first()
                        assertEquals(
                            "FAILED",
                            run.lastStatus.statusID.id,
                            "The thresholds set up on the branch are the ones computing the status",
                        )
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------

    private fun Build.createValidationRunVariables(data: Any) = mapOf(
        "project" to project.name,
        "branch" to branch.name,
        "build" to name,
        "validation" to SECRETS,
        "dataTypeId" to ThresholdNumberValidationDataType::class.java.name,
        "data" to data,
    )

    private fun Build.createValidationRun(data: Any) {
        run(CREATE_VALIDATION_RUN, createValidationRunVariables(data)) { result ->
            assertNoUserError(result, "createValidationRun")
        }
    }

    companion object {

        private const val SECRETS = "SECURITY.SECRETS"

        /**
         * The mutation the CLI's generic `validate --data-type ... --data ...` form sends.
         */
        private val CREATE_VALIDATION_RUN = """
            mutation CreateValidationRun(
                ${'$'}project: String!,
                ${'$'}branch: String!,
                ${'$'}build: String!,
                ${'$'}validation: String!,
                ${'$'}dataTypeId: String,
                ${'$'}data: JSON
            ) {
                createValidationRun(input: {
                    project: ${'$'}project,
                    branch: ${'$'}branch,
                    build: ${'$'}build,
                    validationStamp: ${'$'}validation,
                    dataTypeId: ${'$'}dataTypeId,
                    data: ${'$'}data
                }) {
                    errors { message }
                }
            }
        """

        /**
         * The mutation `scripts/security-rescan.sh setup-stamp` sends.
         */
        private val SETUP_VALIDATION_STAMP = """
            mutation SetupValidationStamp(
                ${'$'}project: String!,
                ${'$'}branch: String!,
                ${'$'}validation: String!,
                ${'$'}dataType: String,
                ${'$'}dataTypeConfig: JSON
            ) {
                setupValidationStamp(input: {
                    project: ${'$'}project,
                    branch: ${'$'}branch,
                    validation: ${'$'}validation,
                    dataType: ${'$'}dataType,
                    dataTypeConfig: ${'$'}dataTypeConfig
                }) {
                    errors { message }
                }
            }
        """
    }
}
