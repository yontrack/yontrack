package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Component
import kotlin.jvm.optionals.getOrNull

@Component
class ReleaseValidationPropertyLabelListener(
    private val propertyService: PropertyService,
    private val structureService: StructureService,
    private val securityService: SecurityService,
) : ReleasePropertyListener {
    override fun onBuildReleaseLabel(build: Build, releaseProperty: ReleaseProperty) {
        val releaseValidationProperty =
            propertyService.getPropertyValue(build.branch, ReleaseValidationPropertyType::class.java)
        if (releaseValidationProperty != null) {
            // Forcing the creation of the validation stamp if it does not exist
            securityService.asAdmin("release-validation") {
                val vs = structureService.findValidationStampByName(build.project.name, build.branch.name, releaseValidationProperty.validation).getOrNull()
                if (vs == null) {
                    structureService.newValidationStamp(
                        ValidationStamp.of(
                            build.branch,
                            NameDescription.nd(
                                releaseValidationProperty.validation,
                                "Validation stamp created automatically from the release/label property"
                            )
                        )
                    )
                }
            }
            // Validation
            structureService.newValidationRun(
                build = build,
                validationRunRequest = ValidationRunRequest(
                    validationStampName = releaseValidationProperty.validation,
                    validationRunStatusId = ValidationRunStatusID.STATUS_PASSED,
                    description = "Validated because validation on release/label is set and label is ${releaseProperty.name}",
                )
            )
        }
    }
}