package net.nemerosa.ontrack.extension.scorecard.estates

import net.nemerosa.ontrack.extension.scorecard.engine.ReadingComputer
import net.nemerosa.ontrack.extension.scorecard.job.ScorecardJobs
import net.nemerosa.ontrack.extension.scorecard.license.ScorecardLicense
import net.nemerosa.ontrack.extension.scorecard.security.EstateManagement
import net.nemerosa.ontrack.extension.scorecard.storage.EstateRepository
import net.nemerosa.ontrack.job.JobScheduler
import net.nemerosa.ontrack.job.Schedule
import net.nemerosa.ontrack.model.labels.LabelManagementService
import net.nemerosa.ontrack.model.labels.findLabelByDisplay
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.CompletableFuture

@Service
@Transactional
class EstateServiceImpl(
    private val scorecardLicense: ScorecardLicense,
    private val securityService: SecurityService,
    private val estateRepository: EstateRepository,
    private val labelManagementService: LabelManagementService,
    private val structureService: StructureService,
    private val scorecardJobs: ScorecardJobs,
    private val jobScheduler: JobScheduler,
    computers: List<ReadingComputer>,
) : EstateService {

    private val readingKeys: Set<String> = computers.map { it.key }.toSet()

    @Transactional(readOnly = true)
    override fun findAll(): List<Estate> {
        scorecardLicense.checkEstates()
        return estateRepository.findAll()
    }

    @Transactional(readOnly = true)
    override fun findByName(name: String): Estate? {
        scorecardLicense.checkEstates()
        return estateRepository.findByName(name)
    }

    @Transactional(readOnly = true)
    override fun getById(id: Int): Estate {
        scorecardLicense.checkEstates()
        return estateRepository.findById(id) ?: throw EstateNotFoundException(id)
    }

    override fun create(input: EstateInput): Estate {
        checkManagement()
        val valid = validate(input)
        if (estateRepository.findByName(valid.name) != null) {
            throw EstateNameAlreadyExistsException(valid.name)
        }
        val id = estateRepository.create(
            name = valid.name,
            description = valid.description,
            labelIds = valid.labelIds,
            marker = valid.marker,
            readingConfigs = valid.readingConfigs,
            security = valid.security,
        )
        return estateRepository.findById(id) ?: throw EstateNotFoundException(id)
    }

    override fun update(id: Int, input: EstateInput): Estate {
        checkManagement()
        estateRepository.findById(id) ?: throw EstateNotFoundException(id)
        val valid = validate(input)
        val existing = estateRepository.findByName(valid.name)
        if (existing != null && existing.id != id) {
            throw EstateNameAlreadyExistsException(valid.name)
        }
        estateRepository.update(
            id = id,
            name = valid.name,
            description = valid.description,
            labelIds = valid.labelIds,
            marker = valid.marker,
            readingConfigs = valid.readingConfigs,
            security = valid.security,
        )
        return estateRepository.findById(id) ?: throw EstateNotFoundException(id)
    }

    override fun delete(id: Int) {
        checkManagement()
        estateRepository.findById(id) ?: throw EstateNotFoundException(id)
        estateRepository.delete(id)
    }

    @Transactional(readOnly = true)
    override fun getProjects(estate: Estate): List<Project> {
        scorecardLicense.checkEstates()
        return estateRepository.findProjectIds(estate.id).mapNotNull { projectId ->
            structureService.findProjectByID(ID.of(projectId))
        }
    }

    override fun recompute(estate: Estate): CompletableFuture<*>? {
        checkManagement()
        val job = scorecardJobs.estateRecomputeJob(estate)
        jobScheduler.schedule(job, Schedule.NONE)
        return jobScheduler.fireImmediately(job.key).orElse(null)
    }

    private fun checkManagement() {
        scorecardLicense.checkEstates()
        securityService.checkGlobalFunction(EstateManagement::class.java)
    }

    private class ValidEstate(
        val name: String,
        val description: String?,
        val labelIds: List<Int>,
        val marker: EstateMarker?,
        val readingConfigs: List<EstateReadingConfig>,
        val security: EstateSecurity,
    )

    private fun validate(input: EstateInput): ValidEstate {
        val name = input.name.trim()
        if (name.isBlank()) {
            throw EstateInputException("The name of an estate is required.")
        }
        if (name.length > MAX_NAME_LENGTH) {
            throw EstateInputException("The name of an estate is limited to $MAX_NAME_LENGTH characters.")
        }
        val description = input.description?.trim()?.takeIf { it.isNotBlank() }
        if (description != null && description.length > MAX_DESCRIPTION_LENGTH) {
            throw EstateInputException("The description of an estate is limited to $MAX_DESCRIPTION_LENGTH characters.")
        }
        // Labels
        val labels = input.labels.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (labels.isEmpty()) {
            throw EstateInputException("An estate needs one label at least, to select its projects.")
        }
        val labelIds = labels.map { display ->
            labelManagementService.findLabelByDisplay(display)?.id
                ?: throw EstateInputException("Label $display does not exist.")
        }
        // Marker
        val marker = when (val marker = input.marker) {
            null -> null
            is EstatePromotionMarker -> {
                val levelName = marker.levelName.trim()
                if (levelName.isBlank()) {
                    throw EstateInputException("A promotion marker needs the name of a promotion level.")
                }
                EstatePromotionMarker(levelName)
            }

            is EstateEnvironmentMarker -> {
                val environment = marker.environment.trim()
                if (environment.isBlank()) {
                    throw EstateInputException("An environment marker needs the name of an environment.")
                }
                EstateEnvironmentMarker(environment, marker.qualifier.trim())
            }
        }
        // Readings
        input.readingConfigs.groupBy { it.key }.forEach { (key, configs) ->
            if (key !in readingKeys) {
                throw EstateInputException("Reading $key does not exist.")
            }
            if (configs.size > 1) {
                throw EstateInputException("Reading $key is configured more than once.")
            }
        }
        val readingConfigs = input.readingConfigs.mapNotNull { config ->
            if (config.windowDays != null && config.windowDays <= 0) {
                throw EstateInputException("The window of reading ${config.key} must be one day at least.")
            }
            if (config.target != null && !config.target.isFinite()) {
                throw EstateInputException("The target of reading ${config.key} must be a number.")
            }
            config.takeIf { it.windowDays != null || it.target != null }
        }
        // Security
        val security = input.security
        if (security.freshnessDays != null && security.freshnessDays <= 0) {
            throw EstateInputException("The freshness of the security scans must be one day at least.")
        }
        if (security.criticalTargetDays != null && security.criticalTargetDays < 0) {
            throw EstateInputException("The remediation target of the CRITICAL findings must be zero days or more.")
        }
        if (security.highTargetDays != null && security.highTargetDays < 0) {
            throw EstateInputException("The remediation target of the HIGH findings must be zero days or more.")
        }
        return ValidEstate(
            name = name,
            description = description,
            labelIds = labelIds,
            marker = marker,
            readingConfigs = readingConfigs,
            security = security.copy(expectedKinds = security.expectedKinds.distinct().sorted()),
        )
    }

    companion object {
        private const val MAX_NAME_LENGTH = 100
        private const val MAX_DESCRIPTION_LENGTH = 500
    }
}
