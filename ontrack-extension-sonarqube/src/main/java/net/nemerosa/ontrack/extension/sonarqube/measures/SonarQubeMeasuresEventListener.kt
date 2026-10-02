package net.nemerosa.ontrack.extension.sonarqube.measures

import net.nemerosa.ontrack.extension.general.validation.MetricsValidationData
import net.nemerosa.ontrack.extension.general.validation.MetricsValidationDataType
import net.nemerosa.ontrack.extension.sonarqube.property.SonarQubeProperty
import net.nemerosa.ontrack.extension.sonarqube.property.SonarQubePropertyType
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventListener
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Component

@Component
class SonarQubeMeasuresEventListener(
    private val propertyService: PropertyService,
    private val sonarQubeMeasuresCollectionService: SonarQubeMeasuresCollectionService,
    private val cachedSettingsService: CachedSettingsService,
    private val validationRunService: ValidationRunService,
    private val metricsValidationDataType: MetricsValidationDataType,
    private val securityService: SecurityService,
) : EventListener {
    override fun onEvent(event: Event) {
        if (event.eventType == EventFactory.NEW_VALIDATION_RUN) {
            // Gets the validation run
            val run: ValidationRun = event.getEntity(ProjectEntityType.VALIDATION_RUN)
            // Gets the project & the SonarQube property
            val project = run.build.project
            val property: SonarQubeProperty? =
                propertyService.getPropertyValue(project, SonarQubePropertyType::class.java)
            if (property != null) {
                // Gets the validation stamp
                val stamp: ValidationStamp = event.getEntity(ProjectEntityType.VALIDATION_STAMP)
                // Checks the validation stamp
                if (stamp.name == property.validationStamp) {
                    // Filtering on the branch
                    if (sonarQubeMeasuresCollectionService.matches(run.build, property)) {
                        // Checking the settings
                        val settings = cachedSettingsService.getCachedSettings(SonarQubeMeasuresSettings::class.java)
                        if (!settings.disabled) {
                            // Launching the collection of metrics
                            val result = sonarQubeMeasuresCollectionService.collect(run.build, property)
                            // If the property allows for validation metrics
                            if (property.validationMetrics && !result.measures.isNullOrEmpty()) {
                                // Some conditions must be met:
                                // * the validation stamp must not be typed or be of type Metrics
                                // * the validation run  must not be typed or be of type Metrics
                                //
                                // If the validation run has already some metrics, they will be overridden by the new one.
                                val vsDataTypeId = run.validationStamp.dataType?.descriptor?.id
                                if (vsDataTypeId == null ||
                                    vsDataTypeId == MetricsValidationDataType::class.java.name
                                ) {
                                    val runDataTypeId = run.data?.descriptor?.id
                                    if (runDataTypeId == null ||
                                        runDataTypeId == MetricsValidationDataType::class.java.name
                                    ) {
                                        // Getting the new data
                                        // Setting the data on the run
                                        securityService.asAdmin(SONARQUBE_SYSTEM_REASON) {
                                            validationRunService.updateValidationRunData(
                                                run,
                                                metricsValidationDataType.data(
                                                    MetricsValidationData(
                                                        result.measures
                                                    )
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}