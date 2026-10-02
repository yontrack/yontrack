package net.nemerosa.ontrack.service

import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventPostService
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
    private val validationDataTypeService: ValidationDataTypeService,
    private val eventPostService: EventPostService,
    private val eventFactory: EventFactory,
) : ValidationRunService {

    override fun updateValidationRunData(run: ValidationRun, data: ValidationRunData<*>?): ValidationRun {
        securityService.checkProjectFunction(run, ProjectEdit::class.java)
        val updated = validationRunRepository.updateValidationRunData(run, data)
        eventPostService.post(eventFactory.updateValidationRunData(updated))
        return updated
    }

    override fun isValidationRunPassed(build: Build, validationStamp: ValidationStamp): Boolean =
        // A status is passed when it is flagged as such (PASSED, but also FIXED).
        getLastValidationRunStatus(build, validationStamp)?.isPassed == true

    override fun isValidationRunPassedForAutoPromotion(build: Build, validationStamp: ValidationStamp): Boolean {
        val status = getLastValidationRunStatus(build, validationStamp) ?: return false
        return isPassedForAutoPromotion(validationStamp, status)
    }

    override fun isValidationRunPassedForAutoPromotion(validationRun: ValidationRun): Boolean =
        isPassedForAutoPromotion(validationRun.validationStamp, validationRun.lastStatus.statusID)

    /**
     * Status of the last run, if any. An unknown status is returned as `null` - it must not fail the caller.
     */
    private fun getLastValidationRunStatus(build: Build, validationStamp: ValidationStamp): ValidationRunStatusID? {
        val statusId = validationRunRepository.getLastValidationRunStatusId(build, validationStamp)
            ?: return null
        return validationRunStatusService.getValidationRunStatusList()
            .find { it.id == statusId }
    }

    /**
     * Delegates to the data type of the stamp, which reads its current configuration. A stamp without any
     * data type, or whose data type is no longer available, falls back to the passed flag of the status.
     */
    private fun isPassedForAutoPromotion(validationStamp: ValidationStamp, status: ValidationRunStatusID): Boolean {
        val dataTypeConfig = validationStamp.dataType ?: return status.isPassed
        val dataType = validationDataTypeService.getValidationDataType<Any?, Any>(dataTypeConfig.descriptor.id)
            ?: return status.isPassed
        return dataType.isPassedForAutoPromotion(dataTypeConfig.config, status)
    }

}