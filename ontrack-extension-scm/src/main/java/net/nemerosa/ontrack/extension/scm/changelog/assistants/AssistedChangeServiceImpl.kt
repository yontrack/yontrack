package net.nemerosa.ontrack.extension.scm.changelog.assistants

import kotlinx.coroutines.runBlocking
import net.nemerosa.ontrack.extension.scm.changelog.SCMChangeLogEnabled
import net.nemerosa.ontrack.extension.scm.changelog.SCMChangeLogService
import net.nemerosa.ontrack.extension.scm.service.SCMBuildCommitPropertyType
import net.nemerosa.ontrack.extension.scm.service.SCMDetector
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.PropertyService
import net.nemerosa.ontrack.model.structure.StructureService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AssistedChangeServiceImpl(
    private val propertyService: PropertyService,
    private val structureService: StructureService,
    private val scmDetector: SCMDetector,
    private val scmChangeLogService: SCMChangeLogService,
    private val scmCommitAssistantService: SCMCommitAssistantService,
) : AssistedChangeService {

    private val logger: Logger = LoggerFactory.getLogger(AssistedChangeServiceImpl::class.java)

    override fun getAssistedChange(build: Build): AssistedChangeProperty? =
        propertyService.getPropertyValue(build, AssistedChangePropertyType::class.java)

    override fun computeAssistedChange(build: Build): AssistedChangeProperty? {
        val current = getAssistedChange(build)
        // CI wins
        if (current?.basis == AssistedChangeBasis.SET_BY_CI) {
            return current
        }
        // Computation
        val value = compute(build) ?: return current
        // The CI may have set the value meanwhile
        val latest = getAssistedChange(build)
        if (latest?.basis == AssistedChangeBasis.SET_BY_CI) {
            return latest
        }
        // Writing the value only if it changes
        if (value != latest) {
            propertyService.editProperty(build, AssistedChangePropertyType::class.java, value)
        }
        return value
    }

    /**
     * Computes the value for a build, `null` if the build has no commit yet.
     */
    private fun compute(build: Build): AssistedChangeProperty? {
        val scm = scmDetector.getSCM(build.project) as? SCMChangeLogEnabled
        if (scm == null) {
            // The build may have a commit which no SCM can read
            return if (hasCommitProperty(build)) {
                AssistedChangeProperty.unknown(AssistedChangeProperty.REASON_NO_SCM)
            } else {
                null
            }
        }
        // No commit yet: its property comes later
        if (scm.getBuildCommit(build).isNullOrBlank()) {
            return null
        }
        // Previous build with a commit
        val previous = findPreviousBuildWithCommit(build, scm)
            ?: return AssistedChangeProperty.unknown(AssistedChangeProperty.REASON_NO_PREVIOUS_BUILD)
        // Change log
        return try {
            val changeLog = runBlocking {
                scmChangeLogService.getChangeLog(previous, build)
            } ?: return AssistedChangeProperty.unknown(AssistedChangeProperty.REASON_NO_SCM, previous.id())
            val commits = changeLog.commits.map { it.commit }
            val assistants = commits.map { scmCommitAssistantService.getAssistants(it) }
            AssistedChangeProperty.validated(
                AssistedChangeProperty(
                    basis = AssistedChangeBasis.COMPUTED,
                    unknownReason = null,
                    assistants = assistants.flatten().map { it.name },
                    assistedCommits = assistants.count { it.isNotEmpty() },
                    totalCommits = commits.size,
                    sessionLinks = assistants.flatten().mapNotNull { it.sessionLink },
                    previousBuildId = previous.id(),
                )
            )
        } catch (any: Exception) {
            logger.warn("Cannot compute the assisted change of build ${build.entityDisplayName}", any)
            AssistedChangeProperty.unknown(
                reason = AssistedChangeProperty.REASON_SCM_ERROR_PREFIX + (any.message ?: any::class.java.name),
                previousBuildId = previous.id(),
            )
        }
    }

    private fun hasCommitProperty(build: Build): Boolean =
        propertyService.getProperties(build).any { property ->
            property.type is SCMBuildCommitPropertyType && property.value != null
        }

    private fun findPreviousBuildWithCommit(build: Build, scm: SCMChangeLogEnabled): Build? {
        var previous = structureService.getPreviousBuild(build.id)
        while (previous != null && scm.getBuildCommit(previous).isNullOrBlank()) {
            previous = structureService.getPreviousBuild(previous.id)
        }
        return previous
    }
}
