package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.extension.api.BuildDisplayNameExtension
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Component

@Component
class LabelBuildDisplayNameExtension(
    extensionFeature: GeneralExtensionFeature,
    private val propertyService: PropertyService,
    private val structureService: StructureService,
) : AbstractExtension(extensionFeature), BuildDisplayNameExtension {

    override fun getBuildDisplayName(build: Build): String? {
        val displayProperty: BuildLinkDisplayProperty? =
            propertyService.getProperty(build.project, BuildLinkDisplayPropertyType::class.java).value
        val labelProperty: ReleaseProperty? = propertyService.getProperty(build, ReleasePropertyType::class.java).value
        return displayProperty.getLabel(labelProperty)
    }

    override val displayNameProperty = BuildDisplayNameProperty(
        propertyTypeName = ReleasePropertyType::class.java.name,
        jsonField = ReleaseProperty::name.name,
    )

    override fun mustProvideBuildName(build: Build): Boolean {
        val displayProperty: BuildLinkDisplayProperty? =
            propertyService.getProperty(build.project, BuildLinkDisplayPropertyType::class.java).value
        return displayProperty?.useLabel ?: false
    }

    override fun setDisplayName(
        build: Build,
        displayName: String,
        override: Boolean
    ): Boolean {
        val release = propertyService.getPropertyValue(build, ReleasePropertyType::class.java)
        if (release == null || override) {
            propertyService.editProperty(
                build,
                ReleasePropertyType::class.java,
                ReleaseProperty(
                    name = displayName
                )
            )
            return true
        }
        return false
    }

    override fun findBuildByDisplayName(project: Project, name: String, onlyDisplayName: Boolean): Build? =
        // Looking first with release property
        structureService.buildSearch(
            projectId = project.id,
            form = BuildSearchForm(
                maximumCount = 1,
                property = ReleasePropertyType::class.java.name,
                propertyValue = name,
            )
        ).firstOrNull()
            ?: if (onlyDisplayName) {
                null
            } else {
                structureService.buildSearch(
                    projectId = project.id,
                    form = BuildSearchForm(
                        maximumCount = 1,
                        buildName = name,
                        buildExactMatch = true,
                    )
                ).firstOrNull()
            }
}