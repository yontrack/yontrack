package net.nemerosa.ontrack.service

import net.nemerosa.ontrack.model.security.ProjectEdit
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.repository.ValidationRunRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class ValidationRunServiceImpl(
    private val securityService: SecurityService,
    private val validationRunRepository: ValidationRunRepository,
    private val validationRunStatusService: ValidationRunStatusService,
) : ValidationRunService {

    override fun updateValidationRunData(run: ValidationRun, data: ValidationRunData<*>?): ValidationRun {
        securityService.checkProjectFunction(run, ProjectEdit::class.java)
        return validationRunRepository.updateValidationRunData(run, data)
    }

    override fun isValidationRunPassed(build: Build, validationStamp: ValidationStamp): Boolean {
        // Gets the status of the last run, if any
        val statusId = validationRunRepository.getLastValidationRunStatusId(build, validationStamp)
            ?: return false
        // A status is passed when it is flagged as such (PASSED, but also FIXED).
        // An unknown status is never passed - it must not fail the caller.
        return validationRunStatusService.getValidationRunStatusList()
            .find { it.id == statusId }
            ?.isPassed == true
    }

}