package net.nemerosa.ontrack.service

import net.nemerosa.ontrack.model.exceptions.ValidationRunStatusChangeForbiddenException
import net.nemerosa.ontrack.model.exceptions.ValidationRunStatusNotFoundException
import net.nemerosa.ontrack.model.exceptions.ValidationRunStatusUnknownDependencyException
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import net.nemerosa.ontrack.model.structure.ValidationRunStatusService
import net.nemerosa.ontrack.model.support.StartupService
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

@Service
class ValidationRunStatusServiceImpl : ValidationRunStatusService, StartupService {

    private val logger = LoggerFactory.getLogger(ValidationRunStatusService::class.java)

    private val statuses: MutableMap<String, ValidationRunStatusID> = LinkedHashMap()

    override fun getValidationRunStatusList(): Collection<ValidationRunStatusID> = statuses.values

    override fun getValidationRunStatus(id: String): ValidationRunStatusID =
            Optional.ofNullable(statuses[id]).orElseThrow { ValidationRunStatusNotFoundException(id) }

    override fun getNextValidationRunStatusList(id: String): List<ValidationRunStatusID> =
            getValidationRunStatus(id)
                    .followingStatuses
                    .map { getValidationRunStatus(it) }

    override fun checkTransition(from: ValidationRunStatusID, to: ValidationRunStatusID) {
        if (!from.followingStatuses.contains(to.id)) {
            throw ValidationRunStatusChangeForbiddenException(from.id, to.id)
        }
    }

    override fun getName(): String = "Loading of validation run statuses"

    override fun startupOrder(): Int = StartupService.SYSTEM_REGISTRATION

    /**
     * Registers the tree of validation run status ids.
     */
    override fun start() {
        register(ValidationRunStatusID.STATUS_PASSED)
        register(ValidationRunStatusID.STATUS_FIXED)
        register(ValidationRunStatusID.STATUS_DEFECTIVE)
        register(ValidationRunStatusID.STATUS_EXPLAINED, ValidationRunStatusID.FIXED)
        register(ValidationRunStatusID.STATUS_INVESTIGATING, ValidationRunStatusID.DEFECTIVE, ValidationRunStatusID.EXPLAINED, ValidationRunStatusID.FIXED)
        register(ValidationRunStatusID.STATUS_INTERRUPTED, ValidationRunStatusID.INVESTIGATING, ValidationRunStatusID.FIXED)
        register(ValidationRunStatusID.STATUS_FAILED, ValidationRunStatusID.INTERRUPTED, ValidationRunStatusID.INVESTIGATING, ValidationRunStatusID.EXPLAINED, ValidationRunStatusID.DEFECTIVE, ValidationRunStatusID.FIXED)
        register(ValidationRunStatusID.STATUS_WARNING, ValidationRunStatusID.INTERRUPTED, ValidationRunStatusID.INVESTIGATING, ValidationRunStatusID.EXPLAINED, ValidationRunStatusID.DEFECTIVE, ValidationRunStatusID.FIXED)
        // Checks the tree
        for (statusID in statuses.values) {
            for (nextStatus in statusID.followingStatuses) {
                if (!statuses.containsKey(nextStatus)) {
                    throw ValidationRunStatusUnknownDependencyException(statusID.id, nextStatus)
                }
            }
        }
        // Logging
        for (statusID in statuses.values) {
            logger.info(
                    "[status] {} -> {}",
                    statusID.id,
                    StringUtils.join(statusID.followingStatuses, ",")
            )
        }
    }

    private fun register(statusID: ValidationRunStatusID, vararg next: String) {
        statuses[statusID.id] = statusID.addDependencies(*next)
    }
}