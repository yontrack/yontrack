package net.nemerosa.ontrack.extension.sonarqube.measures

import net.nemerosa.ontrack.extension.sonarqube.SonarQubeExtensionFeature
import net.nemerosa.ontrack.extension.sonarqube.property.SonarQubePropertyType
import net.nemerosa.ontrack.job.*
import net.nemerosa.ontrack.job.orchestrator.JobOrchestratorSupplier
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.PropertyService
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Component
import java.util.stream.Stream

/**
 * Creates a job for each project configured with SonarQube, so that SonarQube metrics are collected for each build.
 */
@Component
class SonarQubeMeasuresJobSupplier(
        private val securityService: SecurityService,
        private val structureService: StructureService,
        private val propertyService: PropertyService,
        private val sonarQubeMeasuresCollectionService: SonarQubeMeasuresCollectionService,
        private val cachedSettingsService: CachedSettingsService
) : JobOrchestratorSupplier {

    companion object {
        private val SONARQUBE_COLLECTION_JOB = SonarQubeExtensionFeature.SONARQUBE_JOB_CATEGORY.getType("sonarqube-collection").withName("Collection of SonarQube measures")
    }

    override val jobRegistrations: Collection<JobRegistration>
        get() = structureService.projectList
                .filter { propertyService.hasProperty(it, SonarQubePropertyType::class.java) }
                .map { createSonarQubeMeasuresJob(it) }

    private fun createSonarQubeMeasuresJob(project: Project): JobRegistration =
            JobRegistration.of(
                    object : Job {
                        override fun getKey(): JobKey =
                                SONARQUBE_COLLECTION_JOB.getKey(project.name)

                        override fun getTask() = JobRun { listener ->
                            securityService.asAdmin(SONARQUBE_SYSTEM_REASON) {
                                sonarQubeMeasuresCollectionService.collect(project) { msg ->
                                    listener.message(msg)
                                }
                            }
                        }

                        override fun getDescription(): String =
                                "Collection of SonarQube measures for project ${project.name}"

                        override fun isDisabled(): Boolean = project.isDisabled ||
                                cachedSettingsService.getCachedSettings(SonarQubeMeasuresSettings::class.java).disabled

                    }
            ).withSchedule(Schedule.NONE)

}