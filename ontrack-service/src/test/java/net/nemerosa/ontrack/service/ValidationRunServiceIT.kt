package net.nemerosa.ontrack.service

import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.model.structure.ValidationRunService
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidationRunServiceIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var validationRunService: ValidationRunService

    @Test
    fun `Checking for a passed build when no validation run`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    build {
                        // No validation at all
                        assertFalse(validationRunService.isValidationRunPassed(this, vs))
                    }
                }
            }
        }
    }

    @Test
    fun `Checking for a passed build when only one passed validation`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    build {
                        validate(vs, validationRunStatusID = ValidationRunStatusID.STATUS_PASSED)
                        assertTrue(validationRunService.isValidationRunPassed(this, vs))
                    }
                }
            }
        }
    }

    @Test
    fun `Checking for a passed build when only one failed validation`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    build {
                        validate(vs, validationRunStatusID = ValidationRunStatusID.STATUS_FAILED)
                        assertFalse(validationRunService.isValidationRunPassed(this, vs))
                    }
                }
            }
        }
    }

    @Test
    fun `Checking for a passed build when last validation is passed`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    build {
                        validate(vs, validationRunStatusID = ValidationRunStatusID.STATUS_FAILED)
                        validate(vs, validationRunStatusID = ValidationRunStatusID.STATUS_PASSED)
                        assertTrue(validationRunService.isValidationRunPassed(this, vs))
                    }
                }
            }
        }
    }

    @Test
    fun `Checking for a passed build when last validation is failed`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    build {
                        validate(vs, validationRunStatusID = ValidationRunStatusID.STATUS_PASSED)
                        validate(vs, validationRunStatusID = ValidationRunStatusID.STATUS_FAILED)
                        assertFalse(validationRunService.isValidationRunPassed(this, vs))
                    }
                }
            }
        }
    }

    /**
     * #1629 - a validation run whose status has been changed to FIXED must be considered as passed.
     */
    @Test
    fun `Checking for a passed build when the last validation has been fixed`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    build {
                        val run = validate(vs, validationRunStatusID = ValidationRunStatusID.STATUS_FAILED)
                        assertFalse(validationRunService.isValidationRunPassed(this, vs))
                        val investigated =
                            run.validationStatus(ValidationRunStatusID.STATUS_INVESTIGATING, "Investigating")
                        assertFalse(validationRunService.isValidationRunPassed(this, vs))
                        investigated.validationStatus(ValidationRunStatusID.STATUS_FIXED, "Fixed")
                        assertTrue(validationRunService.isValidationRunPassed(this, vs))
                    }
                }
            }
        }
    }

    /**
     * #1629 - a status which is not flagged as passed must not be considered as passed.
     */
    @Test
    fun `Checking for a passed build when the last validation is defective`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    build {
                        validate(vs, validationRunStatusID = ValidationRunStatusID.STATUS_FAILED)
                            .validationStatus(ValidationRunStatusID.STATUS_DEFECTIVE, "Defective")
                        assertFalse(validationRunService.isValidationRunPassed(this, vs))
                    }
                }
            }
        }
    }

}
